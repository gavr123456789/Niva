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


    return anyMessageSend(false)
}


// 1^ sas sus sos -> {sas sus sos}
fun Parser.getAllUnary(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()
    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val tok = step()
        val unaryFirstMsg = UnaryMsg(receiver, tok.lexeme, null, tok)
        unaryMessages.add(unaryFirstMsg)
    }

    return unaryMessages
}

fun Parser.unaryOrBinary(inBrackets: Boolean, customReceiver: Receiver? = null): MessageSend {
    val receiver: Receiver = customReceiver ?: primaryReceiver()


    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessagesForReceiver = getAllUnary(receiver) // inc inc
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = step()
        val binaryArgument = primaryReceiver() // 2
        val unaryForArg = getAllUnary(binaryArgument)
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


    // if there is no binary message, that's mean there is only unary
    if (binaryMessages.isEmpty()) {
        return MessageSendUnary(
            receiver,
            unaryMessagesForReceiver,
            inBrackets,
            null,
            receiver.token
        )
    }
    // its binary msg
    return MessageSendBinary(receiver, binaryMessages, inBrackets, null, receiver.token)
}

fun Parser.anyMessageSend(inBrackets: Boolean): MessageSend {
    // TODO сначала пробовать парсить коллекцию или блок кода, это добавить в новый вид парсинга симпл ресиверов


    // сначала попробовать унарное или бинарное
    val q = messageOrPrimaryReceiver()
    // если ресивер вернул сообщение значит дальше идет кейворд
    // если после парсинга унарно/бинарного дальше идет идент с колоном
    if (q is UnaryMsg || q is BinaryMsg) {
        return keyword(inBrackets, q)
    }

    // keyword, 1 and 2 because identifier skip, this will broke if receiver takes more than one token
    if (check(TokenType.Identifier) && check(TokenType.Colon, 1)) {
        return keyword(inBrackets, q)
    }
    // unary/binary
    return unaryOrBinary(inBrackets, q)
}

fun Parser.keyword(
    inBrackets: Boolean,
    customReceiver: Receiver? = null
): MessageSend {
    // if unary/binary message receiver then we already parsed it somewhere on a higher level
    val receiver: Receiver = customReceiver ?: primaryReceiver()

    val stringBuilder = StringBuilder()
    val unaryAndBinaryMessages = mutableListOf<KeywordMsg>()
    val keyWordArguments = mutableListOf<KeywordArgAndItsMessages>()
    var firstCycle = true

    do {
        val keywordPart = matchAssert(TokenType.Identifier, "Identifier expected inside keyword message send")
        matchAssert(TokenType.Colon, "Colon expected before argument, inside keyword message send") // skip colon
        // x from: ^3 inc to: 2 inc
        // x from: 3 ^inc to: 2 inc
        val unaryOrBinary = unaryOrBinary(inBrackets) // Тут может понядобится все таки передать ресивер
        val keyArg = unaryOrBinary.receiver


        // making fun name camelCase
        stringBuilder.append(
            if (firstCycle) keywordPart.lexeme
            else keywordPart.lexeme.capitalizeFirstLetter()
        )


        val isUnaryOrIsBinary = unaryOrBinary is MessageSendUnary || unaryOrBinary is MessageSendBinary
        val unaryOrBinaryMsgForArg =
            if (isUnaryOrIsBinary && unaryOrBinary.messages.isNotEmpty()) unaryOrBinary.messages
            else null


        val x = KeywordArgAndItsMessages(
            selectorName = keywordPart.lexeme,
            keywordArg = keyArg,
            unaryOrBinaryMsgsForArg = unaryOrBinaryMsgForArg
        )

        keyWordArguments.add(x)

        // if keyword was split to 2 lines
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
    unaryAndBinaryMessages.add(keywordMsg)
    return MessageSendKeyword(
        receiver,
        unaryAndBinaryMessages,
        inBrackets,
        null,
        receiver.token
    )
}
