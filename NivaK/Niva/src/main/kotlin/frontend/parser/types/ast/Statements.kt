package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

// https://github.com/antlr/grammars-v4/blob/master/smalltalk/Smalltalk.g4
sealed class ASTNode2(
    val token: Token
) {
    val str: String
        get() = this.token.lexeme
}

sealed class Statement(
    token: Token,
    val isPrivate: Boolean,
    val pragmas: List<Pragma>,
) : ASTNode2(token) {
    override fun toString(): String {
        return "Declaration(${token.lexeme})"
    }
}

sealed class Metadata()

sealed class Expression(
    val type: Type? = null,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
    metadata: Metadata? = null
) : Statement(token, isPrivate, pragmas)


class VarDeclaration(
    token: Token,
    val name: String,
    val value: Expression,
    val valueType: TypeAST? = null,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "VarDeclaration(${name} = ${value.str}, valueType=${valueType?.name})"
    }
}


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

