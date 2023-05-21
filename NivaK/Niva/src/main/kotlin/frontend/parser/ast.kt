package frontend.parser

import frontend.meta.Token

// https://github.com/antlr/grammars-v4/blob/master/smalltalk/Smalltalk.g4
sealed class ASTNode2(
    val token: Token
) {
    val str: String
        get() = this.token.lexeme
}

open class Declaration(
    token: Token,
    val isPrivate: Boolean,
    val pragmas: List<Pragma>,
) : ASTNode2(token) {
    override fun toString(): String {
        return "Declaration(${token.lexeme})"
    }
}

sealed class Statement(
    file: String,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(token, isPrivate, pragmas)

// assignment | cascade | keywordSend | binarySend | primitive
sealed class Expression(
    val type: String?,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(token, isPrivate, pragmas)

class VarDeclaration(
    token: Token,
    val name: IdentifierExpr,
    val value: Expression,
    val valueType: String? = null,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "VarDeclaration(${name.token.lexeme} = ${value.str}, valueType=$valueType)"
    }
}


// receiver

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: String?, token: Token) : Expression(type, token)


// PRIMARY
// identifier | LiteralExpression
sealed class Primary(type: String?, token: Token) : Receiver(type, token)

// LITERALS
sealed class LiteralExpression(type: String?, literal: Token) : Primary(type, literal) {
    class IntExpr(literal: Token) : LiteralExpression("int", literal)
    class StringExpr(literal: Token) : LiteralExpression("string", literal)
    class FalseExpr(literal: Token) : LiteralExpression("bool", literal)
    class TrueExpr(literal: Token) : LiteralExpression("bool", literal)
    class FloatExpr(literal: Token) : LiteralExpression("float", literal)
}

class IdentifierExpr(
    name: String,
    type: String?,
    token: Token,
//    val depth: Int,
) : Primary(type, token)


// MESSAGES

// x sas + y sus
class MessageCall(val receiver: Receiver, val messages: List<Message>, type: String?, token: Token) :
    Expression(type, token) {
    override fun toString(): String {
        return "${messages.map { it.toString() }}"
    }
}

// binaryMessage | unaryMessage | keywordMessage
sealed class Message(val receiver: Receiver, val selectorName: String, type: String?, token: Token) :
    Expression(type, token)

class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    type: String?,
    token: Token,
) : Message(receiver, selectorName, type, token)

class BinaryMsg(
    receiver: Receiver,
    val unaryMsgsForReceiver: List<UnaryMsg>,
    selectorName: String,
    type: String?,
    token: Token,
    val argument: Expression,
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
//                val u = unaryOrBinaryMessagesForArg.filterIsInstance<UnaryMsg>()
                val receiver = firstMsg.receiver.str
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
    type: String?,
    token: Token,
    val args: List<KeywordArgAndItsMessages>
) : Message(receiver, selectorName, type, token) {
    override fun toString(): String {

        val receiverName = receiver.str

        return "KeywordCall($receiverName ${args.map { it.toString() }})"
    }
}


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

@JvmInline
value class LiteralExpr(val literal: Token)
