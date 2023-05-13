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
    type: String?,
    token: Token,
    val depth: Int,
) : Primary(type, token)


// MESSAGES

// x sas + y sus
sealed class MessageCall(val receiver: Receiver, messages: List<Message>, type: String?, token: Token) :
    Expression(type, token)

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
    val unaryMsgs: List<UnaryMsg> = listOf(),
    receiver: Receiver,
    selectorName: String,
    type: String?,
    token: Token,
) : Message(receiver, selectorName, type, token)

class KeywordMsg(
    val unaryMsgs: List<UnaryMsg> = listOf(),
    val binaryMsgs: List<BinaryMsg> = listOf(),
    receiver: Receiver,
    selectorName: String,
    type: String?,
    token: Token,
) : Message(receiver, selectorName, type, token)


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

@JvmInline
value class LiteralExpr(val literal: Token)
