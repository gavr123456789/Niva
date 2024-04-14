package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.*
import main.utils.CYAN
import main.utils.GREEN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMsgType
import main.frontend.resolver.findStaticMessageType
import main.utils.capitalizeFirstLetter
import main.utils.toCamelCase
import main.utils.isGeneric
import kotlin.collections.mutableMapOf


fun Resolver.resolveKeywordMsg(
    statement: KeywordMsg,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>

) {
    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

    // resolve just non-generic types of args
    resolveKwArgs(statement, previousAndCurrentScope, filterCodeBlock = true)

    // resolve receiverType
    val selectorName = statement.args.map { it.name }.toCamelCase()
    if (statement.receiver.type == null) {
        currentLevel++
        resolveSingle((statement.receiver), previousAndCurrentScope, statement)
        currentLevel--
    }
    val receiverType =
        statement.receiver.type
            ?: statement.token.compileError("Can't infer receiver $YEL${statement.receiver.str}${RESET} type")


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
            if (keywordReceiverType is Type.UserLike || keywordReceiverType is Type.InternalType) {
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

    var kind: KeywordLikeType = resolveKindOfKeyword(statement, receiverType)

    // collect generic from args
    val letterToTypeFromReceiver =
        if (receiverType is Type.UserLike) {
            getTableOfLettersFrom_TypeArgumentListOfType(receiverType)
        } else mutableMapOf<String, Type>()


    // find this keyword in db
    val kwTypeFromDB = when (kind) {
        KeywordLikeType.Keyword -> findAnyMsgType(
            receiverType,
            statement.selectorName,
            statement.token,
            MessageDeclarationType.Keyword
        ) as KeywordMsgMetaData

        KeywordLikeType.CustomConstructor -> {
            findStaticMessageType(
                receiverType,
                statement.selectorName,
                statement.token,
            ).first
        }

        KeywordLikeType.Constructor -> {
            // there is no fields in internal types
            if (receiverType is Type.InternalType) {
                val keys = statement.args.joinToString(", ") { it.name }
                statement.token.compileError("Type $CYAN$receiverType$RESET is internal and doesn't have fields or custom constructor: $WHITE$keys$RESET")
            } else null

        }

        else -> null
    }


    val argsTypesFromDb = when (kwTypeFromDB) {
        is UnaryMsgMetaData -> listOf()
        is BinaryMsgMetaData -> listOf()
        is KeywordMsgMetaData -> {
            // only for bind Fields feature
            if (kwTypeFromDB.isSetter) {
                kind = KeywordLikeType.Setter
                statement.kind = kind
            }
            if (statement.args.count() != kwTypeFromDB.argTypes.count()) {
                statement.token.compileError("Wrong number of arguments, $WHITE$kwTypeFromDB$RESET is needed, but you send $WHITE$statement")
            }
            kwTypeFromDB.argTypes.map { it.type }
        }

        null -> listOf()
    }


    if (kwTypeFromDB != null) {
        statement.pragmas = kwTypeFromDB.pragmas
    }


    // чтобы резолвнуть тип мне нужны резолвнутые типы у аргументов, потому что может быть такое что одинаковые имена но разные типы у них
    // но чтобы резолвать женерики мне уже нужен резолвнутый тип ресивера из дб
    // выход, резолвнуть аргументы, резолвнуть тип, резолвнуть дженерики

    val letterToTypeFromArgs = argsTypesFromDb.asSequence()
        .foldIndexed(mutableMapOf<String, Type>()) { i, map, acc ->
            val realArgTypeFromCallee = statement.args[i].keywordArg.type
            if (acc is Type.UnknownGenericType && realArgTypeFromCallee !is Type.UnknownGenericType && realArgTypeFromCallee != null) {
                map[acc.name] = realArgTypeFromCallee
            }
            map
        }


    // resolve args types
    resolveKwArgs(
        statement,
        previousAndCurrentScope,
        filterCodeBlock = false,
        argsTypesFromDb,
        letterToTypeFromArgs + letterToTypeFromReceiver
    )

    // resolve generic args types
    resolveKwArgsGenerics(statement, argsTypesFromDb, letterToTypeFromReceiver)

    // if receiverType is lambda then we need to check does it have same argument names and types
    if (receiverType is Type.Lambda) {
        val receiverArgs = receiverType.args

        if (receiverArgs.count() != statement.args.count()) {
            val setOfHaveFields = statement.args.map { it.name }.toSet()

            val setOfNeededFields = receiverArgs.map { it.name }.toSet()
            val extraOrMissed = statement.args.count() > receiverArgs.count()
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

        // check for alias

        val aliasToLambda = typeDB.lambdaTypes.values.find { compare2Types(it, receiverType) }

        val findKwForLambda = {
            val possibleKWName = statement.args.first().name + statement.args.drop(1).joinToString("") { it.name.capitalizeFirstLetter() }
            var result: KeywordMsgMetaData? = null
            if (aliasToLambda != null) {
                aliasToLambda.protocols.values.forEach {
                    val kw = it.keywordMsgs[possibleKWName]
                    if (kw != null) result = kw
                }
            }

            result
        }
        val kw = findKwForLambda()

        val realArgs = if (aliasToLambda != null && receiverArgs.first().name == aliasToLambda.args.first().name)
            aliasToLambda.args // message to lambda type itself, like [Int -> Int] Int: ...
        else if (kw != null) {
            kw.argTypes     // kw message declared for lambda alias
        } else
            receiverArgs // message to declared in lambda arguments [x::Int -> Int] x: ...

        statement.args.forEachIndexed { ii, it ->
            // name check
            // if it lambda, then any arg name is valid
            if (it.keywordArg.type !is Type.Lambda && it.name != realArgs[ii].name) {
                statement.token.compileError("$YEL${it.name}${RESET} is not valid arguments for codeblock $WHITE${statement.receiver.str}$RESET, the valid arguments are: $YEL${receiverType.args.map { it.name }}")
            }
            // type check
            val isTypesEqual =
                compare2Types(it.keywordArg.type!!, realArgs[ii].type, it.keywordArg.token)
            if (!isTypesEqual) {
                statement.token.compileError(
                    "Arg: $WHITE${it.keywordArg}$RESET::$YEL${it.keywordArg.type}$RESET for $WHITE${it.name}$RESET is not valid, $YEL${realArgs[ii].type}$RESET expected, $WHITE${statement.receiver.str}${RESET} has signature: $YEL${receiverType.args.map { it.type }}"
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
        val sameArgsInDbAndInStatement = argsTypesFromDb.count() == statement.args.count()
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
            if (sameArgsInDbAndInStatement) {
                val kwArgFromDb = argsTypesFromDb[i]
                val currentArgType = kwArg.keywordArg.type
                if (kwArgFromDb.name == kwArg.name && currentArgType != null && kwArgFromDb is Type.UnknownGenericType) {
                    val realTypeForKwFromDb = letterToTypeFromReceiver[kwArgFromDb.name]!!
                    val isResolvedGenericParamEqualRealParam =
                        compare2Types(realTypeForKwFromDb, currentArgType)
                    if (!isResolvedGenericParamEqualRealParam) {
                        statement.token.compileError("Generic type error, type $YEL${kwArgFromDb.name}${RESET} of $WHITE$statement${RESET} was resolved to $YEL$realTypeForKwFromDb${RESET} but found $YEL$currentArgType")
                    }
                }
            }
        }
    }

    fun checkThatKwArgsAreTypeFields(receiverFields: MutableList<KeywordArg>) {
        statement.args.forEach { kwArg ->
            val argFromDB = receiverFields.find { it.name == kwArg.name }
            if (argFromDB == null) {
                kwArg.keywordArg.token.compileError("Constructor of ${YEL}${statement.receiver} has fields: $CYAN${receiverFields.map { it.name }}${RESET}, not ${CYAN}${kwArg.name} ")
            }

            if (!compare2Types(argFromDB.type, kwArg.keywordArg.type!!, kwArg.keywordArg.token, unpackNull = true)) {
                kwArg.keywordArg.token.compileError("Inside constructor of $YEL${statement.receiver.type}$RESET, type of ${WHITE}${kwArg.name}${RESET} must be ${YEL}${argFromDB.type}${RESET}, not ${YEL}${kwArg.keywordArg.type} ")
            }
        }
        val typeFieldsNamesSet = statement.args.map { it.name }.toSet()
        val receiverFieldsSet = receiverFields.map { it.name }.toSet()

        if (typeFieldsNamesSet != receiverFieldsSet) { // !thisIsCustomConstructor &&
            statement.token.compileError("Inside constructor message for type ${YEL}${statement.receiver.str}${RESET} fields $CYAN$typeFieldsNamesSet${RESET} != $CYAN$receiverFieldsSet")
        }

    }

    when (kind) {

        KeywordLikeType.Constructor -> {
            statement.type = if (receiverType is Type.UserLike) {
                // collect all fields from parents
                val listOfAllParentsFields = mutableListOf<KeywordArg>()
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
                        "$YEL${statement.receiver} $CYAN${statement.args.joinToString(": ") { it.name }}:${RESET}"
                    val text =
                        if (extraOrMissed)
                            "For $errorText constructor call, extra fields are listed: $CYAN$whatIsMissingOrExtra"
                        else
                            "For $errorText constructor call, not all fields are listed, you missed: $CYAN$whatIsMissingOrExtra"
                    statement.token.compileError(text)
                }

                checkThatKwArgsAreTypeFields(receiverFields)

                // array add: 5
                // array is Array::T
                resolveReceiverGenericsFromArgs(receiverType, statement.args, statement.token)

            } else receiverType

        }

        KeywordLikeType.Setter -> {
            if (statement.type == null) {

                val argFromDb = (kwTypeFromDB as KeywordMsgMetaData).argTypes.first().type
                val arg = statement.args.first()

                val equalSetterTypes = compare2Types(argFromDb, arg.keywordArg.type!!, statement.token)
                if (!equalSetterTypes) {
                    statement.token.compileError("In the setter $WHITE'${statement.receiver} $statement' $YEL$argFromDb$RESET expected but got $YEL${arg.keywordArg.type}")
                }
                statement.type = Resolver.defaultTypes[InternalTypes.Unit]
            }
            // Nothing to do, because check for setter already sets the type of statement
        }

        // Custom constructor has no difference from Keyword message
        KeywordLikeType.Keyword, KeywordLikeType.CustomConstructor -> {
            if (statement.type != null)
                return

            val msgTypeFromDB = foundCustomConstructorDb ?: findAnyMsgType(
                receiverType,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Keyword
            )


            // type check args
            assert(statement.args.count() == argsTypesFromDb.count())

            statement.args.forEachIndexed { i, argAndItsMessages ->
                val typeOfArgFromDb = argsTypesFromDb[i]
                val typeOfArgFromDeclaration = argAndItsMessages.keywordArg.type!!

                val sameTypes =
                    compare2Types(typeOfArgFromDb, typeOfArgFromDeclaration, statement.token)
                if (!sameTypes) {
                    argAndItsMessages.keywordArg.token.compileError(
                        "Type of $WHITE${argAndItsMessages.keywordArg}$RESET is $YEL${typeOfArgFromDeclaration}${RESET} but $YEL${typeOfArgFromDb}${RESET} for argument $CYAN${argAndItsMessages.name}${RESET} required"
                    )
                }

            }


            val returnTypeFromDb = msgTypeFromDB.returnType

            val returnType2 =
                resolveReturnTypeIfGeneric(returnTypeFromDb, letterToTypeFromReceiver, receiverGenericsTable)

            // if return type was unwrapped nullable, we need to wrap it again
            val returnType =
                if (returnTypeFromDb is Type.NullableType && returnType2 !is Type.NullableType) Type.NullableType(
                    realType = returnType2
                ) else returnType2

            statement.type = returnType
        }

        KeywordLikeType.ForCodeBlock -> {
            throw Exception("We can't reach here, because we do early return")
        }
    }

}


fun Resolver.resolveKwArgs(
    statement: KeywordMsg,
    previousAndCurrentScope: MutableMap<String, Type>,
    filterCodeBlock: Boolean,
    argsTypesFromDb: List<Type>? = null,
    letterToRealType: Map<String, Type>? = null,
) {
    // first time we are resoling all args except the codeblocks
    val usualArgs = mutableListOf<KeywordArgAst>()
    val codeBlockArgs = mutableListOf<CodeBlock>()
    val codeBlocks = mutableListOf<KeywordArgAst>()
    val mapOfArgToDbArg = mutableMapOf<Expression, Type>()

    if (argsTypesFromDb != null && argsTypesFromDb.isNotEmpty())
        statement.args.forEachIndexed { i, it ->
            if (it.keywordArg is CodeBlock) {
                codeBlocks.add(it)
                codeBlockArgs.add(it.keywordArg)
            } else {
                usualArgs.add(it)
                // because non codeblock args already resolved
            }
            mapOfArgToDbArg[it.keywordArg] = argsTypesFromDb[i]
        }
    else
        statement.args.forEachIndexed { i, it ->
            if (it.keywordArg is CodeBlock) {
                codeBlocks.add(it)
                codeBlockArgs.add(it.keywordArg)
            } else usualArgs.add(it)
        }

    // add to letterList code blocks types from db
    if (!filterCodeBlock && letterToRealType != null) {

        codeBlockArgs.forEach {

            val typeFromDb = mapOfArgToDbArg[it]
            if (typeFromDb != null && typeFromDb is Type.Lambda) {
                // add types to known args
                typeFromDb.args.forEachIndexed { i, lambdaArgFromDb ->
                    val lambdaArgFromDBType = lambdaArgFromDb.type
                    if (lambdaArgFromDBType is Type.UnknownGenericType) {
                        val qwe = letterToRealType[lambdaArgFromDBType.name]
                        if (qwe != null && it.inputList.isNotEmpty()) {
                            it.inputList[i].type = qwe
                        }
                    }
                }
            }
        }
    }


    // cant do this, because then argNum is wrong
    val args = if (filterCodeBlock) usualArgs else codeBlocks
    args.forEachIndexed { argNum, it ->
        val arg = it.keywordArg
        if (arg.type == null) {
            currentLevel++
            currentArgumentNumber = statement.args.indexOf(it)
            resolveSingle((arg), previousAndCurrentScope, statement)
            if (arg.type == null) arg.token.compileError("Compiler bug: can't resolve type of argument: ${WHITE}${it.name}: ${it.keywordArg}")
            currentLevel--
        }
    }
}


fun Resolver.resolveKwArgsGenerics(
    statement: KeywordMsg,
    argTypesFromDb: List<Type>,
    letterToRealType: MutableMap<String, Type>
) {
    val args = statement.args
    args.forEachIndexed { argNum, it ->
        // we need to check for generic args only if it is Keyword
        val argType = it.keywordArg.type!!
        val typeFromDBForThisArg = argTypesFromDb.getOrNull(argNum)
            ?: return


        // this is T
        if (typeFromDBForThisArg.name.isGeneric()) {
            // check that receiver wasn't already resolved to different type
            val receiverType = statement.receiver.type
            if (receiverType != null && receiverType is Type.UserLike && receiverType.typeArgumentList.isNotEmpty()) {
                // find the arg with same letter
                fillGenericsWithLettersByOrder(receiverType)
                val argTypeWithSameLetter =
                    receiverType.typeArgumentList.find { it.beforeGenericResolvedName == typeFromDBForThisArg.name }
                if (argTypeWithSameLetter != null) {
                    // receiver has the same generic param resolved
                    if (!compare2Types(argType, argTypeWithSameLetter)) {
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
    currentArgumentNumber = -1
}


@Suppress("UnusedReceiverParameter")
fun Resolver.resolveReturnTypeIfGeneric(
    returnTypeFromDb: Type,
    letterToRealType: MutableMap<String, Type>,
    receiverGenericsTable: MutableMap<String, Type>
): Type {
    ///
    val returnTypeOrNullUnwrap = returnTypeFromDb.unpackNull()


    return if (returnTypeOrNullUnwrap is Type.UnknownGenericType) {
        val realTypeFromTable =
            letterToRealType[returnTypeOrNullUnwrap.name] ?: receiverGenericsTable[returnTypeOrNullUnwrap.name]
            ?: returnTypeFromDb
//            ?: throw Exception("Cant find generic type $YEL${returnTypeOrNullUnwrap.name}${RESET} in letterToRealType table $YEL$letterToRealType$RESET")
        realTypeFromTable
    }
    // если ретурн тип ту стринг есть среди параметров функции имеющих дженерики, или
    else if (returnTypeOrNullUnwrap is Type.UserLike && returnTypeOrNullUnwrap.typeArgumentList.isNotEmpty()) {
        // что если у обычного кейворда возвращаемый тип имеет нересолвнутые женерик параметры
        // идем по каждому, если он не резолвнутый, то добавляем из таблицы, если резолвнутый то добавляем так
        replaceAllGenericsToRealTypeRecursive(returnTypeOrNullUnwrap, letterToRealType, receiverGenericsTable)
    } else
        returnTypeFromDb // return without changes

}
