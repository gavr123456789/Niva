package main.frontend.parser.types.ast

import frontend.parser.types.ast.Pragma
import frontend.resolver.Type
import main.frontend.meta.Token

// absolutely not helping https://github.com/antlr/grammars-v4/blob/master/smalltalk/Smalltalk.g4
sealed class ASTNode2(
    val token: Token
) {
    val str: String
        get() = this.token.lexeme
}

sealed class Statement(
    token: Token,
    var pragmas: MutableList<Pragma>,
    var docComment: DocComment? = null
) : ASTNode2(token) {
    override fun toString(): String {
        return token.lexeme
    }
}

sealed class Declaration(
    token: Token,
    pragmas: MutableList<Pragma>,
) : Statement(token, pragmas) {
    override fun toString(): String {
        return "Declaration(${token.lexeme})"
    }
}

sealed class Expression(
    var type: Type? = null,

    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
    var isInlineRepl: Boolean = false,
    var inlineReplCounter: Int = 1,
    var isInfoRepl: Boolean = false,
) : Statement(token, pragmas)


class NeedInfo(
    var expression: Expression?,
    token: Token,
//    num: Int = 1, // selected suggestion
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Statement(token, pragmas)

class ReturnStatement(
    var expression: Expression?,
    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Statement(token, pragmas)

class VarDeclaration(
    token: Token,
    val name: String,
    var value: Expression,
    var valueTypeAst: TypeAST? = null,
    val mutable: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    var declaredType: Type? = null,
) : Statement(token, pragmas) {
    override fun toString(): String {
        val type = if (value.type != null) "::" + value.type.toString() else ""
        return "$name$type = $value"
    }
}

class DestructingAssign(
    token: Token,
    val names: List<IdentifierExpr>,
    val value: Expression,
    pragmas: MutableList<Pragma> = mutableListOf()
) : Statement(token, pragmas) {
    override fun toString(): String {
        return "{" + "${names.joinToString(", ") + "}"} <- $value"
    }
}


// mutate
class Assign(
    token: Token,
    val name: String,
    val value: Expression,
    pragmas: MutableList<Pragma> = mutableListOf()
) : Statement(token, pragmas) {
    override fun toString(): String {
        return "$name <- $value"
    }
}





