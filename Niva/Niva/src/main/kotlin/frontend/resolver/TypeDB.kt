package frontend.resolver


import main.utils.RED
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.Receiver

@Suppress("unused")
sealed class TypeDBResult {
    class FoundOne(val type: Type) : TypeDBResult()
    class FoundMoreThanOne(val packagesToTypes: Map<String, Type>) : TypeDBResult()
    class NotFound(val notFountName: String) : TypeDBResult()
}

class TypeDB(
    val internalTypes: MutableMap<String, Type.InternalType> = mutableMapOf(),
    val lambdaTypes: MutableMap<String, Type.Lambda> = mutableMapOf(),
    val userTypes: MutableMap<TypeName, MutableList<Type.UserLike>> = mutableMapOf()
)

// getting
fun TypeDB.getType(
    name: String,
    currentScope: MutableMap<String, Type>? = null,
    previousScope: MutableMap<String, Type>? = null,
    names: List<String> = listOf()
): TypeDBResult {

    // found in scope
    val fromScope = currentScope?.get(name) ?: previousScope?.get(name)
    if (fromScope != null){
        return TypeDBResult.FoundOne(fromScope)
    }

    // first check internal types
    val foundInInternal = internalTypes[name]
    if (foundInInternal != null) {
        return TypeDBResult.FoundOne(foundInInternal)
    }

    // then userTypes
    val userTypesFromDifferentPkgs = userTypes[name]
    if (userTypesFromDifferentPkgs != null) {
        val countOfTypes = userTypesFromDifferentPkgs.count()
        when {
            countOfTypes == 1 -> {
                return TypeDBResult.FoundOne(userTypesFromDifferentPkgs[0])
            }

            countOfTypes > 1 -> {
                val map = mutableMapOf<String, Type>()
                userTypesFromDifferentPkgs.forEach {
                    map[it.pkg] = it
                }
                // if already qualified
                if (names.count() > 1) {
                    val pkgName = names.dropLast(1).joinToString(".")
                    val q = userTypesFromDifferentPkgs.find { it.pkg == pkgName }

                    if (q != null) {
                        return TypeDBResult.FoundOne(q)
                    }
                }
                return TypeDBResult.FoundMoreThanOne(map)
            }

            countOfTypes == 0 -> return TypeDBResult.NotFound(name)
            else -> throw Exception("???")
        }


    } else {
        // not in scope, not user type, not internal type
        return TypeDBResult.NotFound(name)
    }
}

fun TypeDB.getTypeOfIdentifierReceiver(
    typeName: String,
    value: Receiver,
    imports: Set<String>,
    curPkg: String,
    currentScope: MutableMap<String, Type>? = null,
    previousScope: MutableMap<String, Type>? = null,
    names: List<String> = listOf()
): Type? {


    val q = getType(typeName, currentScope, previousScope, names = names)
    val w = q.getTypeFromTypeDBResultConstructor(
        KeywordMsg(value, "", value.type, value.token, listOf(), listOf(value.toString())),
        imports,
        curPkg
    )
    return w
}

// adding

fun TypeDB.add(type: Type, token: Token) {
    when (type) {
        is Type.UserLike -> addUserLike(type.name, type, token)
        is Type.InternalType -> addInternalType(type.name, type)
        is Type.Lambda -> addLambdaType(type.name, type)

        is Type.NullableType -> TODO()

        Type.RecursiveType -> TODO()
    }

}

fun TypeDB.addInternalType(typeName: TypeName, type: Type.InternalType) {
    internalTypes[typeName] = type
}

fun TypeDB.addLambdaType(typeName: TypeName, type: Type.Lambda) {
    lambdaTypes[typeName] = type
}

fun TypeDB.addUserLike(typeName: TypeName, type: Type.UserLike, @Suppress("UNUSED_PARAMETER") token: Token) {
    val list = userTypes[typeName]
    if (list == null) {
        // create list with single new type
        userTypes[typeName] = mutableListOf(type)
    } else {
        // check if list alreadyContain this type
        val listHasTypeWithThisName = list.find { it.name == type.name }
        if (listHasTypeWithThisName != null) {
            // check that their defined in different packages, if not than drop
            if (type.pkg == listHasTypeWithThisName.pkg) {
//                println("typeDB: Type with name ${type.name} already defined in package ${type.pkg}")
//                token.compileError("Type with name ${type.name} already defined in package ${type.pkg}")
            } else {
                // add new type to list
                list.add(type)
            }

        } else {
            // this type is first with that name in the list
            // this is never happen, lists contains types with same name
            list.add(type)
        }
    }
}


fun resolveTypeIfSameNamesFromConstructor(
    result: TypeDBResult.FoundMoreThanOne,
    statement: KeywordMsg?,
    imports2: Set<String>,
    currentPkgName: String
): Type {

    // case 0 we are in the same package as statement
    // if there type in this pkg, but there is more than one type with same name
    // than just Person is this type, and b.Person is type from some pkg
    val typeDeclaredInTheCurrentPkg = result.packagesToTypes[currentPkgName]
    if (typeDeclaredInTheCurrentPkg  != null) {
        return typeDeclaredInTheCurrentPkg
    }

    if (statement == null) {
        val typesList = result.packagesToTypes.values.map { it.name to it.pkg}
        createFakeToken().compileError("Found more the one types with same name in different packages: $WHITE$typesList" )
    }


    val imports = imports2 + currentPkgName
    // if statement has clarification already like a.Person
    val receiver = statement.receiver
    if (receiver is IdentifierExpr && receiver.names.count() > 1) {
        val packageName = receiver.names.dropLast(1).joinToString(".")
        val resultType = result.packagesToTypes[packageName]
            ?: statement.token.compileError("Can't find type $YEL${receiver.name}$RED inside ${WHITE}$packageName$RED package")
        return resultType
    }

    val setOfArgsNames = statement.args.map { it.name }.toSet()
    val setOfArgsTypeNames = statement.args.map {
        it.keywordArg.type!!.pkg + "::" + it.keywordArg.type!!.name
    }.toSet()

    val set = mutableSetOf<Type>()


    // case 1 same names different arg names
    result.packagesToTypes.values.forEach { type ->
        if (type is Type.UserLike) {
            val resultSetNames = type.fields.map { it.name }.toSet()
            val resultSetTypes = type.fields.map { it.type.pkg + "::" + it.type.name }.toSet()
            val sameNames = resultSetNames == setOfArgsNames
            val sameTypes = resultSetTypes == setOfArgsTypeNames

            if (sameNames && sameTypes) {
                set.add(type)
            }
        }
    }

    if (set.count() == 1) {
        return set.first()
    }

    // we have 2 or more absolutely same types defined in different packages
    // need to check if current package has some import

    val type = set.find { imports.contains(it.pkg) }
    if (type != null) {
        return type
    } else {
        statement.token.compileError(
            "Type `${statement.receiver}` is defined in many packages: ${set.map { it.pkg }},\n\t" +
                    "please specify what package you wanna use with for example `${currentPkgName}.${statement.receiver}`\n\t" +
                    "or `Project use: \"${currentPkgName}\"` "
        )
    }
}


fun TypeDBResult.getTypeFromTypeDBResultConstructor(statement: KeywordMsg?, imports: Set<String>, curPkg: String): Type? {
    return when (this) {
        is TypeDBResult.FoundMoreThanOne -> {
            resolveTypeIfSameNamesFromConstructor(this, statement, imports, curPkg)
        }

        is TypeDBResult.FoundOne -> this.type
        is TypeDBResult.NotFound -> {
            null
        }
    }
}
