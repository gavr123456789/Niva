package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: Type?, token: Token, var inBracket: Boolean = false) : Expression(type, token)


// Message send is for pipe operations
// a to: 2 |> sas: 6 == (a to: 2) sas: 6
// 4 + 5 |> + 6 == useless
// x sas |> sus == useless
// a to: 2 |> + 2 == add 2 to result of to:
sealed class MessageSend(
    val receiver: Receiver,
    open val messages: List<Message>,
    type: Type?,
    token: Token
) : Receiver(type, token) {
    override fun toString(): String {
        return "${receiver.token.lexeme} ${messages.map { it.toString() }}"
    }
}

class MessageSendUnary(
    receiver: Receiver,
    override val messages: List<Message>,
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token)

class MessageSendBinary(
    receiver: Receiver,
    override val messages: List<Message>, // can be unary after keyword
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token)

class MessageSendKeyword(
    receiver: Receiver,
    override val messages: List<Message>, // can be unary or binary after keyword
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token)


// binaryMessage | unaryMessage | keywordMessage
sealed class Message(
    var receiver: Receiver,
    val selectorName: String,
    val path: List<String>,
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
    path: List<String>,

//    val unaryMsgs: List<UnaryFirstMsg> = listOf(),
) : Message(receiver, selectorName, path, type, token)

data class KeywordArgAndItsMessages(
    val selectorName: String,
    val keywordArg: Receiver,
    // there can't be unary AND binary messages in one time, binary will contain unary
    // but there can be many unary messages for one arg duuud
    val unaryOrBinaryMsgsForArg: List<Message>?
)

enum class KeywordLikeType {
    Keyword, Constructor, Setter, ForCodeBlock
}

class KeywordMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
    val args: List<KeywordArgAndItsMessages>,
    path: List<String>,
    var kind: KeywordLikeType = KeywordLikeType.Keyword,
) : Message(receiver, selectorName, path, type, token) {
    override fun toString(): String {
        val receiverName = receiver
        return "KeywordCall($receiverName ${args.map { it.toString() }})"
    }
}

enum class UnaryMsgKind {
    Unary, Getter, ForCodeBlock
}

class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    identifier: List<String>,
    type: Type?,
    token: Token,
    var kind: UnaryMsgKind = UnaryMsgKind.Unary
) : Message(receiver, selectorName, identifier, type, token)


