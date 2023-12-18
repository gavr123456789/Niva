package frontend.resolver

import frontend.meta.compileError
import frontend.parser.types.ast.*
import main.*


// returns true if unresolved
fun Resolver.resolveMessageDeclaration(
    st: MessageDeclaration,
    needResolveOnlyBody: Boolean,
    previousScope: MutableMap<String, Type>,
    addToDb: Boolean = true
): Boolean {
    val forTypeAst = st.forTypeAst


    val forType = st.forType ?: if (forTypeAst is TypeAST.UserType) {
        val ident = IdentifierExpr(
            name = forTypeAst.name,
            names = forTypeAst.names,
            token = forTypeAst.token
        )
        val q = typeDB.getTypeOfIdentifierReceiver(
            forTypeAst.name,
            ident,
            getCurrentImports(st.token),
            currentPackageName,
            names = forTypeAst.names
        )
        if (q == null) {
            unResolvedMessageDeclarations.add(currentPackageName, st)
            currentLevel--
            return true
        } else q

    } else typeTable[st.forTypeAst.name]


//    val testDB = typeDB.getType(statement.forTypeAst.name)
    if (forType == null) {
        unResolvedMessageDeclarations.add(currentPackageName, st)
        currentLevel--
        return true
    } else {
        // but wait maybe some generic param's type is unresolved
        if (st.forTypeAst is TypeAST.UserType && st.forTypeAst.typeArgumentList.isNotEmpty() && forType is Type.UserLike) {
            var alTypeArgsAreFound = true
            val newListOfTypeArgs = mutableListOf<Type>()
            st.forTypeAst.typeArgumentList.forEach {
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
                unResolvedMessageDeclarations.add(currentPackageName, st)
                currentLevel--
                return true
            }
        }
        unResolvedMessageDeclarations.remove(currentPackageName, st)
        st.forType = forType
    }


    // check that there is no field with the same name (because of getter has the same signature)
    // TODO! check only unary and keywords with one arg
    // no, check this when kind already resolved


    if (forType is Type.UserType) {
        val fieldWithTheSameName = forType.fields.find { it.name == st.name }
        if (fieldWithTheSameName != null) {
            st.token.compileError("Type $WHITE${st.forTypeAst.name}$RED already has field with name $WHITE${st.name}")
        }
    }

    val bodyScope = mutableMapOf<String, Type>()

    val resolveBody = {
        bodyScope["this"] = forType

        // add args to scope
        when (st) {
            is MessageDeclarationKeyword -> {
                st.args.forEach {
                    if (it.type == null) {
                        st.token.compileError("Can't parse type for argument $WHITE${it.name}")
                    }
                    val astType = it.type
                    val type = astType.toType(typeDB, typeTable)//fix

                    bodyScope[it.localName ?: it.name] = type

                    if (type is Type.UnknownGenericType) {
                        st.typeArgs.add(type.name)
                    }
                    // add generic params to scope
                    if (type is Type.UserType && type.typeArgumentList.isNotEmpty()) {
                        st.typeArgs.addAll(type.typeArgumentList.map { typeArg -> typeArg.name })
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
                val arg = st.arg
                val argType = arg.type?.toType(typeDB, typeTable)
                    ?: st.token.compileError("Cant infer type of argument: `$YEL${arg.name}$RED` for binary message declaration `$YEL${st.forTypeAst.name} $CYAN${st.name}`")
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
        resolvingMessageDeclaration = st
        resolve(st.body, (previousScope + bodyScope).toMutableMap(), st)
        if (!st.isSingleExpression && wasThereReturn == null && st.returnTypeAST != null && st.returnTypeAST.name != InternalTypes.Unit.name) {
            st.token.compileError("You missed returning(^) a value of type: ${YEL}${st.returnTypeAST.name}")
        }
        resolvingMessageDeclaration = null
        // change return type in db is single exp, because it was recorded as -> Unit, since there was no return type declarated
        if (st.isSingleExpression) {
            val expr = st.body[0]
            if (expr is Expression) {
                val returnType = expr.type!!
                val mdgData = when (st) {
                    is ConstructorDeclaration -> findStaticMessageType(forType, st.name, st.token).first
                    is MessageDeclarationBinary -> findBinaryMessageType(forType, st.name, st.token)
                    is MessageDeclarationKeyword -> findKeywordMsgType(forType, st.name, st.token)
                    is MessageDeclarationUnary -> findUnaryMessageType(forType, st.name, st.token)
                }

                val declaredReturnType = st.returnType
                mdgData.returnType = returnType
                st.returnType = returnType

                // in single expr declared type not matching real type
                if (!st.isRecursive && declaredReturnType != null && !compare2Types(returnType, declaredReturnType)) {
                    st.returnTypeAST?.token?.compileError("Return type defined: $YEL$declaredReturnType$RESET but real type returned: $YEL$returnType")
                }
            }
        } else {
            val declaredReturnType = wasThereReturn
            val returnType = st.returnType
            if (declaredReturnType != null && returnType != null && !compare2Types(returnType, declaredReturnType)) {
                st.returnTypeAST?.token?.compileError("Return type defined: $YEL$declaredReturnType$RESET but real type returned: $YEL$returnType")
            }
        }


    }


    // addToDb
    if (addToDb) when (st) {
        is MessageDeclarationUnary -> addNewUnaryMessage(st)
        is MessageDeclarationBinary -> addNewBinaryMessage(st)
        is MessageDeclarationKeyword -> addNewKeywordMessage(st)

        is ConstructorDeclaration -> {
            if (st.returnTypeAST == null) {
                st.returnType = forType
            }
            addStaticDeclaration(st)
        }
    }

    if (needResolveOnlyBody) {
        resolveBody()
    }

    return false
}
