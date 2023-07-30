package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.*
import frontend.util.capitalizeFirstLetter

//   // MESSAGES
//  messages = (unaryMessage+ binaryMessage* keywordMessage?)  -- unaryFirst
//  		 | (binaryMessage+ keywordMessage?) -- binaryFirst
//           | (keywordMessage) -- keywordFirst
fun Parser.messageSend(): MessageSend {
    // x echo // identifier
    // 1 echo // primary
    // (1 + 1) echo // parens
    // [1 2 3] // data structure

    // 3 + (1 / 2)
    // 3 + 8 to: 7


//    return newMessageSend()

    return anyMessageSend(false)
}


fun Parser.newMessageSend(): MessageSend {

    // parsing (receiver message?)+

//    val q = nullableSimpleReceiver() ?: throw Exception("bruh!")

    val q = step()
    val k = q.kind
//    when (k) {
//        TokenType.OpenBrace -> {
//
//        }
//
//    }

    TODO()
}



// 1^ sas sus sos -> {sas sus sos}
fun Parser.unaryMessagesMatching(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()
    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val identifier = identifierMayBeTyped()
        val unaryFirstMsg = UnaryMsg(receiver, identifier.name, identifier.names, null, identifier.token)
        unaryMessages.add(unaryFirstMsg)
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
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = identifierMayBeTyped()
        val binaryArgument = simpleReceiver() // 2
        val unaryForArg = unaryMessagesMatching(binaryArgument)
        val binaryMsg = BinaryMsg(
            receiver,
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
    }
    return binaryMessages
}

fun Parser.unaryOrBinary(
    inBrackets: Boolean,
    customReceiver: Receiver? = null,
    parsePipeAndCascade: Boolean = true
): MessageSend {
    var wasPipe = false
    var wasCascade = false

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
        val tok = peek()
        val kind = tok.kind
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
//                val keyword = keywordMessageParsing(false, firstReceiver)
//                pipedMsgs.add(keyword)
                error("sas!")
            }


        }
    }



    val cascadedMsgs = mutableListOf<Message>()
    // Cascade operator
    while (parsePipeAndCascade && match(TokenType.Cascade)) {
        wasCascade = true
        if (wasPipe) {
            error("Dont use pipe with cascade operator, better create different variables")
        }
        val tok = peek()
        val kind = tok.kind
        when {
            check(TokenType.BinarySymbol) -> {
                val binary = binaryMessagesMatching(firstReceiver, mutableListOf())
                cascadedMsgs.addAll(binary)
            }

            checkForKeyword() -> {
                val keyword = keywordMessageParsing(false, firstReceiver)
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

fun Parser.checkForKeyword(): Boolean {
    val savePoint = current
    var result = false
    val q = match(TokenType.Identifier)
    if (q) {
        val w = match(TokenType.Dot)
        if (w) {
            while (match(TokenType.Identifier)) {
                match(TokenType.Dot)
            }
            if (match(TokenType.Colon)) {
                result = true
            }

        } else {
            if (match(TokenType.Colon)) {
                result = true
            }
        }
    }


    current = savePoint
    return result
}





fun Parser.anyMessageSend(inBrackets: Boolean): MessageSend {
    // сначала попробовать унарное или бинарное
    val receiver = messageOrPrimaryReceiver()

    // если ресивер вернул сообщение значит дальше идет кейворд
    // если после парсинга унарно/бинарного дальше идет идент с колоном
    return if (receiver is UnaryMsg || receiver is BinaryMsg) {
        keyword(inBrackets, receiver)
    } else if (checkForKeyword()) {
        // keyword, 1 and 2 because identifier skip, this will break if the receiver takes more than one token
        keyword(inBrackets, receiver)
    } else
    // unary/binary
        unaryOrBinary(inBrackets, receiver)
}

fun Parser.keyword(
    inBrackets: Boolean,
    customReceiver: Receiver? = null
): MessageSend {
    // if unary/binary message receiver then we already parsed it somewhere on a higher level
    val receiver: Receiver = customReceiver ?: simpleReceiver()



    val keyColonCycle = {
        keywordMessageParsing(inBrackets, receiver)
    }

    val messages = mutableListOf<Message>()
    messages.add(keyColonCycle())
    while (match(TokenType.PipeOperator)) {
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
    inBrackets: Boolean,
    receiver: Receiver
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

        // trying to eat unary or binary, if there are non, then we got receiver
        val unaryOrBinaryForArgument =
            unaryOrBinary(inBrackets, null, false) // Тут может понядобится все таки передать ресивер
        val keyArg = unaryOrBinaryForArgument.receiver



        // making fun name camelCase
        stringBuilder.append(
            if (firstCycle) keywordPart.name
            else keywordPart.name.capitalizeFirstLetter()
        )


        val isUnaryOrIsBinary =
            unaryOrBinaryForArgument is MessageSendUnary || unaryOrBinaryForArgument is MessageSendBinary
        val unaryOrBinaryMsgForArg =
            if (isUnaryOrIsBinary && unaryOrBinaryForArgument.messages.isNotEmpty()) unaryOrBinaryForArgument.messages
            else null


        val x = KeywordArgAndItsMessages(
            selectorName = keywordPart.name,
            keywordArg = keyArg,
            unaryOrBinaryMsgsForArg = unaryOrBinaryMsgForArg
        )

        keyWordArguments.add(x)

        // if the keyword was split to 2 lines
        if (check(TokenType.EndOfLine) &&
            check(TokenType.Identifier, 1) &&
            check(TokenType.Colon, 2)
        )
            step()

        firstCycle = false
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
