package frontend.parser.types.ast

import frontend.meta.Token
import frontend.parser.parsing.Pragma
import frontend.resolver.Type

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
    var pragmas: MutableList<Pragma>,
) : ASTNode2(token) {
    override fun toString(): String {
        return token.lexeme
    }
}

sealed class Declaration(
    token: Token,
    isPrivate: Boolean,
    pragmas: MutableList<Pragma>,
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
    pragmas: MutableList<Pragma> = mutableListOf(),
    var isInlineRepl: Boolean = false,
    var inlineReplCounter: Int = 1,
    var isInfoRepl: Boolean = false
) : Statement(token, isPrivate, pragmas)

class ReturnStatement(
    var expression: Expression?,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Statement(token, isPrivate, pragmas)

class VarDeclaration(
    token: Token,
    val name: String,
    var value: Expression,
    var valueTypeAst: TypeAST? = null,
    val mutable: Boolean = false,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        val type = if (value.type != null) "::" + value.type.toString() else ""
        return "$name$type = $value"
    }
}

class Assign(
    token: Token,
    val name: String,
    val value: Expression,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "$name = ${value.str})"
    }
}




