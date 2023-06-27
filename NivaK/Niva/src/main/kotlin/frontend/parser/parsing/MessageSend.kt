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

//    val q = anyMessageSend(false)
//    do {
//        val w = anyMessageSend(false)
//        val e = w.messages[0]
//        q.messages.add(e)
//    } while (match(TokenType.PipeOperator))

    return anyMessageSend(false)
}


// 1^ sas sus sos -> {sas sus sos}
fun Parser.unaryMessagesMatching(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()
    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val tok = step()
        val unaryFirstMsg = UnaryMsg(receiver, tok.lexeme, null, tok)
        unaryMessages.add(unaryFirstMsg)
    }

    return unaryMessages
}

fun Parser.binaryMessagesMatching(
    receiver: Receiver,
    unaryMessagesForReceiver: MutableList<UnaryMsg>
): MutableList<BinaryMsg> {
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = step()
        val binaryArgument = receiver() // 2
        val unaryForArg = unaryMessagesMatching(binaryArgument)
        val binaryMsg = BinaryMsg(
            receiver,
            if (needAddMessagesForReceiverForBinary) unaryMessagesForReceiver else listOf(),
            binarySymbol.lexeme,
            null,
            binarySymbol,
            binaryArgument,
            unaryForArg
        )
        binaryMessages.add(binaryMsg)
        needAddMessagesForReceiverForBinary = false
    }
    return binaryMessages
}

fun Parser.unaryOrBinary(inBrackets: Boolean, customReceiver: Receiver? = null, usePipe: Boolean = true): MessageSend {
    val receiver: Receiver = customReceiver ?: receiver()


    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessagesForReceiver = unaryMessagesMatching(receiver)

//    if (match(TokenType.PipeOperator)) {
//        error("It's useless, unary always evaluates first")
//    }

    // 3 inc inc^ + 2 dec dec + ...
    var binaryMessages: List<Message> = binaryMessagesMatching(receiver, unaryMessagesForReceiver)


    // 1 inc |> inc useless!
    // 1 + 2 to: 3 useless!
    // 1 + 2 |> inc to: 3

    // Pipe operator
    // 1 + 2 |> inc inc inc
    if (usePipe && match(TokenType.PipeOperator)) {
        if (check(TokenType.BinarySymbol)) {
            // binary after binary
            error("It's useless, binary always evaluates after another binary")
        } else if (check(TokenType.Identifier) && binaryMessages.isNotEmpty()) {
            // unary after binary
            val unary = unaryMessagesMatching(receiver = binaryMessages.last())
            binaryMessages = (binaryMessages + unary).toMutableList()
        }
    }


    // if there is no binary message, that's mean there is only unary
    if (binaryMessages.isEmpty()) {
        return MessageSendUnary(
            receiver,
            messages = unaryMessagesForReceiver,
            inBrackets,
            null,
            receiver.token
        )
    }
    // its binary msg
    return MessageSendBinary(receiver, binaryMessages, inBrackets, null, receiver.token)
}


fun Parser.anyMessageSend(inBrackets: Boolean): MessageSend {
    // сначала попробовать унарное или бинарное
    val q = messageOrPrimaryReceiver()

    // если ресивер вернул сообщение значит дальше идет кейворд
    // если после парсинга унарно/бинарного дальше идет идент с колоном
    return if (q is UnaryMsg || q is BinaryMsg) {
        keyword(inBrackets, q)
    } else if (check(TokenType.Identifier) && check(TokenType.Colon, 1)) {
        // keyword, 1 and 2 because identifier skip, this will break if the receiver takes more than one token
        keyword(inBrackets, q)
    } else
    // unary/binary
        unaryOrBinary(inBrackets, q)
}

fun Parser.keyword(
    inBrackets: Boolean,
    customReceiver: Receiver? = null
): MessageSend {
    // if unary/binary message receiver then we already parsed it somewhere on a higher level
    val receiver: Receiver = customReceiver ?: receiver()



    val keyColonCycle = {
        val stringBuilder = StringBuilder()

        val keyWordArguments = mutableListOf<KeywordArgAndItsMessages>()
        var firstCycle = true
        do {
            val keywordPart = matchAssert(TokenType.Identifier, "Identifier expected inside keyword message send")
            matchAssert(TokenType.Colon, "Colon expected before argument, inside keyword message send") // skip colon
            // x from: ^3 inc to: 2 inc
            // x from: 3 ^inc to: 2 inc


            // trying to eat unary or binary, if there are non, then we got receiver
            val unaryOrBinaryForArgument =
                unaryOrBinary(inBrackets, null, false) // Тут может понядобится все таки передать ресивер
            val keyArg = unaryOrBinaryForArgument.receiver


            // making fun name camelCase
            stringBuilder.append(
                if (firstCycle) keywordPart.lexeme
                else keywordPart.lexeme.capitalizeFirstLetter()
            )


            val isUnaryOrIsBinary =
                unaryOrBinaryForArgument is MessageSendUnary || unaryOrBinaryForArgument is MessageSendBinary
            val unaryOrBinaryMsgForArg =
                if (isUnaryOrIsBinary && unaryOrBinaryForArgument.messages.isNotEmpty()) unaryOrBinaryForArgument.messages
                else null


            val x = KeywordArgAndItsMessages(
                selectorName = keywordPart.lexeme,
                keywordArg = keyArg,
                unaryOrBinaryMsgsForArg = unaryOrBinaryMsgForArg
            )

            keyWordArguments.add(x)

            // if the keyword was split to 2 lines
            if (check(TokenType.EndOfLine)) {
                if (check(TokenType.EndOfLine) &&
                    check(TokenType.Identifier, 1) &&
                    check(TokenType.Colon, 2)
                )
                    step()
            }
            firstCycle = false
        } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))
        val keywordMsg = KeywordMsg(receiver, stringBuilder.toString(), null, receiver.token, keyWordArguments)
        keywordMsg
    }

    val w = mutableListOf<Message>()
    w.add(keyColonCycle())
    while (match(TokenType.PipeOperator)) {
        w.add(keyColonCycle())
    }


    return MessageSendKeyword(
        receiver,
        w,
        inBrackets,
        null,
        receiver.token
    )
}
