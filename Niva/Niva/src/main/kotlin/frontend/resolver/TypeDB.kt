package frontend.resolver


import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.Receiver
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.frontend.parser.types.ast.TypeAST
import main.utils.GlobalVariables
import main.utils.RED
import main.utils.WHITE
import main.utils.YEL

@Suppress("unused")
sealed class TypeDBResult {
    class FoundOne(val type: Type) : TypeDBResult()
    class FoundMoreThanOne(val packagesToTypes: Map<String, Type>) : TypeDBResult()
    class NotFound(val notFountName: String) : TypeDBResult()
}

class FieldNameAndParent(
    val fieldName: String, val parent: Type.UserLike, var ast: TypeAST? = null, // ast is here only in complex types
    val typeDeclaration: SomeTypeDeclaration
)

class TypeDB(
    val internalTypes: MutableMap<TypeName, Type.InternalType> = mutableMapOf(),
    val lambdaTypes: MutableMap<TypeName, Type.Lambda> = mutableMapOf(),
    val userTypes: MutableMap<TypeName, MutableList<Type.UserLike>> = mutableMapOf(),
    // name of the Missing Type to (parent type + field of parent name)
    val unresolvedFields: MutableMap<String, FieldNameAndParent> = mutableMapOf()
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
    if (fromScope != null) {
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


    }

    // check lambdas
    val foundInLambda = lambdaTypes[name]
    if (foundInLambda != null) {
        return TypeDBResult.FoundOne(foundInLambda)
    }

    // not in scope, not user type, not internal type
    return TypeDBResult.NotFound(name)

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
        KeywordMsg(value, "", value.type, value.token, listOf(), listOf(value.toString())), imports, curPkg, value.token
    )
    return w
}

// region adding

fun TypeDB.add(type: Type, token: Token, customNameAlias: String? = null) {
    val realName = customNameAlias ?: type.name
    when (type) {
//        is Type.ErrorType -> addErrorDomain(realName, type, token)
        is Type.UserLike -> addUserLike(realName, type, token)
        is Type.InternalType -> addInternalType(realName, type)
        is Type.Lambda -> addLambdaType(realName, type)
        is Type.NullableType, Type.RecursiveType, is Type.UnresolvedType -> throw Exception("unreachable")
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
    kwConstructor: KeywordMsg?,
    imports2: Set<String>,
    currentPkgName: String,
    tokenForError: Token
): Type {

    // case 0 we are in the same package as statement
    // if there type in this pkg, but there is more than one type with same name
    // than just Person is this type, and b.Person is type from some pkg
    val typeDeclaredInTheCurrentPkg = result.packagesToTypes[currentPkgName]
    if (typeDeclaredInTheCurrentPkg != null) {
        return typeDeclaredInTheCurrentPkg
    }

    val imports = imports2 + currentPkgName

    if (kwConstructor == null) {
        // check if we have such import, so its like "Application", without constructor call

        val fileImportsHasOneOfResultsPkgs = imports.intersect(result.packagesToTypes.keys)
        if (fileImportsHasOneOfResultsPkgs.count() == 1) {
            if (GlobalVariables.isLspMode) {
                return result.packagesToTypes[fileImportsHasOneOfResultsPkgs.first()]!!
            } else {
                tokenForError.compileError("It looks like you forget to call constructor for $tokenForError")
            }
        }


        val typesList = result.packagesToTypes.values.joinToString(", ") { it.name + ": " + it.pkg }
        tokenForError.compileError("Found more than one type with same name in different packages: $WHITE$typesList")
    }


    // if statement has clarification already like a.Person
    val receiver = kwConstructor.receiver
    if (receiver is IdentifierExpr && receiver.names.count() > 1) {
        val packageName = receiver.names.dropLast(1).joinToString(".")
        val resultType = result.packagesToTypes[packageName]
            ?: kwConstructor.token.compileError("Can't find type $YEL${receiver.name}$RED inside ${WHITE}$packageName$RED package")
        return resultType
    }

    val setOfArgsSendedNames = kwConstructor.args.map { it.name }.toSet()
    val setOfArgsTypeNames = kwConstructor.args.map {
        it.keywordArg.type!!.pkg + "::" + it.keywordArg.type!!.name
    }.toSet()

    val set = mutableSetOf<Type>()


    // case 1 same names different arg names
    result.packagesToTypes.values.filterIsInstance<Type.UserLike>().forEach { type ->
        val resultSetNames = type.fields.map { it.name }.toSet()
        val resultSetTypes = type.fields.map { it.type.pkg + "::" + it.type.name }.toSet()
        val sameNames = resultSetNames == setOfArgsSendedNames
        val sameTypes = resultSetTypes == setOfArgsTypeNames

        if (sameNames && sameTypes) {
            set.add(type)
        }
    }

    if (set.count() == 1) {
        return set.first()
    }

    // we have 2 or more absolutely same types defined in different packages
    // need to check if current package has some import

    val type = if (set.isEmpty()) result.packagesToTypes.values.find { imports.contains(it.pkg) } else set.find {
        imports.contains(it.pkg)
    }
    if (type != null) {
        return type
    } else {

        kwConstructor.token.compileError(
            "Type `${kwConstructor.receiver}` is defined in many packages: ${result.packagesToTypes.values.map { it.pkg }},\n\t" + "please specify what package you wanna use with for example `${currentPkgName}.${kwConstructor.receiver}`\n\t" + "or `Project use: \"${currentPkgName}\"` "
        )
    }
}


fun TypeDBResult.getTypeFromTypeDBResultConstructor(
    statement: KeywordMsg?, imports: Set<String>, curPkg: String, tokenForError: Token
): Type? {
    return when (this) {
        is TypeDBResult.FoundMoreThanOne -> {
            resolveTypeIfSameNamesFromConstructor(this, statement, imports, curPkg, tokenForError)
        }

        is TypeDBResult.FoundOne -> this.type
        is TypeDBResult.NotFound -> {
            null
        }
    }
}
