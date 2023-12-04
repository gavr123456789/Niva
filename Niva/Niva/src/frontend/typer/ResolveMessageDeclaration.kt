package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.typer.*


// returns true if unresolved
fun Resolver.resolveMessageDeclaration(
    statement: MessageDeclaration,
    needResolveBody: Boolean,
    previousScope: MutableMap<String, Type>,
    addToDb: Boolean = true
): Boolean {
    // here u need not check for UserType, but allow typeDB search for all kinds of types
    val forTypeAst = statement.forTypeAst
    val forType = if (forTypeAst is TypeAST.UserType) {
        val ident = IdentifierExpr(
            name = forTypeAst.name,
            names = forTypeAst.names,
            token = forTypeAst.token
        )

        val q = typeDB.getTypeOfIdentifierReceiver(forTypeAst.name, ident, getCurrentImports(statement.token), currentPackageName, names = forTypeAst.names)
        if (q == null) {
            unResolvedMessageDeclarations.add(currentPackageName, statement)
            currentLevel--
            return true
        } else q

    } else typeTable[statement.forTypeAst.name]

//    val testDB = typeDB.getType(statement.forTypeAst.name)
    if (forType == null) {
        unResolvedMessageDeclarations.add(currentPackageName, statement)
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
                unResolvedMessageDeclarations.add(currentPackageName, statement)
                currentLevel--
                return true
            }
        }
        unResolvedMessageDeclarations.remove(currentPackageName, statement)
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
        if (!statement.isSingleExpression && wasThereReturn == null && statement.returnTypeAST != null && statement.returnTypeAST.name != InternalTypes.Unit.name) {
            statement.token.compileError("You missed returning(^) a value of type: ${statement.returnTypeAST.name}")
        }
        // change return type in db is single exp, because it was recorded as -> Unit, since there was no return type declarated
        if (statement.isSingleExpression ) {
            val expr = statement.body[0]
            if (expr is Expression) {
                val returnType = expr.type!!
                val mdgData = when (statement) {
                    is ConstructorDeclaration -> findStaticMessageType(forType, statement.name, statement.token).first
                    is MessageDeclarationBinary -> findBinaryMessageType(forType, statement.name, statement.token)
                    is MessageDeclarationKeyword -> findKeywordMsgType(forType, statement.name, statement.token)
                    is MessageDeclarationUnary -> findUnaryMessageType(forType, statement.name, statement.token)
                }

                mdgData.returnType = returnType
                statement.returnType = returnType
            }
        }

    }




    // addToDb
    if (addToDb) when (statement) {
        is MessageDeclarationUnary ->  addNewUnaryMessage(statement)
        is MessageDeclarationBinary -> addNewBinaryMessage(statement)
        is MessageDeclarationKeyword ->  addNewKeywordMessage(statement)

        is ConstructorDeclaration -> {
            if (statement.returnTypeAST == null) {
                statement.returnType = forType
            }
            addStaticDeclaration(statement)
        }
    }

    if (needResolveBody) {
        resolveBody()
    }


    return false
}
