package frontend.typer

import frontend.meta.Token
import frontend.meta.compileError
import frontend.parser.types.ast.IdentifierExpr
import frontend.parser.types.ast.KeywordMsg

enum class TypeDBResultKind { FOUND, HAS }

sealed class TypeDBResult {
    class FoundOneUser(val type: Type.UserLike) : TypeDBResult()
    class FoundOneInternal(val type: Type.InternalType) : TypeDBResult()
    class FoundMoreThanOne(val packagesToTypes: Map<String, Type>) : TypeDBResult()
    class NotFound(val notFountName: String) : TypeDBResult()
}

class TypeDB(
    val internalTypes: MutableMap<String, Type.InternalType> = mutableMapOf(),
    val userTypes: MutableMap<TypeName, MutableList<Type.UserLike>> = mutableMapOf()
)

// getting

fun TypeDB.getType(
    name: String,
    currentScope: MutableMap<String, Type>? = null,
    previousScope: MutableMap<String, Type>? = null,
    project: Project? = null
): TypeDBResult {
    // first check internal types
    val foundInInternal = internalTypes[name]
    if (foundInInternal != null) {
        return TypeDBResult.FoundOneInternal(foundInInternal)
    }
    // then userTypes
    val listOfUserTypes = userTypes[name]
    if (listOfUserTypes != null) {
        val countOfTypes = listOfUserTypes.count()
        return when {
            countOfTypes == 1 -> {
                TypeDBResult.FoundOneUser(listOfUserTypes[0])
            }

            countOfTypes > 1 -> {
                val map = mutableMapOf<String, Type>()
                listOfUserTypes.forEach {
                    map[it.pkg] = it
                }
                TypeDBResult.FoundMoreThanOne(map)
            }

            countOfTypes == 0 -> TypeDBResult.NotFound(name)
            else -> throw Exception("???")
        }


    } else {
        if (currentScope != null) {
            val type = currentScope[name]
            if (type != null) {
                return when (type) {
                    is Type.InternalType -> TypeDBResult.FoundOneInternal(type)
                    is Type.UserLike -> TypeDBResult.FoundOneUser(type)
                    else -> {
                        throw Exception("Type can be only internal or user like")
                    }
                }
            }
        }

        if (previousScope != null) {
            val type = previousScope[name]
            if (type != null) {
                return when (type) {
                    is Type.InternalType -> TypeDBResult.FoundOneInternal(type)
                    is Type.UserLike -> TypeDBResult.FoundOneUser(type)
                    else -> {
                        throw Exception("Type can be only internal or user like")
                    }
                }
            }
        }
        return TypeDBResult.NotFound(name)
    }
}

// adding

fun TypeDB.add(type: Type, token: Token) {
    if (type is Type.InternalType) {
        addInternalType(type.name, type)
    }
    if (type is Type.UserLike) {
        addUserLike(type.name, type, token)
    }
}

fun TypeDB.addInternalType(typeName: TypeName, type: Type.InternalType) {
    internalTypes[typeName] = type
}

fun TypeDB.addUserLike(typeName: TypeName, type: Type.UserLike, token: Token) {
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
                println("typeDB: Type with name ${type.name} already defined in package ${type.pkg}")
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


fun resolveTypeIfSameNames(result: TypeDBResult.FoundMoreThanOne, statement: KeywordMsg, imports: Set<String>): Type {
    // if statement has clarification already like a.Person
    val receiver = statement.receiver
    if (receiver is IdentifierExpr && receiver.names.count() > 1) {
        val packageName = receiver.names.dropLast(1).joinToString(".")
        val resultType = result.packagesToTypes[packageName]
            ?: statement.token.compileError("Can't find type ${receiver.name} inside $packageName package")
        return resultType
    }

    val setOfArgsNames = statement.args.map { it.selectorName }.toSet()
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

    val qwe = set.find { imports.contains(it.pkg) }
    if (qwe != null) {
        return qwe
    } else {
        statement.token.compileError(
            "Type `${statement.receiver}` is defined in may packages: ${set.map { it.pkg }},\n\t" +
                    "please specify what package you wanna use with for example `${set.first().pkg}.${statement.receiver}`\n\t" +
                    "or `Project use: \"${set.first().pkg}\"` "
        )
    }


    TODO()
}


fun TypeDBResult.getTypeFromTypeDBResult(statement: KeywordMsg, imports: Set<String>): Type {

    return when (this) {
        is TypeDBResult.FoundMoreThanOne -> {
            resolveTypeIfSameNames(this, statement, imports)
        }

        is TypeDBResult.FoundOneInternal -> this.type

        is TypeDBResult.FoundOneUser -> this.type
        is TypeDBResult.NotFound -> {
            statement.token.compileError("Cant find type: ${this.notFountName}")
        }
    }
}
