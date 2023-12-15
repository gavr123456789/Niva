package frontend.parser.types.ast

import frontend.meta.Token
import frontend.resolver.Type

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: Type?, token: Token, var isPiped: Boolean = false) : Expression(type, token)


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
        val msg = if (messages.count() == 1) {
            messages[0].toString()
        } else messages.joinToString(" ") { it.toString() }
        return "${receiver.token.lexeme} $msg"
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
) : MessageSend(receiver, messages, type, token) {
    override fun toString(): String {
        return messages.joinToString(" ") { it.toString() }
    }
}


// binaryMessage | unaryMessage | keywordMessage
sealed class Message(
    var receiver: Receiver,
    var selectorName: String,
    val path: List<String>,

    type: Type?,
    token: Token,
//    var isPiped: Boolean = false
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
) : Message(receiver, selectorName, path, type, token) {
    override fun toString(): String {
        return "$receiver $selectorName $argument"
    }
}

data class KeywordArgAndItsMessages(
    val name: String,
    val keywordArg: Expression
)


enum class KeywordLikeType {
    Keyword, // .sas(1, 2)
    Constructor, // Sas(x = 1, y = 2)
    CustomConstructor, // Sas(1, 2)
    Setter, // x y: 1 == x.y = 1
    ForCodeBlock // [] whileDo: []
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
        return "KeywordMsg(${receiver} ${args.joinToString(" ") { it.name + ": " + it.keywordArg.str }})"
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


class DotReceiver(
    type: Type?,
    token: Token
) : Receiver(type, token) {
    override fun toString(): String {
        return "Dot"
    }
}


