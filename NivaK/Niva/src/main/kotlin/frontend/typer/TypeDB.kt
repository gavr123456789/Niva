package frontend.typer

import frontend.meta.Token

enum class TypeDBResultKind { FOUND, HAS }

sealed class TypeDBResult {
    class FoundOneUser(val type: Type.UserLike) : TypeDBResult()
    class FoundOneInternal(val type: Type.InternalType) : TypeDBResult()
    class FoundMoreThanOne(val packagesToTypes: Map<String, Type>) : TypeDBResult()
    data object NotFound : TypeDBResult()
}

class TypeDB(
    val internalTypes: MutableMap<String, Type.InternalType> = mutableMapOf(),
    val userTypes: MutableMap<TypeName, MutableList<Type.UserLike>> = mutableMapOf()
)

// getting

fun TypeDB.getType(
    name: String, currentScope: MutableMap<String, Type>? = null,
    previousScope: MutableMap<String, Type>? = null
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
                    map[it.name] = it
                }
                TypeDBResult.FoundMoreThanOne(map)
            }

            countOfTypes == 0 -> TypeDBResult.NotFound
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
        return TypeDBResult.NotFound
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