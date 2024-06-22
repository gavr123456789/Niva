package main.frontend.parser.types.ast

import frontend.resolver.KeywordArgAst
import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import main.frontend.meta.Token

sealed class Receiver(type: Type?, token: Token, var isPiped: Boolean = false, var isCascade: Boolean = false) : Expression(type, token)


// Pkg.Person.[x, y]
// Pkg.Person.[+]
// Pkg.Person.[sas]
sealed class MethodReference(
    val forType: TypeAST,
    val name: String,
    token: Token,
    type: Type? = null,
    var method: MessageMetadata? = null
) : Receiver(type, token) {
    class Unary(
        forType: TypeAST,
        name: String, // inc
        token: Token,
        type: Type? = null,
    ) : MethodReference(forType, name, token, type)
    class Binary(
        forType: TypeAST,
        name: String, // +
        token: Token,
        type: Type? = null,
    ) : MethodReference(forType, name, token, type)

    class Keyword(
        val keys: List<String>, // P.[from, to]
        forType: TypeAST,
        name: String, // fromTo
        token: Token,
        type: Type? = null,
    ) : MethodReference(forType, name, token, type)
}

// Message send is for pipe operations
// a to: 2 |> sas: 6 == (a to: 2) sas: 6
// 4 + 5 |> + 6 == useless
// x sas |> sus == useless
// a to: 2 |> + 2 == add 2 to result of to:
sealed class MessageSend(
    val receiver: Receiver,
    open val messages: List<Message>,
    type: Type?,
    token: Token,
) : Receiver(type, token) {
    override fun toString(): String {
        val receiver = receiver.toString()
        val msg = if (messages.count() == 1) {
            messages[0].toString()
        } else messages.joinToString(" ") { it.toString() }
        return "$receiver $msg"
    }
}

class MessageSendUnary(
    receiver: Receiver,
    override val messages: MutableList<Message>,
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token)

class MessageSendBinary(
    receiver: Receiver,
    override val messages: List<Message>, // can be unary after keyword
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token) {
    override fun toString(): String {
        return "$receiver " + messages.joinToString(" ")
    }
}

class MessageSendKeyword(
    receiver: Receiver,
    override val messages: List<Message>, // can be unary or binary after keyword
    type: Type? = null,
    token: Token
) : MessageSend(receiver, messages, type, token) {
    override fun toString(): String {
        return "$receiver " + messages.joinToString(" ")
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

//    val unaryMsgs: List<UnaryFirstMsg> = listOf(),
) : Message(receiver, selectorName, listOf(), type, token) {
    override fun toString(): String {
        val y = if (unaryMsgsForReceiver.isNotEmpty()) unaryMsgsForReceiver.joinToString(" ") + " " else ""
        val x = if (unaryMsgsForArg.isNotEmpty()) unaryMsgsForArg.joinToString(" ") + " " else ""
        return "$y$selectorName $argument$x"
    }
}


enum class KeywordLikeType {
    Keyword, // .sas(1, 2)
    Constructor, // Sas(x = 1, y = 2)
    CustomConstructor, // Sas(1, 2)
    Setter, // x y: 1 == x.y = 1
    SetterImmutableCopy, // x = p age: 1 == x = Person(name = p.name, age = 1)
    ForCodeBlock // [] whileDo: []
}

class KeywordMsg(
    receiver: Receiver,
    selectorName: String,
    type: Type?,
    token: Token,
    val args: List<KeywordArgAst>,
    path: List<String>,
    var kind: KeywordLikeType = KeywordLikeType.Keyword,
) : Message(receiver, selectorName, path, type, token) {
    override fun toString(): String {
        return args.joinToString(" ") { it.name + ": " + it.keywordArg }
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
) : Message(receiver, selectorName, identifier, type, token) {
    override fun toString(): String {
        return selectorName
    }
}


class DotReceiver(
    type: Type?,
    token: Token
) : Receiver(type, token) {
    override fun toString(): String {
        return "Dot"
    }
}


