package frontend.resolver

import frontend.parser.parsing.MessageDeclarationType
import main.frontend.meta.CompilerError
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMsgType
import main.frontend.resolver.findStaticMessageType
import main.utils.CYAN
import main.utils.GlobalVariables
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.isGeneric


// returns true if unresolved
fun Resolver.resolveMessageDeclaration(
    statement: MessageDeclaration,
    needResolveOnlyBody: Boolean,
    previousScope: MutableMap<String, Type>,
    addToDb: Boolean = true
): Boolean {
    val forTypeAst = statement.forTypeAst

    val typeFromDB: Type? = statement.forType ?: if (forTypeAst is TypeAST.UserType) {
        val ident = IdentifierExpr(
            name = forTypeAst.name,
            names = forTypeAst.names,
            token = forTypeAst.token
        )
        val q = typeDB.getTypeOfIdentifierReceiver(
            forTypeAst.name,
            ident,
            getCurrentImports(statement.token),
            currentPackageName,
            names = forTypeAst.names
        ) ?: if (forTypeAst.name.isGeneric()) {
            statement.typeArgs.add(forTypeAst.name)
            Type.UnknownGenericType(forTypeAst.name)
        } else null


        if (q == null) {
            unResolvedMessageDeclarations.add(currentPackageName, statement)
            currentLevel--
            return true
        } else q

    } else typeTable[statement.forTypeAst.name]
    if (typeFromDB == null) {
        unResolvedMessageDeclarations.add(currentPackageName, statement)
        currentLevel--
        return true
    }

    val checkThatTypeGenericsResolvable = {
        if (typeFromDB is Type.UserType &&
            typeFromDB.typeArgumentList.isNotEmpty() &&
            !typeFromDB.isCopy &&
            typeFromDB.typeArgumentList.find { it.name.isGeneric() } == null &&
            statement.forTypeAst is TypeAST.UserType &&
            statement.forTypeAst.typeArgumentList.find { it.name.isGeneric() } == null // if every param is generic, then we don't need to copy that type like copy of List::T is still List::T
        ) {
            typeFromDB.copyAnyType().also {it.isCopy = true}
        } else
            typeFromDB

    }
    val copyTypeIfGenerics = checkThatTypeGenericsResolvable()

    // but wait maybe some generic param's type is unresolved (List::T unary = [])
    if (copyTypeIfGenerics is Type.UserLike && statement.forTypeAst is TypeAST.UserType && statement.forTypeAst.typeArgumentList.isNotEmpty()) {
        var alTypeArgsAreFound = true
        val newListOfTypeArgs = mutableListOf<Type>()
        statement.forTypeAst.typeArgumentList.forEach {
            val type = if (it.name.isGeneric()) Type.UnknownGenericType(it.name) else typeTable[it.name] //testing
            if (type != null) {
                newListOfTypeArgs.add(type)
            } else {
                alTypeArgsAreFound = false
            }
        }

        if (alTypeArgsAreFound) {
            copyTypeIfGenerics.typeArgumentList = newListOfTypeArgs
        } else {
            unResolvedMessageDeclarations.add(currentPackageName, statement)
            currentLevel--
            return true
        }
    }
    unResolvedMessageDeclarations.remove(currentPackageName, statement)
    statement.forType = copyTypeIfGenerics


    val bodyScope = mutableMapOf<String, Type>()

    val resolveBody = {

        val isStaticBuilderWithoutReceiver = statement is StaticBuilderDeclaration && !statement.withReceiver
        if (!isStaticBuilderWithoutReceiver && statement !is ConstructorDeclaration)
            bodyScope["this"] = copyTypeIfGenerics

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

        when (statement) {
            is MessageDeclarationKeyword -> {
                addArgsToBodyScope(statement)
            }

            is MessageDeclarationUnary -> {
                // unary has zero args
            }

            is MessageDeclarationBinary -> {
                val arg = statement.arg
                val argType = arg.typeAST?.toType(typeDB, typeTable)
                    ?: statement.token.compileError("Cant infer type of argument: `${YEL}${arg.name}${RED}` for binary message declaration `${YEL}${statement.forTypeAst.name} ${CYAN}${statement.name}`")
                bodyScope[arg.name] = argType
            }

            is ConstructorDeclaration ->
                if (statement.msgDeclaration is MessageDeclarationKeyword) // there is no binary constructors
                    addArgsToBodyScope(statement.msgDeclaration)

            is StaticBuilderDeclaration -> {

                addArgsToBodyScope(statement.msgDeclaration)
                // add lambda [this::forType, defaultAction::[Type -> ]]
                // with the same name as builder itself
                val lambda = Type.Lambda(
                    args = mutableListOf(
                        KeywordArg(
                            name = "this",
                            type = copyTypeIfGenerics
                        ),
                    ),
                    returnType = Resolver.defaultTypes[InternalTypes.Unit]!!,
                    pkg = getCurrentPackage(statement.token).packageName
                )
                val defaultAction = statement.defaultAction
                if (defaultAction != null && defaultAction.inputList.count() > 0 && defaultAction.inputList[0].typeAST != null) {
                    lambda.args.add(
                        KeywordArg(
                            name = "defaultAction",
                            type = Resolver.defaultTypes[InternalTypes.Any]!!//TODO use not any but lambda that takes f.typeAST!!.toType(typeDB, typeTable) but it not resolved yet
                        )
                    )
                }
                // lambda that calls body of the caller always named build
                bodyScope["build"] = lambda
            }
        }
        // add args to bodyScope, but not for constructors
        if (copyTypeIfGenerics is Type.UserLike && statement !is ConstructorDeclaration) {
            if (copyTypeIfGenerics.typeArgumentList.isNotEmpty()) {
                // check that all generics of receiver were instantiated
                val genericFields = copyTypeIfGenerics.fields.filter { it.name.isGeneric() }

                if (genericFields.count() > copyTypeIfGenerics.typeArgumentList.count()) {
                    statement.token.compileError("Not all generic arguments were instantiated got ${copyTypeIfGenerics.typeArgumentList} but generic fields are $genericFields")
                }
                // match types of args from forType to Generics fields\
                copyTypeIfGenerics.fields.forEachIndexed { i, it ->
                    if (it.type.name.isGeneric()) {
                        bodyScope[it.name] = copyTypeIfGenerics.typeArgumentList[i].copyAnyType()
                    } else
                        bodyScope[it.name] = it.type
                }
            } else
                copyTypeIfGenerics.fields.forEach {
                    bodyScope[it.name] = it.type
                }
        }
        wasThereReturn = null
        resolvingMessageDeclaration = statement
        resolve(statement.body, (previousScope + bodyScope).toMutableMap(), statement)
        // check that errors that returns and stack are the same
        val validateErrorsDeclarated = {
            val returnTypeAST = statement.returnTypeAST
            val possibleErrors = statement.stackOfPossibleErrors
            val declaredErrors = returnTypeAST?.errors
            val listOfErrorsWithLines = {
                buildString {
                    possibleErrors.forEach {
                        assert(it.errors.isNotEmpty())
                        append("\t" + it.msg.token.line, " | $WHITE", it.msg.receiver, " ", it.msg, RESET)
                        append(" !{$RED", it.errors.joinToString(" ") { it.name }, "$RESET}\n")
                    }
                }
            }

            fun errorDeclaredMismatch(
                possibleErrorsUnion: List<Type.Union>,
                possibleErrorsJoined: String,
                declaredErrors: List<String>
            ) {
                // maybe declared but wrong
                val setOfPossible = possibleErrorsUnion.map { it.name }.toSet()
                val setOfDeclared = declaredErrors.toSet()
                if (setOfDeclared != setOfPossible) {
                    val declErrors = declaredErrors.joinToString(" ")
                    val diff =
                        (if (setOfDeclared.count() > setOfPossible.count()) setOfDeclared - setOfPossible else setOfPossible - setOfDeclared)
                            .joinToString(" ")

                    statement.token.compileError(
                        "Possible errors of $WHITE$statement$RESET are: \n" +
                                "${listOfErrorsWithLines()}\n" +
                                "Wrong set of errors declarated\nPossible errors: $possibleErrorsJoined\nDeclared errors: $declErrors\nDiff: $diff"
                    )
                }
            }

            fun errorsMismatchCompileError() {
                val allPossibleErrors = possibleErrors.flatMap { it.errors }
                val possibleErrorsUnion = allPossibleErrors.distinct()
                val possibleErrorsJoined = possibleErrorsUnion.joinToString(" ") { it.name }


                // not declared anything at all
                if (declaredErrors == null) {
                    val singleOrManyErrors = if (allPossibleErrors.count() == 1)
                        allPossibleErrors[0].name
                    else "{$possibleErrorsJoined}"
                    val returnTypeASTOrUnit = if (returnTypeAST == null) Resolver.defaultTypes[InternalTypes.Unit]!! else returnTypeAST
                    val possibleSolutions =
                        "$WHITE-> $returnTypeASTOrUnit!$RESET or $WHITE-> $returnTypeASTOrUnit!$singleOrManyErrors$RESET"

                    statement.token.compileError("Possible errors of $WHITE$statement$RESET are: \n${listOfErrorsWithLines()} but you not declared them\n use $possibleSolutions")
                } else if (declaredErrors.count() != 0) {
                    errorDeclaredMismatch(possibleErrorsUnion, possibleErrorsJoined, declaredErrors)
                }
            }

            if (returnTypeAST != null) {
                // errors declared but there are no possible errors

                if (possibleErrors.isNotEmpty()) {
                    errorsMismatchCompileError()
                }
                // errors are possible but not declared
                if (possibleErrors.isEmpty() && declaredErrors?.isNotEmpty() == true) {
                    statement.token.compileError("there are declared errors:\n${listOfErrorsWithLines()}\nbut no possible ones you didn't specify them in return type: `-> Type!{Errors}`")
                }
            }
            if (returnTypeAST == null && possibleErrors.isNotEmpty()) {
                errorsMismatchCompileError()
//                statement.token.compileError("There is no possible errors but you declared ${possibleErrors.joinToString(", ")}")
            }

        }
        validateErrorsDeclarated()

        if (!statement.isSingleExpression && wasThereReturn == null && statement.returnTypeAST != null && statement.returnTypeAST.name != InternalTypes.Unit.name) {
            statement.token.compileError("You missed returning(^) a value of type: ${YEL}${statement.returnTypeAST.name}")
        }
        resolvingMessageDeclaration = null
        // change return type in db is single exp, because it was recorded as -> Unit, since there was no return type declarated
        if (statement.isSingleExpression) {
            val expr = statement.body[0]
            if (expr is Expression) {
                val typeOfSingleExpr = expr.type!!
                val mdgData = when (statement) {
                    is ConstructorDeclaration -> findStaticMessageType(
                        copyTypeIfGenerics,
                        statement.name,
                        statement.token
                    ).first

                    is MessageDeclarationUnary -> findAnyMsgType(
                        copyTypeIfGenerics,
                        statement.name,
                        statement.token,
                        MessageDeclarationType.Unary
                    )

                    is MessageDeclarationBinary -> findAnyMsgType(
                        copyTypeIfGenerics,
                        statement.name,
                        statement.token,
                        MessageDeclarationType.Binary
                    )

                    is MessageDeclarationKeyword -> findAnyMsgType(
                        copyTypeIfGenerics,
                        statement.name,
                        statement.token,
                        MessageDeclarationType.Keyword
                    )

                    is StaticBuilderDeclaration ->
                        TODO("single expr builder is impossible")
                }


                val declaredReturnTypeOrFromReturnStatement = statement.returnType

                mdgData.returnType = typeOfSingleExpr
                statement.returnType = typeOfSingleExpr
                //!st.isRecursive && cant understand why recursive check was here
                // ... because we cant infer type of recursive functions
                if (!statement.isRecursive && declaredReturnTypeOrFromReturnStatement != null && statement.returnTypeAST != null) {
                    // unpack null because return Int from -> Int? method is valid
                    if (!compare2Types(
                            typeOfSingleExpr,
                            declaredReturnTypeOrFromReturnStatement,
                            statement.token,
                            unpackNull = true,
                            isOut = true
                        )
                    )
                        statement.returnTypeAST.token.compileError("Return type defined: ${YEL}$declaredReturnTypeOrFromReturnStatement${RESET} but real type returned: ${YEL}$typeOfSingleExpr")
                    else if (mdgData.returnType != declaredReturnTypeOrFromReturnStatement) {
                        // change return type to the declarated one, since they are compatible
                        // because it can be type Null returned from -> Int? msg, but it must be Int? in such case
                        statement.returnType = declaredReturnTypeOrFromReturnStatement
                        mdgData.returnType = declaredReturnTypeOrFromReturnStatement
                    }
                }
            } else {
                TODO("Single-expression with not-expression, but statement, is not possible")
            }
        }


        if (GlobalVariables.isLspMode) {
            onEachStatement!!(statement, previousScope, previousScope, statement.token.file) // message decl
        }
    }


    // no return type declared, not recursive, single expr
    // body is not resolved and no returnTypeAst, so we cant infer return type
    // because not all declarations resolved yet
    if (statement.returnTypeAST == null && !statement.isRecursive && statement.isSingleExpression && !needResolveOnlyBody) {
        unResolvedSingleExprMessageDeclarations.add(currentPackageName, Pair(currentProtocolName,statement))
        currentLevel--
        return true
    }

    if (statement.returnTypeAST != null && statement.returnType == null) {
        try {
            val returnType = statement.returnTypeAST.toType(typeDB, typeTable)
            // we cant infer error effects from bidings, because they have no body
            // so we need to infer errors from ASTReturnType
            val isThisABinding = statement.body.isEmpty()
            statement.returnType =
                if (isThisABinding && statement.returnTypeAST.errors != null && returnType.errors == null) {
                    val copy = returnType.copyAnyType()
                    val errors = inferErrorTypeFromASTReturnTYpe(
                        statement.returnTypeAST.errors,
                        this.typeDB,
                        statement.returnTypeAST.token
                    )
                    copy.errors = errors

                    statement.stackOfPossibleErrors.add(
                        PairOfErrorAndMessage(
                            createFakeMsg(
                                statement.token,
                                copy
                            ), errors
                        )
                    )

                    copy
                } else returnType

        } catch (_: CompilerError) {
            unResolvedMessageDeclarations.add(currentPackageName, statement)
            currentLevel--
            return true
        }
    }


    val currentReturnType = statement.returnType
    // addToDb
    if (addToDb) {
        try {
            val x = addNewAnyMessage(statement, isGetter = false, isSetter = false, forType = typeFromDB)

            val errors = statement.returnType?.errors
            if (errors != null && x.errors == null ) {
                x.addErrors(errors)
            }
        } catch (_: CompilerError) {
            unResolvedMessageDeclarations.add(currentPackageName, statement)
            currentLevel--
            return true
        }
    }

    val isResolvedSingleExpr = if (statement.isSingleExpression && statement.body.isNotEmpty()) {
        val first = statement.body.first()
        first is Expression && first.type != null
    } else false
    if (needResolveOnlyBody && !isResolvedSingleExpr) {
        resolveBody()
        val newReturnType = statement.returnType
        // return type of expression changed after body resolved
        if (newReturnType != null && newReturnType != currentReturnType) {
            val msgData = findAnyMsgType(typeFromDB, statement.name, statement.token, statement.getDeclType())
            // change return type inside db
            msgData.returnType = newReturnType
        }

    }


    if (statement.returnType == null) {
        statement.returnType = Resolver.defaultTypes[InternalTypes.Unit]!!
    }

    if (statement is ConstructorDeclaration) {
        statement.msgDeclaration.forType = statement.forType
        statement.msgDeclaration.returnType = statement.returnType
    }



    return false
}
