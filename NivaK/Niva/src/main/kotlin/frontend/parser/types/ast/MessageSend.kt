package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: Type?, token: Token) : Expression(type, token)


// Message send is for pipe operations
// a to: 2 |> sas: 6 == (a to: 2) sas: 6
// 4 + 5 |> + 6 == useless
// x sas |> sus == useless
// a to: 2 |> + 2 == add 2 to result of to:
sealed class MessageSend(
    val receiver: Receiver,
    open val messages: List<Message>,
    val inBracket: Boolean,
    type: Type?,
    token: Token
) : Expression(type, token) {
    override fun toString(): String {
        return "${messages.map { it.toString() }}"
    }
}

class MessageSendUnary(
    receiver: Receiver,
    override val messages: List<UnaryMsg>,
    inBracket: Boolean,
    type: Type?,
    token: Token
) : MessageSend(receiver, messages, inBracket, type, token)

class MessageSendBinary(
    receiver: Receiver,
    override val messages: List<BinaryMsg>,
    inBracket: Boolean,
    type: Type?,
    token: Token
) : MessageSend(receiver, messages, inBracket, type, token)

class MessageSendKeyword(
    receiver: Receiver,
    override val messages: List<KeywordMsg>,
    inBracket: Boolean,
    type: Type?,
    token: Token
) : MessageSend(receiver, messages, inBracket, type, token)


// binaryMessage | unaryMessage | keywordMessage
sealed class Message(
    var receiver: Receiver,
    val selectorName: String,
    type: Type?,
    token: Token
) : Receiver(type, token) // any message can be receiver for other message(kw through |>)

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
    // but there can be many unary messages for one arg duuud
    val unaryOrBinaryMsgsForArg: List<Message>?
)

//sealed class KeywordLike(
//    receiver: Receiver,
//    selectorName: String,
//    type: Type?,
//    token: Token,
//    val args: List<KeywordArgAndItsMessages>
//) : Message(receiver, selectorName, type, token) {
//    override fun toString(): String {
//        val receiverName = receiver
//        return "KeywordCall($receiverName ${args.map { it.toString() }})"
//    }
//}

enum class KeywordLikeType {
    Keyword, Constructor, Setter
}

class KeywordMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
    val args: List<KeywordArgAndItsMessages>,
    var kind: KeywordLikeType = KeywordLikeType.Keyword
) : Message(receiver, selectorName, type, token) {
    override fun toString(): String {
        val receiverName = receiver
        return "KeywordCall($receiverName ${args.map { it.toString() }})"
    }
}

//class SetterMsg(
//    receiver: Receiver,
//    selectorName: String,
//    type: Type?,
//    token: Token,
//    args: List<KeywordArgAndItsMessages>
//) : KeywordLike(receiver, selectorName, type, token, args)
//
//class ConstructorMsg(
//    receiver: Receiver,
//    selectorName: String,
//    type: Type?,
//    token: Token,
//    args: List<KeywordArgAndItsMessages>
//) : KeywordLike(receiver, selectorName, type, token, args)


//sealed class UnaryLike(
//    receiver: Receiver,
//    selectorName: String,
//    type: Type?,
//    token: Token,
//) : Message(receiver, selectorName, type, token)

enum class UnaryMsgKind {
    Unary, Getter
}
class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
    var kind: UnaryMsgKind = UnaryMsgKind.Unary
) : Message(receiver, selectorName, type, token)

//class GetterMsg(
//    receiver: Receiver,
//    selectorName: String,
//    type: Type?,
//    token: Token,
//) : UnaryLike(receiver, selectorName, type, token)



