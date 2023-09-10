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

sealed class Declaration(
    token: Token,
    isPrivate: Boolean,
    pragmas: List<Pragma>,
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "Declaration(${token.lexeme})"
    }
}

//sealed class Metadata()

sealed class Expression(
    var type: Type? = null,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
    var isInlineRepl: Boolean = false,
    var inlineReplCounter: Int = 1,
) : Statement(token, isPrivate, pragmas)

class ReturnStatement(
    var expression: Expression,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
) : Statement(token, isPrivate, pragmas)

class VarDeclaration(
    token: Token,
    val name: String,
    val value: Expression,
    var valueType: TypeAST? = null,
    val mutable: Boolean = false,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "VarDeclaration(${name} = ${value.str}, valueType=${valueType?.name})"
    }
}

class Assign(
    token: Token,
    val name: String,
    val value: Expression,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "Assign(${name} = ${value.str})"
    }
}


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

