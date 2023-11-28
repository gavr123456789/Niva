package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.typer.*

fun Resolver.resolveMessageDeclaration(
    statement: MessageDeclaration,
    needResolveBody: Boolean,
    previousScope: MutableMap<String, Type>,
    addToDb: Boolean = true
): Boolean {
    // check if the type already registered
    val forType = typeTable[statement.forTypeAst.name]//testing
    val testDB = typeDB.getType(statement.forTypeAst.name)
    if (forType == null) {
        unResolvedMessageDeclarations.add(statement)
        currentLevel--
        return true
    } else {
        // but wait maybe some generic param's type is unresolved
        if (statement.forTypeAst is TypeAST.UserType && statement.forTypeAst.typeArgumentList.isNotEmpty() && forType is Type.UserLike) {
            var alTypeArgsAreFound = true
            val newListOfTypeArgs = mutableListOf<Type>()
            statement.forTypeAst.typeArgumentList.forEach {
                val type = typeTable[it.name]//testing
                val testDB = typeDB.getType(it.name)

                if (type != null) {
                    newListOfTypeArgs.add(type)
                } else {
                    alTypeArgsAreFound = false
                }
            }

            if (alTypeArgsAreFound) {
                forType.typeArgumentList = newListOfTypeArgs
            } else {
                unResolvedMessageDeclarations.add(statement)
                currentLevel--
                return true
            }
        }
        unResolvedMessageDeclarations.remove(statement)
        statement.forType = forType
    }


    // check that there is no field with the same name (because of getter has the same signature)
    // TODO! check only unary and keywords with one arg
    // no, check this when kind already resolved


    if (forType is Type.UserType) {
        val fieldWithTheSameName = forType.fields.find { it.name == statement.name }
        if (fieldWithTheSameName != null) {
            statement.token.compileError("Type ${statement.forTypeAst.name} already has field with name ${statement.name}")
        }
    }

    val bodyScope = mutableMapOf<String, Type>()

    val resolveBody = {
        bodyScope["this"] = forType

        // add args to scope
        when (statement) {
            is MessageDeclarationKeyword -> {
                statement.args.forEach {
                    if (it.type == null) {
                        statement.token.compileError("Can't parse type for argument ${it.name}")
                    }
                    val astType = it.type
                    val type = astType.toType(typeDB, typeTable)//fix

                    bodyScope[it.localName ?: it.name] = type

                    if (type is Type.UnknownGenericType) {
                        statement.typeArgs.add(type.name)
                    }
                    // add generic params to scope
                    if (type is Type.UserType && type.typeArgumentList.isNotEmpty()) {
                        statement.typeArgs.addAll(type.typeArgumentList.map { typeArg -> typeArg.name })
                        // T == T
                        if (type.name == it.type.name) {
                            bodyScope[it.name] = type
                        }
                    }

                }
            }

            is MessageDeclarationUnary -> {
                // unary has zero args
            }

            is MessageDeclarationBinary -> {
                val arg = statement.arg
                val argType = arg.type?.toType(typeDB, typeTable)
                    ?: statement.token.compileError("Cant infer type of argument: `${arg.name}` for binary message declaration `${statement.forTypeAst.name} ${statement.name}`")
                bodyScope[arg.name] = argType
            }

            is ConstructorDeclaration -> {}
        }
        // add args to bodyScope
        if (forType is Type.UserLike) {
            forType.fields.forEach {
                bodyScope[it.name] = it.type
            }
        }
        wasThereReturn = null
        resolve(statement.body, (previousScope + bodyScope).toMutableMap(), statement)
        if (wasThereReturn == null && statement.returnTypeAST != null && statement.returnTypeAST.name != InternalTypes.Unit.name) {
            statement.token.compileError("You missed returning a value of type: ${statement.returnTypeAST.name}")
        }

    }

    // we need to resolve body anyway, if there is single expression
    // to write right signature to db
    if (statement.isSingleExpression) {
        val lastStatement = statement.body[0]
        if (lastStatement is Expression) {
            if (lastStatement.type == null) {
                resolveBody()
            }
            // check that ast type equal to real type
            val returnAstType = statement.returnTypeAST

            val isReturnTypeGeneric =
                (returnAstType != null && returnAstType.name.count() == 1 && returnAstType.name[0].isUpperCase())

            if (!isReturnTypeGeneric) {
                if (returnAstType != null && !compareAstWithType(returnAstType, lastStatement.type!!)) {
                    lastStatement.token.compileError("Declarated return type of `${returnAstType.name} ${statement.name}` is `${returnAstType.name}` but you returning `${lastStatement.type}`")
                }
            }

            if (returnAstType == null && lastStatement.type != null) {
                statement.returnType = lastStatement.type
            }

        } else {
            if (lastStatement !is ReturnStatement && lastStatement !is Assign && lastStatement !is VarDeclaration) {
                lastStatement.token.compileError("You can use only expressions in single expression message declaration")
            }
        }
    }


    if (needResolveBody) {
        resolveBody()
    }


    // addToDb
    when (statement) {
        is MessageDeclarationUnary -> if (addToDb) addNewUnaryMessage(statement)
        is MessageDeclarationBinary -> if (addToDb) addNewBinaryMessage(statement)
        is MessageDeclarationKeyword -> {

            if (addToDb)
                addNewKeywordMessage(statement)
        }

        is ConstructorDeclaration -> {
            if (statement.returnTypeAST == null) {
                statement.returnType = forType
//                statement.returnTypeAST = TypeAST.UserType(
//                    name = forType.name,
//                    typeArgumentList = listOf(),
//                    isNullable = false,
//                    token = createFakeToken(),
//                )
            }
            if (addToDb) addStaticDeclaration(statement)
        }
    }


    // TODO check that return type is the same as declared return type, or if it not declared -> assign it
    return false
}
