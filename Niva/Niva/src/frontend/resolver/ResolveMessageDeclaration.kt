package frontend.resolver

import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*
import main.*
import main.frontend.resolver.findAnyMsgType
import main.frontend.resolver.findStaticMessageType
import main.utils.isGeneric


// returns true if unresolved
fun Resolver.resolveMessageDeclaration(
    st: MessageDeclaration,
    needResolveOnlyBody: Boolean,
    previousScope: MutableMap<String, Type>,
    addToDb: Boolean = true
): Boolean {
    val forTypeAst = st.forTypeAst


    val forType: Type? = st.forType ?: if (forTypeAst is TypeAST.UserType) {
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
        ) ?: if (forTypeAst.name.isGeneric()) {
            st.typeArgs.add(forTypeAst.name)
            Type.UnknownGenericType(forTypeAst.name)
        } else null


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
//                val testDB = typeDB.getType(it.name)

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
            st.token.compileError("Type $YEL${st.forTypeAst.name}$RESET already has field with name $WHITE${st.name}")
        }
    }

    val bodyScope = mutableMapOf<String, Type>()

    val resolveBody = {
        bodyScope["this"] = forType

        // add args to scope
        fun addArgsToBodyScope(st: MessageDeclarationKeyword) {
            st.args.forEach {
                if (it.type == null) {
                    st.token.compileError("Can't parse type for argument $WHITE${it.name}")
                }
                val astType = it.type
                val typeFromAst = astType.toType(typeDB, typeTable)//fix

                bodyScope[it.localName ?: it.name] = typeFromAst

                if (typeFromAst is Type.UnknownGenericType) {
                    st.typeArgs.add(typeFromAst.name)
                }
                // add generic params to scope
                if (typeFromAst is Type.UserType && typeFromAst.typeArgumentList.isNotEmpty()) {
                    st.typeArgs.addAll(typeFromAst.typeArgumentList.map { typeArg -> typeArg.name })
                    // T == T
                    if (typeFromAst.name == it.type.name) {
                        bodyScope[it.name] = typeFromAst
                    }
                }
            }
        }

        when (st) {
            is MessageDeclarationKeyword -> {
                addArgsToBodyScope(st)
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

            is ConstructorDeclaration -> {
                if (st.msgDeclaration is MessageDeclarationKeyword)
                    addArgsToBodyScope(st.msgDeclaration)
            }
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
                    is MessageDeclarationUnary -> findAnyMsgType(forType, st.name, st.token, MessageDeclarationType.Unary)
                    is MessageDeclarationBinary -> findAnyMsgType(forType, st.name, st.token, MessageDeclarationType.Binary)
                    is MessageDeclarationKeyword -> findAnyMsgType(forType, st.name, st.token, MessageDeclarationType.Keyword)
                }


                val declaredReturnType = st.returnType
                mdgData.returnType = returnType
                st.returnType = returnType

                // in single expr declared type not matching real type
                if (!st.isRecursive && declaredReturnType != null && !compare2Types(returnType, declaredReturnType) && st.returnTypeAST != null) {
                    st.returnTypeAST.token.compileError("Return type defined: $YEL$declaredReturnType$RESET but real type returned: $YEL$returnType")
                }
            }
        } else {
            val realReturn = wasThereReturn
            val returnType = st.returnType
            if (realReturn != null && returnType != null && !compare2Types(returnType, realReturn)) {
                st.returnTypeAST?.token?.compileError("Return type defined: $YEL$returnType$RESET but real type returned: $YEL$realReturn")
            }
        }


    }

    if (st.returnTypeAST == null && !st.isRecursive && st.isSingleExpression && !needResolveOnlyBody) {
        unResolvedSingleExprMessageDeclarations.add(currentPackageName, st)
        currentLevel--
        return true
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
