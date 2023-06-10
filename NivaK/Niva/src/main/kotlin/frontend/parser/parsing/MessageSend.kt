package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.*
import frontend.util.capitalizeFirstLetter

//   // MESSAGES
//  messages = (unaryMessage+ binaryMessage* keywordMessage?)  -- unaryFirst
//  		 | (binaryMessage+ keywordMessage?) -- binaryFirst
//           | (keywordMessage) -- keywordFirst
fun Parser.messageCall(): MessageCall {
    // x echo // identifier
    // 1 echo // primary
    // (1 + 1) echo // parens
    // [1 2 3] // data structure

    // 3 + (1 / 2)

    // 3 + 8 to: 7


    // first check what first

    // if unary then run parse unary first parser
    // if unary then run parse binaryFirst parser
    // if keyword then run parse keyword parser


    if (match(TokenType.OpenParen)) {
        val receiver: Receiver = receiver()
        val result = anyMessageCall(receiver, true)
        matchAssert(TokenType.CloseParen, "Close paren expected")
        return result
    }


    val receiver: Receiver = receiver()
    return anyMessageCall(receiver, false)
}

fun Parser.getAllUnary(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()
    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val tok = step()
        val unaryFirstMsg = UnaryMsg(receiver, tok.lexeme, null, tok)
        unaryMessages.add(unaryFirstMsg)
    }

    return unaryMessages
}

fun Parser.unaryOrBinary(receiver: Receiver): Pair<MutableList<out Message>, MessageDeclarationType> {
    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessagesForReceiver = getAllUnary(receiver) // inc inc
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = step()
        val binaryArgument = receiver() // 2
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
        return Pair(unaryMessagesForReceiver, MessageDeclarationType.Unary)
    }
    // its binary msg
    return Pair(binaryMessages, MessageDeclarationType.Binary)
}

fun Parser.anyMessageCall(receiver: Receiver, inBrackets: Boolean): MessageCall {

    // keyword
    if (check(TokenType.Identifier) && check(TokenType.Colon, 1)) {
        return keyword(receiver, inBrackets)
    }
    // unary/binary
    val unaryAndBinaryMessagePair = unaryOrBinary(receiver)
    val unaryAndBinaryMessage = unaryAndBinaryMessagePair.first
    val type = unaryAndBinaryMessagePair.second
    return MessageCall(receiver, unaryAndBinaryMessage, type, inBrackets, null, receiver.token)
}

private fun Parser.keyword(
    receiver: Receiver,
    inBrackets: Boolean
): MessageCall {
    val stringBuilder = StringBuilder()
    val unaryAndBinaryMessages = mutableListOf<Message>()
    val keyWordArguments = mutableListOf<KeywordArgAndItsMessages>()
    var firstCycle = true

    do {
        val keywordPart = step()
        step()// skip colon
        // x from: ^3 inc to: 2 inc
        val keyArg = receiver()
        // x from: 3 ^inc to: 2 inc
        val unaryOrBinaryPair = unaryOrBinary(receiver)
        val unaryOrBinary = unaryOrBinaryPair.first
        // making fun name camelCase
        stringBuilder.append(
            if (firstCycle) keywordPart.lexeme
            else keywordPart.lexeme.capitalizeFirstLetter()
        )

        val x = KeywordArgAndItsMessages(
            selectorName = keywordPart.lexeme,
            keywordArg = keyArg,
            unaryOrBinaryMsgsForArg = unaryOrBinary
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
    return MessageCall(
        receiver,
        unaryAndBinaryMessages,
        MessageDeclarationType.Keyword,
        inBrackets,
        null,
        receiver.token
    )
}
