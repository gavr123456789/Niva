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
sealed class Receiver(token: Token) : Expression(token)


// PRIMARY
// identifier | LiteralExpression
sealed class Primary(token: Token) : Receiver(token)

// LITERALS
sealed class LiteralExpression(literal: Token) : Primary(literal) {
    class IntExpr(literal: Token) : LiteralExpression(literal)
    class StringExpr(literal: Token) : LiteralExpression(literal)
    class FalseExpr(literal: Token) : LiteralExpression(literal)
    class TrueExpr(literal: Token) : LiteralExpression(literal)
    class FloatExpr(literal: Token) : LiteralExpression(literal)
}

class IdentifierExpr(
    token: Token,
    val name: Token,
    val depth: Int,
) : Primary(token)


// MESSAGES


// binaryMessage | unaryMessage | keywordMessage
sealed class Message(val receiver: Receiver, val selectorName: String, token: Token) :
    Expression(token)

class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    file: String,
    token: Token,
) : Message(receiver, selectorName, token)

class BinaryMsg(
    val unaryMsgs: List<UnaryMsg> = listOf(),
    receiver: Receiver,
    selectorName: String,
    file: String,
    token: Token,
) : Message(receiver, selectorName, token)

class KeywordMsg(
    val unaryMsgs: List<UnaryMsg> = listOf(),
    val binaryMsgs: List<BinaryMsg> = listOf(),
    receiver: Receiver,
    selectorName: String,
    file: String,
    token: Token,
) : Message(receiver, selectorName, token)


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

@JvmInline
value class LiteralExpr(val literal: Token)
