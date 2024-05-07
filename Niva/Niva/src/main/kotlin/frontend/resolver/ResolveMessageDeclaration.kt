package frontend.resolver

import frontend.parser.parsing.MessageDeclarationType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMsgType
import main.frontend.resolver.findStaticMessageType
import main.utils.CYAN
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
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
    }
    // but wait maybe some generic param's type is unresolved (List::T unary = [])
    if (forType is Type.UserLike && st.forTypeAst is TypeAST.UserType && st.forTypeAst.typeArgumentList.isNotEmpty()) {
        var alTypeArgsAreFound = true
        val newListOfTypeArgs = mutableListOf<Type>()
        st.forTypeAst.typeArgumentList.forEach {
            val type = if (it.name.isGeneric()) Type.UnknownGenericType(it.name) else typeTable[it.name] //testing
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


    val bodyScope = mutableMapOf<String, Type>()

    val resolveBody = {
        bodyScope["this"] = forType

        // args from kw or constructor
        fun addArgsToBodyScope(st: MessageDeclarationKeyword) {
            st.args.forEach {
                if (it.typeAST == null) {
                    st.token.compileError("Can't parse type for argument ${WHITE}${it.name}")
                }
                val astType = it.typeAST
                val typeFromAst = astType.toType(typeDB, typeTable)//fix

                bodyScope[it.localName ?: it.name] = typeFromAst

                if (typeFromAst is Type.UnknownGenericType) {
                    st.typeArgs.add(typeFromAst.name)
                }
                // add generic params to scope
                if (typeFromAst is Type.UserType && typeFromAst.typeArgumentList.isNotEmpty()) {
                    st.typeArgs.addAll(typeFromAst.typeArgumentList.map { typeArg -> typeArg.name })
                    // T == T
                    if (typeFromAst.name == it.typeAST.name) {
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
                val argType = arg.typeAST?.toType(typeDB, typeTable)
                    ?: st.token.compileError("Cant infer type of argument: `${YEL}${arg.name}${RED}` for binary message declaration `${YEL}${st.forTypeAst.name} ${CYAN}${st.name}`")
                bodyScope[arg.name] = argType
            }

            is ConstructorDeclaration -> {
                if (st.msgDeclaration is MessageDeclarationKeyword) // there is no binary constructors
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
        // check that errors that returns and stack are the same
        val validateErrorsDeclarated = {
            val returnTypeAST = st.returnTypeAST
            if (returnTypeAST != null) {
                // errors declared but there are no possible errors
                val possibleErrors = st.stackOfPossibleErrors
                val declaredErrors = returnTypeAST.errors

                if (possibleErrors.isNotEmpty()) {
                    val allPossibleErrors = possibleErrors.flatMap { it.second }
                    val possibleErrorsUnion = allPossibleErrors.distinct()
                    val possibleErrorsJoined = possibleErrorsUnion.joinToString(" ") { it.name }

                    val listOfErrorsWithLines = buildString {
                        possibleErrors.forEach {
                            assert(it.second.isNotEmpty())
                            append("\t" + it.first.token.line, " | $WHITE", it.first.receiver, " ", it.first, RESET)
                            append(" !{$RED", it.second.joinToString(" ") { it.name }, "$RESET}\n")
                        }
                    }

                    // not declared anything at all
                    if (declaredErrors == null) {
                        val singleOrManyErrors = if (allPossibleErrors.count() == 1)
                            allPossibleErrors[0].name
                        else "{$possibleErrorsJoined}"

                        val possibleSolutions =
                            "$WHITE-> $returnTypeAST!$RESET or $WHITE-> $returnTypeAST!$singleOrManyErrors$RESET"

                        st.token.compileError("Possible errors of $WHITE$st$RESET are: \n${listOfErrorsWithLines} but you not declared them\n use $possibleSolutions")
                    } else if(declaredErrors.count() != 0 ){
                        // maybe declared but wrong
                        val setOfPossible = possibleErrorsUnion.map { it.name }.toSet()
                        val setOfDeclared = declaredErrors.toSet()
                        if (setOfDeclared != setOfPossible) {
                            val declErrors = declaredErrors.joinToString(" ")
                            val diff = (if (setOfDeclared.count() > setOfPossible.count()) setOfDeclared - setOfPossible else setOfPossible - setOfDeclared)
                                .joinToString(" ")

                            st.token.compileError("Possible errors of $WHITE$st$RESET are: \n" +
                                    "${listOfErrorsWithLines}\n" +
                                    "Wrong set of errors declarated\nPossible errors: $possibleErrorsJoined\nDeclared errors: $declErrors\nDiff: $diff")
                        }
                    }


                }
                // errors are possible but not declared
                if (possibleErrors.isEmpty() && declaredErrors?.isNotEmpty() == true) {
                    st.token.compileError("There is no possible errors but you declared ${declaredErrors.joinToString()}")
                }
            }

        }
        validateErrorsDeclarated()

        if (!st.isSingleExpression && wasThereReturn == null && st.returnTypeAST != null && st.returnTypeAST.name != InternalTypes.Unit.name) {
            st.token.compileError("You missed returning(^) a value of type: ${YEL}${st.returnTypeAST.name}")
        }
        resolvingMessageDeclaration = null
        // change return type in db is single exp, because it was recorded as -> Unit, since there was no return type declarated
        if (st.isSingleExpression) {
            val expr = st.body[0]
            if (expr is Expression) {
                val typeOfSingleExpr = expr.type!!
                val mdgData = when (st) {
                    is ConstructorDeclaration -> findStaticMessageType(forType, st.name, st.token).first
                    is MessageDeclarationUnary -> findAnyMsgType(
                        forType,
                        st.name,
                        st.token,
                        MessageDeclarationType.Unary
                    )

                    is MessageDeclarationBinary -> findAnyMsgType(
                        forType,
                        st.name,
                        st.token,
                        MessageDeclarationType.Binary
                    )

                    is MessageDeclarationKeyword -> findAnyMsgType(
                        forType,
                        st.name,
                        st.token,
                        MessageDeclarationType.Keyword
                    )
                }


                val declaredReturnType = st.returnType
                mdgData.returnType = typeOfSingleExpr


                st.returnType = typeOfSingleExpr
                //!st.isRecursive && cant understand why recursive check was here
                // ... because we cant infer type of recursive functions
                if (!st.isRecursive && declaredReturnType != null && st.returnTypeAST != null) {
                    // unpack null because return Int from -> Int? method is valid
                    if (!compare2Types(typeOfSingleExpr, declaredReturnType, unpackNull = true, isOut = true))
                        st.returnTypeAST.token.compileError("Return type defined: ${YEL}$declaredReturnType${RESET} but real type returned: ${YEL}$typeOfSingleExpr")
                    else if (mdgData.returnType != declaredReturnType) {
                        // change return type to the declarated one, since they are compatible
                        // because it can be type Null returned from -> Int? msg, but it must be Int? in such case
                        st.returnType = declaredReturnType
                        mdgData.returnType = declaredReturnType
                    }
                }
            } else {
                TODO("Single expression with not expression")
            }
        } else {
            val realReturn = wasThereReturn
            val returnType = st.returnType
            if (realReturn != null && returnType != null &&
                !compare2Types(returnType, realReturn, unpackNull = true, isOut = true)
            ) {
                st.returnTypeAST?.token?.compileError("Return type defined: ${YEL}$returnType${RESET} but real type returned: ${YEL}$realReturn")
            }
        }


    }

    // no return type declared, not recursive, single expr
    // body is not resolved and no returnTypeAst, so we cant infer return type
    // because not all declarations resolved yet
    if (st.returnTypeAST == null && !st.isRecursive && st.isSingleExpression && !needResolveOnlyBody) {
        unResolvedSingleExprMessageDeclarations.add(currentPackageName, st)
        currentLevel--
        return true
    }


    // addToDb
    if (addToDb) {
        addNewAnyMessage(st, false, false, forType)
    }

    val isResolvedSingleExpr = if(st.isSingleExpression) {
        val first = st.body.first()
        first is Expression && first.type != null
    } else false
    if (needResolveOnlyBody && !isResolvedSingleExpr) {
        resolveBody()
    }

    if (st is ConstructorDeclaration) {
        st.msgDeclaration.forType = st.forType
        st.msgDeclaration.returnType = st.returnType
    }

    return false
}
