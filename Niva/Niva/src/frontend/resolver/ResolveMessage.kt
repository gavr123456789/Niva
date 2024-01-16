@file:Suppress("UnusedReceiverParameter")

package frontend.resolver

import frontend.meta.Token
import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*
import frontend.resolver.Type.RecursiveType.copy
import frontend.util.toCalmelCase
import main.*
import main.utils.isGeneric

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
            if (it.keywordArg.type == null) it.keywordArg.token.compileError("Compiler bug: can't resolve type of argument: ${WHITE}${it.name}: ${it.keywordArg}")
            currentLevel--
        }
    }
}


fun Resolver.resolveKwArgsGenerics(
    statement: KeywordMsg,
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
            if (typeFromDBForThisArg.name.isGeneric()) {
                // check that receiver wasn't already resolved to different type
                val receiverType = statement.receiver.type
                if (receiverType != null && receiverType is Type.UserLike && receiverType.typeArgumentList.isNotEmpty()) {
                    // find the arg with same letter
                    fillGenericsWithLettersByOrder(receiverType)
                    val argTypeWithSameLetter = receiverType.typeArgumentList.find { it.beforeGenericResolvedName == typeFromDBForThisArg.name }
                    if (argTypeWithSameLetter != null) {
                        // receiver has the same generic param resolved
                        if (!compare2Types(argType, argTypeWithSameLetter)) {
//                            it.keywordArg.token.compileError("${CYAN}${it.name}$RESET: $WHITE${it.keywordArg}$RESET arg has type $YEL$argType${RESET} but generic type of  ${YEL}${statement.receiver.type}$RESET was resolved to ${YEL}$argTypeWithSameLetter$RESET")
                            it.keywordArg.token.compileError("${CYAN}${it.name}$RESET: $WHITE${it.keywordArg}$RESET arg has type $YEL$argType${RESET} but ${YEL}$argTypeWithSameLetter$RESET expected")
                        }
                    }
                }
                letterToRealType[typeFromDBForThisArg.name] = argType
            }
            // This is Box::T
            if (typeFromDBForThisArg is Type.UserLike && argType is Type.UserLike && typeFromDBForThisArg.typeArgumentList.isNotEmpty()) {
                // get resolved generic type from real argument
                if (argType.name == typeFromDBForThisArg.name && argType.typeArgumentList.count() == typeFromDBForThisArg.typeArgumentList.count()) {
                    argType.typeArgumentList.forEachIndexed { i, type ->
                        val fromDb = typeFromDBForThisArg.typeArgumentList[i]
                        if (fromDb.name.isGeneric() && !(type.name.isGeneric())) {
                            letterToRealType[fromDb.name] = type
                        }
                    }
                } else {
                    throw Exception("Something strange in generic resolving going on, $YEL${argType.name}$RESET != $YEL${typeFromDBForThisArg.name}")
                }

            }


            if (typeFromDBForThisArg is Type.Lambda) {
                if (argType !is Type.Lambda) {
                    throw Exception("If typeFromDBForThisArg is codeblock then argType must be codeblock too")
                }
                /// remember letter to type args
                typeFromDBForThisArg.args.forEachIndexed { i, typeField ->
                    val beforeGenericResolvedName = typeField.type.beforeGenericResolvedName
                    if (typeField.type.name.isGeneric()) {
                        letterToRealType[typeField.type.name] = argType.args[i].type
                    } else if (beforeGenericResolvedName != null && beforeGenericResolvedName.isGeneric()) {
                        letterToRealType[beforeGenericResolvedName] = argType.args[i].type
                    }
                }
                /// remember letter to return type
                val returnTypeBefore = typeFromDBForThisArg.returnType.beforeGenericResolvedName

                if (typeFromDBForThisArg.returnType.name.isGeneric()) {
                    letterToRealType[typeFromDBForThisArg.returnType.name] = argType.returnType
                } else if (returnTypeBefore != null && returnTypeBefore.isGeneric()) {
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
    val returnType = kwTypeFromDB?.returnType
    val returnName = returnType?.name ?: ""
    val returnTypeIsSingleGeneric = returnName.isGeneric()

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

    //infer return type as last expression

}


fun findThisInScopes(
    token: Token,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
) = previousScope["this"] ?: currentScope["this"] ?: token.compileError("Cant resolve type of receiver for dot expression")


fun Resolver.resolveMessage(
    statement: Message,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
) {

    when (statement) {
        is KeywordMsg -> {
            // resolve just non-generic types of args
            resolveKwArgs(statement, currentScope, previousScope, true)

            // resolve receiverType
            val selectorName = statement.args.map { it.name }.toCalmelCase()
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(listOf(statement.receiver), previousAndCurrentScope, statement)
                currentLevel--
            }
            val receiverType =
                statement.receiver.type
                    ?: statement.token.compileError("Can't infer receiver $YEL${statement.receiver.str}${RESET} type")

            resolveKwArgs(statement, currentScope, previousScope, false)


            // resolve kw kind
            var foundCustomConstructorDb: MessageMetadata? = null
            fun resolveKindOfKeyword(statement: KeywordMsg, receiverType: Type): KeywordLikeType {
                if (receiverType is Type.Lambda) {
                    return KeywordLikeType.ForCodeBlock
                }
                val receiver = statement.receiver
                val receiverText = receiver.toString()
                val keywordReceiverType = receiverType

                if (receiverText == "Project" || receiverText == "Bind") {
                    statement.token.compileError("We cant get here, type Project are ignored")
                }
                val isThisConstructor = receiver is IdentifierExpr && receiver.names.last() == keywordReceiverType.name
                if (isThisConstructor) {

                    if (keywordReceiverType is Type.UserUnionRootType) {
                        statement.token.compileError("You can't instantiate Union root: $YEL${keywordReceiverType.name}")
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
                    val keyArgText = statement.args[0].name
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
            // fill letterToRealTypeFromReceiver if it generic
            if (receiverType is Type.UserLike) {
                val listOfLetters = listOf("T", "G")
                if (receiverType.typeArgumentList.count() > 2)
                receiverType.typeArgumentList.forEachIndexed { i, it ->
                    letterToRealType[listOfLetters[i]] = it
                }
            }

            // чтобы резолвнуть тип мне нужны резолвнутые типы у аргументов, потому что может быть такое что одинаковые имена но разные типы у них
            // но чтобы резолвать женерики мне уже нужен резолвнутый тип из дб
            // выход, резолвнуть аргументы, резолвнуть тип, резолвнуть дженерики

            // resolve generic args types
            resolveKwArgsGenerics(statement, kwTypeFromDB, letterToRealType)


            resolveReturnTypeIfGeneric(statement, kwTypeFromDB, receiverType, letterToRealType)

            // if receiverType is lambda then we need to check does it have same argument names and types
            if (receiverType is Type.Lambda) {

                if (receiverType.args.count() != statement.args.count()) {
                    val setOfHaveFields = statement.args.map { it.name }.toSet()
                    val setOfNeededFields = receiverType.args.map { it.name }.toSet()
                    val extraOrMissed = statement.args.count() > receiverType.args.count()
                    val whatIsMissingOrExtra =
                        if (extraOrMissed)
                            (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                        else
                            (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }

                    val beginText =
                        statement.receiver.str + " " + statement.args.joinToString(": ") { it.name } + ":"
                    val text =
                        if (extraOrMissed)
                            "For $WHITE$beginText$RESET code block eval, extra fields are listed: $CYAN$whatIsMissingOrExtra"
                        else
                            "For $WHITE$beginText$RESET code block eval, not all fields are listed, you missed: $CYAN$whatIsMissingOrExtra"
                    statement.token.compileError(text)
                }

                statement.args.forEachIndexed { ii, it ->
                    // name check
                    // if it lambda, then any arg name is valid
                    if (it.keywordArg.type !is Type.Lambda && it.name != receiverType.args[ii].name) {
                        statement.token.compileError("$YEL${it.name}${RESET} is not valid arguments for codeblock $WHITE${statement.receiver.str}$RESET, the valid arguments are: $YEL${receiverType.args.map { it.name }}")
                    }
                    // type check
                    val isTypesEqual = compare2Types(it.keywordArg.type!!, receiverType.args[ii].type, it.keywordArg.token)
                    if (!isTypesEqual) {
                        statement.token.compileError(
                            "Arg: $WHITE${it.keywordArg}$RESET::$YEL${it.keywordArg.type}$RESET for $WHITE${it.name}$RESET is not valid type for codeblock $WHITE${statement.receiver.str}${RESET}, the valid arguments are: $YEL${receiverType.args.map { it.type }}"
                        )
                    }
                }

                statement.type = receiverType.returnType
                statement.kind = KeywordLikeType.ForCodeBlock
                return
            }

            val receiverGenericsTable = mutableMapOf<String, Type>()
            // check all generics of receiver are same type
            if (receiverType is Type.UserLike && receiverType.typeArgumentList.isNotEmpty()) {
                receiverType.typeArgumentList.forEach {
                    val beforeResolveName = it.beforeGenericResolvedName
                    if (beforeResolveName != null) {
                        receiverGenericsTable[beforeResolveName] = it
                    }
                }
                statement.args.forEachIndexed { i, kwArg ->
                    val argType = kwArg.keywordArg.type
                    if (argType is Type.UserLike && argType.typeArgumentList.isNotEmpty()) {
                        argType.typeArgumentList.forEach {
                            val beforeName = it.beforeGenericResolvedName
                            if (beforeName != null) {
                                val typeFromReceiver = receiverGenericsTable[beforeName]
                                if (typeFromReceiver != null && !compare2Types(typeFromReceiver, it)) {
                                    statement.token.compileError("Generic param of receiver: `$YEL${typeFromReceiver.name}${RESET}` is different from\n\targ: `$WHITE${kwArg.name}${RESET}: $YEL${kwArg.keywordArg.str}$GREEN::$YEL${it.name}${RESET}` but both must be $YEL$beforeName")
                                }
                            }
                        }
                    }
                    // check that kw from db is the same as real arg type
                    // like x = {1}, x add: "str" is error
                    val kwArgFromDb = kwTypeFromDB?.argTypes?.get(i)
                    val kwArgFromDbType = kwArgFromDb?.type
                    val currentArgType = kwArg.keywordArg.type
                    if (kwArgFromDb != null && kwArgFromDb.name == kwArg.name && currentArgType != null && kwArgFromDbType is Type.UnknownGenericType) {
                        val realTypeForKwFromDb = letterToRealType[kwArgFromDbType.name]!!
                        val isResolvedGenericParamEqualRealParam = compare2Types(realTypeForKwFromDb, currentArgType)
                        if (!isResolvedGenericParamEqualRealParam) {
                            statement.token.compileError("Generic type error, type $YEL${kwArgFromDbType.name}${RESET} of $WHITE$statement${RESET} was resolved to $YEL$realTypeForKwFromDb${RESET} but found $YEL$currentArgType")
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
                    var replacerTypeIfItGeneric: Type.UserLike? = null
                    if (receiverType is Type.UserLike) {
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

                            val setOfHaveFields = statement.args.map { it.name }.toSet()
                            val setOfNeededFields = receiverFields.map { it.name }.toSet()
                            val extraOrMissed = statement.args.count() > receiverFields.count()
                            val whatIsMissingOrExtra =
                                if (extraOrMissed)
                                    (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                                else
                                    (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }


                            val errorText =
                                "$YEL${statement.receiver.str} $CYAN${statement.args.joinToString(": ") { it.name }}:${RESET}"
                            val text =
                                if (extraOrMissed)
                                    "For $errorText constructor call, extra fields are listed: $CYAN$whatIsMissingOrExtra"
                                else
                                    "For $errorText constructor call, not all fields are listed, you missed: $CYAN$whatIsMissingOrExtra"
                            statement.token.compileError(text)
                        }
                        // check types
                        statement.args.forEach { kwArg ->
                            val argFromDB = receiverFields.find { it.name == kwArg.name }
                            if (argFromDB == null) {
                                kwArg.keywordArg.token.compileError("Constructor of ${YEL}${statement.receiver} has fields: $CYAN${receiverFields.map { it.name }}${RESET}, not ${CYAN}${kwArg.name} ")
                            }
                            if (!compare2Types( argFromDB.type, kwArg.keywordArg.type!!, kwArg.keywordArg.token)) {
                                kwArg.keywordArg.token.compileError("Inside constructor of $YEL${statement.receiver.type}$RESET, type of ${WHITE}${kwArg.name}${RESET} must be ${YEL}${argFromDB.type}${RESET}, not ${YEL}${kwArg.keywordArg.type} ")
                            }
                        }

                        val typeFieldsNamesSet = statement.args.map { it.name }.toSet()
                        val receiverFieldsSet = receiverFields.map { it.name }.toSet()



                        if (typeFieldsNamesSet != receiverFieldsSet) { // !thisIsCustomConstructor &&
                            statement.token.compileError("Inside constructor message for type ${YEL}${statement.receiver.str}${RESET} fields $CYAN$typeFieldsNamesSet${RESET} != $CYAN$receiverFieldsSet")
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
                                    val real = statement.args.find { it.name == genericField.name }
                                        ?: statement.token.compileError("Can't find real type for field: $YEL${genericField.name}${RESET} of generic type: $YEL${genericField.type.name}${RESET}")
                                    val realType = real.keywordArg.type
                                        ?: real.keywordArg.token.compileError("Compiler bug: $YEL${real.name}${RESET} doesn't have type")
                                    // genericField.type = realType
                                    map[typeArg.name] = realType
                                }
                            }
                            // replace typeFields to real ones
                            val realTypes = replacerTypeIfItGeneric.typeArgumentList.toMutableList()
                            map.forEach { (fieldName, fieldRealType) ->
                                val fieldIndex = realTypes.indexOfFirst { it.name == fieldName }
                                realTypes[fieldIndex] = fieldRealType
                                // replace all fields of generic type
                                replacerTypeIfItGeneric.fields.forEach {
                                    if (it.type.name == fieldName) {
                                        it.type = fieldRealType
                                    }
                                }
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
                    val returnTypeFromDb = msgTypeFromDB.returnType
                    val nullUnwrap = if (returnTypeFromDb is Type.NullableType) returnTypeFromDb.realType else returnTypeFromDb

                    val returnType2 = if (nullUnwrap is Type.UnknownGenericType) {
                        val realTypeFromTable = letterToRealType[nullUnwrap.name]
                            ?: throw Exception("Cant find generic type $YEL${nullUnwrap.name}${RESET} in letterToRealType table $YEL$letterToRealType$RESET")
                        realTypeFromTable
                    } else if (nullUnwrap is Type.UserLike && nullUnwrap.typeArgumentList.isNotEmpty()) {
                        // что если у обычного кейворда возвращаемый тип имеет нересолвнутые женерик параметры
                        // идем по каждому, если он не резолвнутый, то добавляем из таблицы, если резолвнутый то добавляем так
                        recursiveGenericResolving(nullUnwrap, letterToRealType, receiverGenericsTable)
                    }
                    else msgTypeFromDB.returnType

                    // type check args
                    if (kwTypeFromDB != null) {
                        assert(statement.args.count() == kwTypeFromDB.argTypes.count())

                        statement.args.forEachIndexed { i, argAndItsMessages ->
                            val typeOfArgFromDb = kwTypeFromDB.argTypes[i].type
                            val typeOfArgFromDeclaration = argAndItsMessages.keywordArg.type!!
                            val sameTypes =
                                compare2Types(typeOfArgFromDb, typeOfArgFromDeclaration, statement.token)
                            if (!sameTypes) {
                                argAndItsMessages.keywordArg.token.compileError(
                                    "In keyword message $CYAN${statement.selectorName}${RESET} type $YEL${typeOfArgFromDeclaration.name}${RESET} for argument $CYAN${argAndItsMessages.name}${RESET} doesn't match $YEL${typeOfArgFromDb.name}${RESET}"
                                )
                            }

                        }
                    }

                    // if return type was unwrapped nullable, we need to wrap it again
                    val returnType = if (returnTypeFromDb is Type.NullableType) Type.NullableType(realType = returnType2) else returnType2

                    statement.type = returnType
                }

                KeywordLikeType.ForCodeBlock -> {
                    throw Exception("We can't reach here, because we do early return")
                }
            }

        }

        is BinaryMsg -> {
            val receiver = statement.receiver

            if (receiver.type == null) {
                resolve(listOf(receiver), (currentScope + previousScope).toMutableMap(), statement)
            }

            val receiverType = receiver.type
                ?: statement.token.compileError("Can't resolve return type of $CYAN${statement.selectorName}${RESET} binary msg")


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

            // 1 < (this at: 0)
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

            if (receiver.type == null) {
                currentLevel++
                resolve(listOf(receiver), (currentScope + previousScope).toMutableMap(), statement)
                currentLevel--
                receiver.type ?: statement.token.compileError("Can't resolve type of $CYAN${statement.selectorName}${RESET} unary msg: $YEL${receiver.str}")
            }

            fun checkForStatic(receiver: Receiver): Boolean =
                if (receiver.type is Type.UserEnumRootType) false
                else receiver is IdentifierExpr && typeTable[receiver.str] != null


            // if this is message for type
            val isStaticCall = checkForStatic(receiver)

//            val testDB = if (receiver is IdentifierExpr)
//                typeDB.getTypeOfIdentifierReceiver(
//                    receiver.name,
//                    receiver,
//                    getCurrentImports(receiver.token),
//                    currentPackageName,
//                    currentScope,
//                    previousScope,
//                    names = receiver.names
//                ) else null

            val receiverType = receiver.type!!



            if (receiverType is Type.Lambda) {
                if (statement.selectorName != "do") {
                    if (receiverType.args.isNotEmpty())
                        statement.token.compileError("Codeblock $WHITE${statement.str}${RESET} takes more than 0 arguments, please use keyword message with it's args names")
                    else
                        statement.token.compileError("For codeblock $WHITE${statement.str}${RESET} you can use only unary 'do' message")
                }


                if (receiverType.args.isNotEmpty()) {
                    statement.token.compileError("Codeblock $WHITE${statement.str}${RESET} takes more than 0 arguments, please use keyword message with it's args names")
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
                // usual message or static message

                // check for recursion
                val resolvingMsgDecl = this.resolvingMessageDeclaration
                if (resolvingMsgDecl?.name == statement.selectorName && !resolvingMsgDecl.isRecursive) {
                    resolvingMsgDecl.isRecursive = true
                    if (resolvingMsgDecl.isSingleExpression && resolvingMsgDecl.returnTypeAST == null) {
                        resolvingMsgDecl.token.compileError("Recursive single expression methods must describe its return type explicitly")
                    }
                }

                // resolve message from db and its generic params
                if (!isStaticCall) {
                    val messageReturnType = findUnaryMessageType(receiverType, statement.selectorName, statement.token)
                    val receiverType2 = receiver.type
                    statement.kind = if (messageReturnType.isGetter) UnaryMsgKind.Getter else UnaryMsgKind.Unary


                    statement.type = if (messageReturnType.returnType is Type.UnknownGenericType) {
                        if (receiverType2 is Type.UserLike) {
                            receiverType2.typeArgumentList.find { it.beforeGenericResolvedName == messageReturnType.returnType.name }
                                ?: statement.token.compileError("Cant infer return generic type of unary $CYAN${statement.selectorName}")
                        } else {
                            throw Exception("Return type is generic, but receiver of $YEL${statement.selectorName}${RESET} is not user type")
                        }
                    } else {
                        messageReturnType.returnType
                    }


                    statement.pragmas = messageReturnType.pragmas
                } else {
                    // static call
                    val (messageReturnType, isGetter2) = findStaticMessageType(
                        receiverType,
                        statement.selectorName,
                        statement.token,
                        MessageDeclarationType.Unary
                    )
//                    if (receiver is IdentifierExpr) {
//                        receiver.isConstructor = false
//                    }

                    statement.kind = if (isGetter2) UnaryMsgKind.Getter else UnaryMsgKind.Unary
                    statement.type = messageReturnType.returnType
                    statement.pragmas = messageReturnType.pragmas
                }

            }
        }
    }
}


fun recursiveGenericResolving(type: Type.UserLike, letterToRealType: MutableMap<String, Type>, receiverGenericsTable: MutableMap<String, Type>): Type.UserType {
    val newResolvedTypeArgs2 = mutableListOf<Type>()

    val copyType = type.copy()

    copyType.typeArgumentList.forEach { typeArg ->
        val isSingleGeneric = typeArg.name.isGeneric()

        if (isSingleGeneric) {
            val resolvedLetterType =
                letterToRealType[typeArg.name] ?: receiverGenericsTable[typeArg.name]
                ?: throw Exception("Can't find generic type: $YEL${typeArg.name}${RESET} in letter table")
            newResolvedTypeArgs2.add(resolvedLetterType)
            resolvedLetterType.beforeGenericResolvedName = typeArg.name
        } else if (typeArg is Type.UserLike && type.typeArgumentList.isNotEmpty()) {
            newResolvedTypeArgs2.add(recursiveGenericResolving(typeArg, letterToRealType, receiverGenericsTable))
        } else {
            newResolvedTypeArgs2.add(typeArg)
        }
    }

    return Type.UserType(
        name = copyType.name,
        typeArgumentList = newResolvedTypeArgs2,
        fields = copyType.fields,
        isPrivate = copyType.isPrivate,
        pkg = copyType.pkg,
        protocols = copyType.protocols
    )
}
