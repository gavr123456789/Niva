package frontend.parser

import frontend.meta.Token

// https://github.com/antlr/grammars-v4/blob/master/smalltalk/Smalltalk.g4
sealed class ASTNode2(
    val file: String,
    val token: Token
) {
    val str: String
        get() = this.token.lexeme
}

open class Declaration(
    file: String,
    token: Token,
    val isPrivate: Boolean,
    val pragmas: List<Pragma>,
) : ASTNode2(file, token)

sealed class Statement(
    file: String,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(file, token, isPrivate, pragmas)

// assignment | cascade | keywordSend | binarySend | primitive
sealed class Expression(
    file: String,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(file, token, isPrivate, pragmas)

class VarDeclaration(
    file: String,
    token: Token,
    val name: IdentifierExpr,
    val value: Expression,
    val valueType: Expression? = null,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(file, token, isPrivate, pragmas)

// LITERALS
sealed class LiteralExpression(file: String, literal: Token) : Expression(file, literal) {
    class IntExpr(file: String, literal: Token) : LiteralExpression(file, literal)
    class StringExpr(file: String, literal: Token) : LiteralExpression(file, literal)
    class FalseExpr(file: String, literal: Token) : LiteralExpression(file, literal)
    class TrueExpr(file: String, literal: Token) : LiteralExpression(file, literal)
    class FloatExpr(file: String, literal: Token) : LiteralExpression(file, literal)
}


// MESSAGES


// binaryMessage | unaryMessage | keywordMessage
sealed class Message(file: String, token: Token) : Expression(file, token)
class Unary

class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

class IdentifierExpr(
    file: String,
    token: Token,
    val name: Token,
    val depth: Int,
) : Expression(file, token)

@JvmInline
value class LiteralExpr(val literal: Token)
