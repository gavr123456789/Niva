package frontend.parser.types.ast

import frontend.meta.Token
import frontend.parser.parsing.MessageDeclarationType

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: Type?, token: Token) : Expression(type, token)

// x sas + y sus
class MessageCall(
    val receiver: Receiver,
    val messages: List<Message>,
    val mainMessageType: MessageDeclarationType, // это нужно превратить в union тип
    val inBracket: Boolean,
    type: Type?,
    token: Token
) :
    Expression(type, token) {
    override fun toString(): String {
        return "${messages.map { it.toString() }}"
    }
}

// binaryMessage | unaryMessage | keywordMessage
sealed class Message(
    val receiver: Receiver,
    val selectorName: String,
    val type: Type?,
    val token: Token
)

class BinaryMsg(
    receiver: Receiver,
    val unaryMsgsForReceiver: List<UnaryMsg>,
    selectorName: String,
    type: Type?,
    token: Token,
    val argument: Receiver,
    val unaryMsgsForArg: List<UnaryMsg>,

//    val unaryMsgs: List<UnaryFirstMsg> = listOf(),
) : Message(receiver, selectorName, type, token)

data class KeywordArgAndItsMessages(
    val selectorName: String,
    val keywordArg: Receiver,
    // there can't be unary AND binary messages in one time, binary will contain unary
    val unaryOrBinaryMsgsForArg: List<Message>
) {
    override fun toString(): String {
        if (unaryOrBinaryMsgsForArg.isNotEmpty()) {
            val firstMsg = unaryOrBinaryMsgsForArg[0]
            if (firstMsg is BinaryMsg) {
                val receiver = firstMsg.receiver
                val unaryForReceiver =
                    if (firstMsg.unaryMsgsForReceiver.isNotEmpty())
                        firstMsg.unaryMsgsForReceiver.map { it.selectorName }.toString()
                    else ""
                val unaryForArg =
                    if (firstMsg.unaryMsgsForArg.isNotEmpty())
                        firstMsg.unaryMsgsForArg.map { it.selectorName }.toString()
                    else ""
                val binaryOperator = firstMsg.selectorName
                val arg = firstMsg.argument.str
                return "$receiver $unaryForReceiver $binaryOperator $arg $unaryForArg "

            } else {
                // unary
                return "${keywordArg.str} ${unaryOrBinaryMsgsForArg.map { it.selectorName }}"
            }

        }
        return "$selectorName: ${keywordArg.str}"
    }
}

class KeywordMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
    val args: List<KeywordArgAndItsMessages>
) : Message(receiver, selectorName, type, token) {
    override fun toString(): String {

        val receiverName = receiver
        return "KeywordCall($receiverName ${args.map { it.toString() }})"
    }
}

class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
) : Message(receiver, selectorName, type, token)
