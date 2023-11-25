package frontend.typer

import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*
import frontend.util.toCalmelCase

fun fillGenericsWithLettersByOrder(type: Type.UserLike) {
    if (type.typeArgumentList.count() > 2) {
        throw Exception("Generics with more than 2 params are not supported yet")
    }
    val genericLetters = listOf("T", "G")

    type.typeArgumentList.forEachIndexed { i, it ->
//        if (it.beforeGenericResolvedName == null) {
        val k = genericLetters[i]
        it.beforeGenericResolvedName = k // тут у нас один и тот же инт
//        }
    }
}


fun Resolver.resolveKwArgs(
    statement: KeywordMsg,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    filterCodeBlock: Boolean
) {
    val args = if (filterCodeBlock)
        statement.args.filter { it.keywordArg !is CodeBlock }
    else statement.args

    args.forEachIndexed { argNum, it ->
        if (it.keywordArg.type == null) {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            currentLevel++
            currentArgumentNumber = argNum
            resolve(listOf(it.keywordArg), previousAndCurrentScope, statement)
            currentLevel--
        }
    }
}


fun Resolver.resolveKwArgsGenerics(
    statement: KeywordMsg,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    kwTypeFromDB: KeywordMsgMetaData?,
    letterToRealType: MutableMap<String, Type>
) {
    val args = statement.args
    args.forEachIndexed { argNum, it ->
        // we need to check for generic args only if it is Keyword
        if (kwTypeFromDB != null) {
            val argType = it.keywordArg.type!!


            val typeFromDBForThisArg = kwTypeFromDB.argTypes[argNum].type

            // this is T
            if (typeFromDBForThisArg.name.length == 1 && typeFromDBForThisArg.name[0].isUpperCase()) {
                letterToRealType[typeFromDBForThisArg.name] = argType
            }
            // This is Box::T
            if (typeFromDBForThisArg is Type.UserLike && argType is Type.UserLike && typeFromDBForThisArg.typeArgumentList.isNotEmpty()) {
                // get resolved generic type from real argument
                if (argType.name == typeFromDBForThisArg.name && argType.typeArgumentList.count() == typeFromDBForThisArg.typeArgumentList.count()) {
                    argType.typeArgumentList.forEachIndexed { i, type ->
                        val fromDb = typeFromDBForThisArg.typeArgumentList[i]
                        if (fromDb.name.length == 1 && fromDb.name[0].isUpperCase() && !(type.name.length == 1 && type.name[0].isUpperCase())) {
                            letterToRealType[fromDb.name] = type
                        }
                    }
                } else {
                    throw Exception("Something strange in generic resolving going on, ${argType.name} != ${typeFromDBForThisArg.name}")
                }

            }



            if (typeFromDBForThisArg is Type.Lambda) {
                if (argType !is Type.Lambda) {
                    throw Exception("If typeFromDBForThisArg is lambda then argType must be lambda")
                }
                /// remember letter to type args
                typeFromDBForThisArg.args.forEachIndexed { i, typeField ->
                    val beforeGenericResolvedName = typeField.type.beforeGenericResolvedName
                    if (typeField.type.name.length == 1 && typeField.type.name[0].isUpperCase()) {
                        letterToRealType[typeField.type.name] = argType.args[i].type
                    } else if (beforeGenericResolvedName != null && beforeGenericResolvedName.length == 1 && beforeGenericResolvedName[0].isUpperCase()) {
                        letterToRealType[beforeGenericResolvedName] = argType.args[i].type
                    }
                }
                /// remember letter to return type
                val returnTypeBefore = typeFromDBForThisArg.returnType.beforeGenericResolvedName

                if (typeFromDBForThisArg.returnType.name.length == 1 && typeFromDBForThisArg.returnType.name[0].isUpperCase()) {
                    letterToRealType[typeFromDBForThisArg.returnType.name] = argType.returnType
                } else if (returnTypeBefore != null && returnTypeBefore.length == 1 && returnTypeBefore[0].isUpperCase()) {
                    letterToRealType[returnTypeBefore] = argType.returnType
                }

            }
        }
    }
    currentArgumentNumber = -1
}

fun Resolver.resolveReturnTypeIfGeneric(
    statement: KeywordMsg,
    kwTypeFromDB: KeywordMsgMetaData?,
    receiverType: Type,
    letterToRealType: MutableMap<String, Type>
) {
    val returnName = kwTypeFromDB?.returnType?.name ?: ""
    val returnTypeIsSingleGeneric = returnName.length == 1 && returnName[0].isUpperCase()
    val returnTypeIsNestedGeneric =
        kwTypeFromDB != null && kwTypeFromDB.returnType is Type.UserLike && kwTypeFromDB.returnType.typeArgumentList.isNotEmpty()

    // infer return generic type from args or receiver
    if (kwTypeFromDB != null &&
        (returnTypeIsSingleGeneric) &&
        kwTypeFromDB.returnType is Type.UserLike && receiverType is Type.UserLike
    ) {
        fillGenericsWithLettersByOrder(receiverType)

        val inferredGenericTypeFromArgs = letterToRealType[kwTypeFromDB.returnType.name]
        // take type from receiver
        if (inferredGenericTypeFromArgs == null) {
            if (receiverType.typeArgumentList.isEmpty()) {
                statement.receiver.token.compileError("Nor receiver type, nor arguments are generic, but method is generic, so can't infer real type")
            }
            val receiverGenericType =
                receiverType.typeArgumentList.find { it.beforeGenericResolvedName == kwTypeFromDB.returnType.name }
            if (receiverGenericType != null) {
                statement.type = receiverGenericType
            } else {
                statement.receiver.token.compileError("Can't infer generic return type from receiver generic type")
            }
        } else {
            // take type from arguments
            statement.type = inferredGenericTypeFromArgs
        }
    }
}

fun Resolver.resolveMessage(
    statement: Message,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
) {

    when (statement) {
        is KeywordMsg -> {
            // resolve just non generic types of args
            resolveKwArgs(statement, currentScope, previousScope, true)

            // resolve receiverType
            val selectorName = statement.args.map { it.selectorName }.toCalmelCase()
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(listOf(statement.receiver), previousAndCurrentScope, statement)
                currentLevel--
            }
            val receiverType =
                statement.receiver.type
                    ?: statement.token.compileError("Can't infer receiver ${statement.receiver.str} type")

            resolveKwArgs(statement, currentScope, previousScope, false)


            // resolve kw kind
            var foundCustomConstructorDb: MessageMetadata? = null
            fun resolveKindOfKeyword(statement: KeywordMsg, receiverType: Type): KeywordLikeType {
                if (receiverType is Type.Lambda) {
                    return KeywordLikeType.ForCodeBlock
                }
                val receiver = statement.receiver
                val receiverText = receiver.toString()
//                val keywordReceiverType2 = getType(receiverText, currentScope, previousScope)
                // TODO! resolve type for Bracket expressions, maybe?
//                val testDB = if (statement.receiver is IdentifierExpr) typeDB.getType(
//                    receiverText,
//                    currentScope,
//                    previousScope
//                ) else null
//
                val keywordReceiverType = receiverType//testDB?.getTypeFromTypeDBResult(statement)

                if (receiverText == "Project" || receiverText == "Bind") {
                    statement.token.compileError("We cant get here, type Project are ignored")
                }
                val isThisConstructor = receiver is IdentifierExpr && receiver.names.last() == keywordReceiverType.name
                if (isThisConstructor) {
                    if (keywordReceiverType is Type.UserUnionRootType) {
                        statement.token.compileError("You can't instantiate Union root: ${keywordReceiverType.name}")
                    }
                    // check if custom
                    if (keywordReceiverType is Type.UserLike) {
                        keywordReceiverType.protocols.values.forEach {
                            if (it.staticMsgs.containsKey(selectorName)) {
                                foundCustomConstructorDb = it.staticMsgs[selectorName]
                                statement.kind = KeywordLikeType.CustomConstructor
                                return KeywordLikeType.CustomConstructor
                            }
                        }
                    }
                    statement.kind = KeywordLikeType.Constructor
                    return KeywordLikeType.Constructor
                }
                // This is Setter or Keyword now

                // if the amount of keyword's arg is 1, and its name on of the receiver field, then its setter
                if (statement.args.count() == 1 && receiverType is Type.UserType) {
                    val keyArgText = statement.args[0].selectorName
                    // find receiver arg same as keyArgText
                    val receiverArgWithSameName = receiverType.fields.find { it.name == keyArgText }
                    if (receiverArgWithSameName != null) {
                        // this is setter
                        statement.kind = KeywordLikeType.Setter
                        statement.type = receiverArgWithSameName.type
                        return KeywordLikeType.Setter
                    }
                }
                // this is Keyword
                statement.kind = KeywordLikeType.Keyword
                return KeywordLikeType.Keyword
            }

            val kind = resolveKindOfKeyword(statement, receiverType)

            val letterToRealType = mutableMapOf<String, Type>()

            val kwTypeFromDB = if (kind == KeywordLikeType.Keyword)
                findKeywordMsgType(
                    receiverType,
                    statement.selectorName,
                    statement.token
                )
            else null

            if (kwTypeFromDB != null) {
                statement.pragmas = kwTypeFromDB.pragmas
            }

            // чтобы резолвнуть тип мне нужны резолвнутые типы у аргументов, потому что может быть такое что одинаковые имена но разные типы у них
            // но чтобы резолвать женерики мне уже нужен резолвнутый тип из дб
            // выход, резолвнуть аргументы, резолвнуть тип, резолвнуть дженерики

            // resolve generic args types
            resolveKwArgsGenerics(statement, currentScope, previousScope, kwTypeFromDB, letterToRealType)


            resolveReturnTypeIfGeneric(statement, kwTypeFromDB, receiverType, letterToRealType)

            // if receiverType is lambda then we need to check does it have same argument names and types
            if (receiverType is Type.Lambda) {

                if (receiverType.args.count() != statement.args.count()) {
                    val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                    val setOfNeededFields = receiverType.args.map { it.name }.toSet()
                    val extraOrMissed = statement.args.count() > receiverType.args.count()
                    val whatIsMissingOrExtra =
                        if (extraOrMissed)
                            (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                        else
                            (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }

                    val beginText =
                        statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                    val text =
                        if (extraOrMissed)
                            "For $beginText code block eval, extra fields are listed: $whatIsMissingOrExtra"
                        else
                            "For $beginText code block eval, not all fields are listed, you missed: $whatIsMissingOrExtra"
                    statement.token.compileError(text)
                }

                statement.args.forEachIndexed { ii, it ->
                    // name check
                    // if it lambda, then any arg name is valid
                    if (it.keywordArg.type !is Type.Lambda && it.selectorName != receiverType.args[ii].name) {
                        statement.token.compileError("${it.selectorName} is not valid arguments for lambda ${statement.receiver.str}, the valid arguments are: ${receiverType.args.map { it.name }} on Line ${statement.token.line}")
                    }
                    // type check
                    val isTypesEqual = compare2Types(it.keywordArg.type!!, receiverType.args[ii].type)
                    if (!isTypesEqual) {
                        statement.token.compileError(
                            "${it.selectorName} is not valid type for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.keywordArg.type?.name }}"
                        )
                    }
                }

                statement.type = receiverType.returnType
                statement.kind = KeywordLikeType.ForCodeBlock
                return
            }

            // check all generics are same type
            // if resolver is generic then
            if (receiverType is Type.UserLike && receiverType.typeArgumentList.isNotEmpty()) {
                val receiverTable = mutableMapOf<String, Type>()
                receiverType.typeArgumentList.forEach {
                    val beforeResolveName = it.beforeGenericResolvedName
                    if (beforeResolveName != null) {
                        receiverTable[beforeResolveName] = it
                    }
                }
                statement.args.forEach { kwArg ->
                    val argType = kwArg.keywordArg.type
                    if (argType is Type.UserLike && argType.typeArgumentList.isNotEmpty()) {
                        argType.typeArgumentList.forEach {
                            val beforeName = it.beforeGenericResolvedName
                            if (beforeName != null) {
                                val typeFromReceiver = receiverTable[beforeName]
                                if (typeFromReceiver != null && !compare2Types(typeFromReceiver, it)) {
                                    statement.token.compileError("Generic param of receiver: `${typeFromReceiver.name}` is different from\n\targ: `${kwArg.selectorName}: ${kwArg.keywordArg.str}::${it.name}` but both must be $beforeName")
                                }
                            }
                        }
                    }
                }
            }


            when (kind) {
                KeywordLikeType.CustomConstructor -> {
                    val capture = foundCustomConstructorDb

                    if (capture != null) {
                        statement.type = capture.returnType
                        statement.pragmas = capture.pragmas
                    } else {
                        statement.type = receiverType
                    }
                }

                KeywordLikeType.Constructor -> {
                    // check that all fields are filled
                    var replacerTypeIfItGeneric: Type? = null
                    if (receiverType is Type.UserType) {
                        // collect all fields from parents
                        val listOfAllParentsFields = mutableListOf<TypeField>()
                        var parent = receiverType.parent
                        while (parent != null && parent is Type.UserType) {
                            listOfAllParentsFields.addAll(parent.fields)
                            parent = parent.parent
                        }

                        val receiverFields = receiverType.fields //+ listOfAllParentsFields
                        // check that amount of arguments if right
                        if (statement.args.count() != receiverFields.count()) { // && !thisIsCustomConstructor

                            val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                            val setOfNeededFields = receiverFields.map { it.name }.toSet()
                            val extraOrMissed = statement.args.count() > receiverFields.count()
                            val whatIsMissingOrExtra =
                                if (extraOrMissed)
                                    (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                                else
                                    (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }


                            val errorText =
                                statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                            val text =
                                if (extraOrMissed)
                                    "For $errorText constructor call, extra fields are listed: $whatIsMissingOrExtra"
                                else
                                    "For $errorText constructor call, not all fields are listed, you missed: $whatIsMissingOrExtra"
                            statement.token.compileError(text)
                        }

                        val typeFieldsNamesSet = statement.args.map { it.selectorName }.toSet()
                        val receiverFieldsSet = receiverFields.map { it.name }.toSet()



                        if (typeFieldsNamesSet != receiverFieldsSet) { // !thisIsCustomConstructor &&
                            statement.token.compileError("In constructor message for type ${statement.receiver.str} fields $typeFieldsNamesSet != $receiverFieldsSet")
                        }

                        // replace every Generic type with real
                        if (receiverType.typeArgumentList.isNotEmpty()) {
                            replacerTypeIfItGeneric = receiverType.copy()

                            // match every type argument with fields
                            // replace fields types to real one
                            val map = mutableMapOf<String, Type>()
                            replacerTypeIfItGeneric.typeArgumentList.forEach { typeArg ->
                                val fieldsOfThisType =
                                    replacerTypeIfItGeneric.fields.filter { it.type.name == typeArg.name }
                                fieldsOfThisType.forEach { genericField ->
                                    // find real type from arguments
                                    val real = statement.args.find { it.selectorName == genericField.name }
                                        ?: statement.token.compileError("Can't find real type for field: ${genericField.name} of generic type: ${genericField.type.name}")
                                    val realType = real.keywordArg.type
                                        ?: real.keywordArg.token.compileError("Panic: ${real.selectorName} doesn't have type")
                                    genericField.type = realType
                                    map[typeArg.name] = realType
                                }
                            }
                            // replace typeFields to real ones
                            val realTypes = replacerTypeIfItGeneric.typeArgumentList.toMutableList()
                            map.forEach { (fieldName, fieldRealType) ->
                                val fieldIndex = realTypes.indexOfFirst { it.name == fieldName }
                                realTypes[fieldIndex] = fieldRealType
                            }
                            replacerTypeIfItGeneric.typeArgumentList = realTypes
                        }

                    }


                    statement.type = replacerTypeIfItGeneric ?: receiverType
                }

                KeywordLikeType.Setter -> {
                    // Nothing to do, because check for setter already sets the type of statement
                }

                KeywordLikeType.Keyword -> {
                    if (statement.type != null)
                        return

                    val msgTypeFromDB = findKeywordMsgType(receiverType, statement.selectorName, statement.token)

                    val returnType = if (msgTypeFromDB.returnType is Type.UnknownGenericType) {
                        val realTypeFromTable = letterToRealType[msgTypeFromDB.returnType.name]
                            ?: throw Exception("Cant find generic type ${msgTypeFromDB.returnType.name} in letterToRealType table $letterToRealType")
                        realTypeFromTable
                    } else if (msgTypeFromDB.returnType is Type.UserType && msgTypeFromDB.returnType.typeArgumentList.find { it.name.length == 1 } != null) {
                        // что если у обычного кейворда возвращаемый тип имеет нересолвнутые женерик параметры
                        val returnType = msgTypeFromDB.returnType
                        val newResolvedTypeArgs = mutableListOf<Type>()

                        // идем по каждому, если он не резолвнутый, то добавляем из таблицы, если резолвнутый то добавляем так
                        returnType.typeArgumentList.forEach { typeArg ->
                            val isNotResolved = typeArg.name.length == 1 && typeArg.name[0].isUpperCase()
                            if (isNotResolved) {
                                val resolvedLetterType = letterToRealType[typeArg.name]
                                    ?: throw Exception("Can't find generic type: ${typeArg.name} in letter table")
                                newResolvedTypeArgs.add(resolvedLetterType)
                                resolvedLetterType.beforeGenericResolvedName = typeArg.name
                            } else {
                                newResolvedTypeArgs.add(typeArg)
                            }
                        }

                        Type.UserType(
                            name = returnType.name,
                            typeArgumentList = newResolvedTypeArgs,
                            fields = returnType.fields,
                            isPrivate = returnType.isPrivate,
                            pkg = returnType.pkg,
                            protocols = returnType.protocols
                        )
                    } else msgTypeFromDB.returnType

                    // type check args
                    if (kwTypeFromDB != null) {
                        assert(statement.args.count() == kwTypeFromDB.argTypes.count())

                        statement.args.forEachIndexed { i, argAndItsMessages ->
                            val typeOfArgFromDb = kwTypeFromDB.argTypes[i].type
                            val typeOfArgFromDeclaration = argAndItsMessages.keywordArg.type!!
                            val sameTypes =
                                compare2Types(typeOfArgFromDb, typeOfArgFromDeclaration)
                            if (!sameTypes) {
                                argAndItsMessages.keywordArg.token.compileError(
                                    "In keyword message ${statement.selectorName} type ${typeOfArgFromDeclaration.name} for argument ${argAndItsMessages.selectorName} doesn't match ${typeOfArgFromDb.name}"
                                )
                            }
                        }
                    }
                    statement.type = returnType
                }

                KeywordLikeType.ForCodeBlock -> {
                    throw Exception("We can't reach here, because we do early return")
                }
            }

        }

        is BinaryMsg -> {
            val receiver = statement.receiver

            receiver.type = when (receiver) {
                is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, previousScope, currentScope)
                is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)
                is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                is LiteralExpression.CharExpr -> Resolver.defaultTypes[InternalTypes.Char]

                is KeywordMsg, is UnaryMsg, is BinaryMsg -> receiver.type

                is MessageSend, is MapCollection, is ListCollection, is SetCollection, is CodeBlock -> TODO()
            }
            val receiverType = receiver.type!!


            // resolve messages
            if (statement.unaryMsgsForArg.isNotEmpty()) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForArg, previousAndCurrentScope, statement)
                currentLevel--
            }

            val isUnaryForReceiver = statement.unaryMsgsForReceiver.isNotEmpty()
            if (isUnaryForReceiver) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForReceiver, previousAndCurrentScope, statement)
                currentLevel--
            }

            // 1 < (this get: 0)
            if (statement.argument is ExpressionInBrackets) {
                resolveExpressionInBrackets(statement.argument, currentScope, previousScope)
            }


            // q = "sas" + 2 toString
            // find message for this type
            val messageReturnType =
                if (isUnaryForReceiver)
                    findBinaryMessageType(
                        statement.unaryMsgsForReceiver.last().type!!,
                        statement.selectorName,
                        statement.token
                    )
                else
                    findBinaryMessageType(receiverType, statement.selectorName, statement.token)
            statement.type = messageReturnType.returnType
            statement.pragmas = messageReturnType.pragmas

        }

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
            val receiver = statement.receiver

            // for constructors

            if (receiver.type == null)
                receiver.type = when (receiver) {

                    is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, currentScope, previousScope)
                    is CodeBlock -> TODO()
                    is ListCollection -> {
                        currentLevel++
                        resolve(listOf(receiver), (currentScope + previousScope).toMutableMap())
                        currentLevel--
                        receiver.type!!
                    }

                    is MapCollection -> TODO()
                    is SetCollection -> TODO()

                    // receiver
                    is UnaryMsg -> TODO()
                    is BinaryMsg -> TODO()
                    is KeywordMsg -> TODO()

                    is MessageSend -> TODO()


                    is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)


                    is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                    is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                    is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                    is LiteralExpression.CharExpr -> Resolver.defaultTypes[InternalTypes.Char]
                }

            // if this is message for type
            val isStaticCall =
                receiver is IdentifierExpr && typeTable[receiver.str] != null//testing

            val testDB = if (receiver is IdentifierExpr)
                typeDB.getTypeOfIdentifierReceiver(
                    receiver,
                    receiver,
                    getCurrentImports(receiver.token),
                    currentScope,
                    previousScope
                ) else null

            val receiverType = receiver.type!!



            if (receiverType is Type.Lambda) {
                if (statement.selectorName != "do") {
                    if (receiverType.args.isNotEmpty())
                        statement.token.compileError("Lambda ${statement.str} takes more than 0 arguments, please use keyword message with it's args names")
                    else
                        statement.token.compileError("For lambda ${statement.str} you can use only unary 'do' message")
                }


                if (receiverType.args.isNotEmpty()) {
                    statement.token.compileError("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                }

                statement.type = receiverType.returnType
                statement.kind = UnaryMsgKind.ForCodeBlock
                return
            }


            val checkForGetter = {
                if (receiverType is Type.UserLike) {
                    val fieldWithSameName = receiverType.fields.find { it.name == statement.selectorName }
                    Pair(fieldWithSameName != null, fieldWithSameName)
                } else Pair(false, null)
            }

            val (isGetter, w) = checkForGetter()
            if (isGetter) {
                statement.kind = UnaryMsgKind.Getter
                statement.type = w!!.type

            } else {
                // usual message
                // find this message

                if (!isStaticCall) {
                    val messageReturnType = findUnaryMessageType(receiverType, statement.selectorName, statement.token)
                    val receiverType = receiver.type
                    statement.kind = if (messageReturnType.isGetter) UnaryMsgKind.Getter else UnaryMsgKind.Unary


                    statement.type = if (messageReturnType.returnType is Type.UnknownGenericType) {
                        if (receiverType is Type.UserLike) {
                            receiverType.typeArgumentList.find { it.beforeGenericResolvedName == messageReturnType.returnType.name }
                                ?: statement.token.compileError("Cant infer return generic type of unary ${statement.selectorName}")
                        } else {
                            throw Exception("Return type is generic, but receiver of ${statement.selectorName} is not user type")
                        }
                    } else {
                        messageReturnType.returnType
                    }

                    statement.pragmas = messageReturnType.pragmas
                } else {
                    val (messageReturnType, isGetter) = findStaticMessageType(
                        receiverType,
                        statement.selectorName,
                        statement.token,
                        MessageDeclarationType.Unary
                    )

                    statement.kind = if (isGetter) UnaryMsgKind.Getter else UnaryMsgKind.Unary
                    statement.type = messageReturnType.returnType
                    statement.pragmas = messageReturnType.pragmas
                }

            }
        }
    }
}
