package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.util.capitalizeFirstLetter

////   // MESSAGES
////  messages = (unaryMessage+ binaryMessage* keywordMessage?)  -- unaryFirst
////  		 | (binaryMessage+ keywordMessage?) -- binaryFirst
////           | (keywordMessage) -- keywordFirst
//fun Parser.messageSend(dontParseKeywords: Boolean): MessageSend {
//    // x echo // identifier
//    // 1 echo // primary
//    // (1 + 1) echo // parens
//    // [1 2 3] // data structure
//
//    // 3 + (1 / 2)
//    // 3 + 8 to: 7
//
//
////    return anyMessageSend2(mutableListOf())
//    return anyMessageSend(dontParseKeywords)
//}

// 1^ sas sus sos -> {sas sus sos}
fun Parser.unaryMessagesMatching(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()

    // if we have
    // a
    //   b
    // situation, where b is unary for a
//    if (check(TokenType.EndOfLine) && check(TokenType.Identifier, 1) && !check(TokenType.Colon, 2) &&
//        peek(1).spaces > receiver.token.spaces
//    ) {
//        step()
//    }

    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val identifier = identifierMayBeTyped()
        if (identifier.typeAST != null) {
            identifier.token.compileError("Error: You can't put type on a unary message send: ${identifier.token.lexeme + "::" + identifier.typeAST.name}, line: ${identifier.token.line}")
        }
        if (check(TokenType.Colon)) {
//            identifier.token.compileError("Error: This is not unary, but a keyword with path")
            throw Exception("This is not unary, but a keyword with path")
        }
        // each unary message must have previous unary as receiver because
        // person name echo -- receiver of echo is name, not person
        val receiver2 = if (unaryMessages.isNotEmpty()) unaryMessages.last() else receiver
        val unaryFirstMsg = UnaryMsg(
            receiver2,
            identifier.name,
            identifier.names,
            null,
            identifier.token
        )
        unaryMessages.add(unaryFirstMsg)


//        if (check(TokenType.EndOfLine) && check(TokenType.Identifier, 1) && !check(TokenType.Colon, 2) &&
//            peek(1).spaces > receiver.token.spaces
//        ) {
//            step()
//        }

    }

    return unaryMessages
}

// 1^ + 2 + 3
fun Parser.binaryMessagesMatching(
    receiver: Receiver,
    unaryMessagesForReceiver: MutableList<UnaryMsg>
): MutableList<BinaryMsg> {
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    var previousBinaryParsed: BinaryMsg? = null
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = identifierMayBeTyped()
        val binaryArgument = simpleReceiver() // 2
        val unaryForArg = unaryMessagesMatching(binaryArgument)
        val binaryMsg = BinaryMsg(
            previousBinaryParsed ?: receiver,
            if (needAddMessagesForReceiverForBinary) unaryMessagesForReceiver else listOf(),
            binarySymbol.name,
            null,
            binarySymbol.token,
            binaryArgument,
            unaryForArg,
            binarySymbol.names
        )
        binaryMessages.add(binaryMsg)
        needAddMessagesForReceiverForBinary = false
        previousBinaryParsed = binaryMsg
    }
    return binaryMessages
}

fun Parser.unaryOrBinary(
    customReceiver: Receiver? = null,
    parsePipeAndCascade: Boolean = true,
): MessageSend {
    var wasPipe = false
//    var wasCascade = false

    val firstReceiver: Receiver = customReceiver ?: simpleReceiver()


    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessages = unaryMessagesMatching(firstReceiver)

    // 3 inc inc^ + 2 dec dec + ...
    val binaryMessages = binaryMessagesMatching(firstReceiver, unaryMessages)

    val takeLastMessage = {
        if (binaryMessages.isNotEmpty()) {
            binaryMessages.last()
        } else {
            unaryMessages.last()
        }
    }

    // 1 inc |> inc useless!
    // 1 + 2 to: 3 useless!
    // 1 + 2 |> inc to: 3

    // Pipe operator

    while (parsePipeAndCascade && match(TokenType.PipeOperator)) {
        wasPipe = true
        when {

            check(TokenType.Identifier) -> {
                val lastMsg = takeLastMessage()
                val unary = unaryMessagesMatching(lastMsg)
                binaryMessages.forEach {
                    it.inBracket = true
                }
                unaryMessages.forEach {
                    it.inBracket = true
                }
                unaryMessages.addAll(unary)

                if (check(TokenType.BinarySymbol)) {
                    val binary = binaryMessagesMatching(takeLastMessage(), mutableListOf())
                    binaryMessages.addAll(binary)
                }
            }

            check(TokenType.BinarySymbol) -> {
                val binary = binaryMessagesMatching(takeLastMessage(), mutableListOf())
                binaryMessages.forEach {
                    it.inBracket = true
                }
                binaryMessages.addAll(binary)
            }

            checkForKeyword() -> {
                error("sas!")
            }


        }
    }


    val cascadedMsgs = mutableListOf<Message>()
    // Cascade operator
    while (parsePipeAndCascade && match(TokenType.Cascade)) {
//        wasCascade = true
        if (wasPipe) {
            error("Dont use pipe with cascade operator, better create different variables")
        }

        when {
            check(TokenType.BinarySymbol) -> {
                val binary = binaryMessagesMatching(firstReceiver, mutableListOf())
                cascadedMsgs.addAll(binary)
            }

            checkForKeyword() -> {
                val keyword = keywordMessageParsing(firstReceiver)
                cascadedMsgs.add(keyword)
            }

            check(TokenType.Identifier) -> {
                val unary = unaryMessagesMatching(firstReceiver)
                cascadedMsgs.addAll(unary)
            }
        }
    }


    // if there is no binary message, that's mean there is only unary
    if (binaryMessages.isEmpty()) {
        return MessageSendUnary(
            firstReceiver,
            messages = unaryMessages + cascadedMsgs,
            null,
            firstReceiver.token
        )
    }
    // its binary msg
    return MessageSendBinary(firstReceiver, binaryMessages + cascadedMsgs, null, firstReceiver.token)
}

// Receiver ^ from: to:
fun Parser.checkForKeyword(): Boolean {
    val savePoint = current
    var result = false
    skipNewLinesAndComments()
    val identTok = match(TokenType.Identifier)
    if (identTok) {
        val dot = match(TokenType.Dot)
        if (dot) {
            while (match(TokenType.Identifier)) {
                match(TokenType.Dot)
            }
            if (match(TokenType.Colon)) {
                result = true
            }

        } else {
//            skipNewLinesAndComments()
            if (match(TokenType.Colon)) {
                result = true
            }
        }
    }


    current = savePoint
    return result
}


fun Parser.messageSend(dontParseKeywords: Boolean, dotReceiver: Boolean = false): MessageSend {

    val keywordOnReceiverWithoutMessages = if (dontParseKeywords) false else {
        val savepoint = current
        dotSeparatedIdentifiers()
        val result = checkForKeyword()
        current = savepoint
        result
    }

    val receiver = when {
        // not keyword then parse unaryBinary with custom receiver
        !keywordOnReceiverWithoutMessages && dotReceiver -> {

            unaryOrBinaryMessageOrPrimaryReceiver(
                DotReceiver(
                    null,
                    matchAssert(TokenType.Dot)
                ))
        }
        // pure keyword, then add dot receiver
        keywordOnReceiverWithoutMessages && dotReceiver -> DotReceiver(
            null,
            matchAssert(TokenType.Dot)
        )
        !keywordOnReceiverWithoutMessages -> unaryOrBinaryMessageOrPrimaryReceiver()
        else -> simpleReceiver()
    }

    val isNextKeyword = if (dontParseKeywords) false else checkForKeyword()
    val isReceiverUnaryOrBinaryOrCodeBlock =
        receiver is MessageSendBinary || receiver is MessageSendUnary || receiver is CodeBlock


    return when {
        isReceiverUnaryOrBinaryOrCodeBlock && isNextKeyword -> {
            keyword(receiver)
        }

        isNextKeyword -> {
            keyword(receiver)
        }

        !isNextKeyword && (receiver is MessageSendBinary) -> {
            receiver
        }

        else -> {
            MessageSendUnary(
                receiver,
                listOf(),
                token = receiver.token
            )
        }
    }
}


fun Parser.keyword(
    customReceiver: Receiver? = null,
): MessageSend {
    // if unary/binary message receiver then we already parsed it somewhere on a higher level
    val receiver: Receiver = customReceiver ?: simpleReceiver()
    skipNewLinesAndComments()

    val keyColonCycle = {
        keywordMessageParsing(receiver)
    }

    val messages = mutableListOf<Message>()
    messages.add(keyColonCycle())
    while (match(TokenType.PipeOperator)) {
        skipNewLinesAndComments()
        messages.add(keyColonCycle())
    }


    return MessageSendKeyword(
        receiver,
        messages,
        null,
        receiver.token
    )
}

fun Parser.keywordMessageParsing(
    receiver: Receiver,
): KeywordMsg {
    val stringBuilder = StringBuilder()

    val keyWordArguments = mutableListOf<KeywordArgAndItsMessages>()
    var firstCycle = true
    var firstKeywordIdentifierExpr: IdentifierExpr? = null
    do {
        val keywordPart =
            identifierMayBeTyped() //matchAssert(TokenType.Identifier, "Identifier expected inside keyword message send")

        matchAssert(TokenType.Colon, "Colon expected before argument, inside keyword message send") // skip colon
        // x from: ^3 inc to: 2 inc
        // x from: 3 ^inc to: 2 inc
        if (firstCycle) {
            firstKeywordIdentifierExpr = keywordPart
        }

        val argument = expression(true)

        if (argument is KeywordMsg) {
            argument.token.compileError("argument can't be another keyword message, use (), foo: (x bar: y)")
        }

        // making fun name camelCase
        stringBuilder.append(
            if (firstCycle) keywordPart.name
            else keywordPart.name.capitalizeFirstLetter()
        )

        val x = KeywordArgAndItsMessages(
            name = keywordPart.name,
            keywordArg = argument,
        )

        keyWordArguments.add(x)

        // if the keyword was split to 2 lines
        if (check(TokenType.EndOfLine) &&
            check(TokenType.Identifier, 1) &&
            check(TokenType.Colon, 2)
        )
            step()

        firstCycle = false
        skipNewLinesAndComments()
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))
    val keywordMsg = KeywordMsg(
        receiver,
        stringBuilder.toString(),
        null,
        receiver.token,
        keyWordArguments,
        firstKeywordIdentifierExpr?.names!!
    )
    return keywordMsg
}
