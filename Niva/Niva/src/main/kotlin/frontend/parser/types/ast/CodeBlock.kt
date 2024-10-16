package main.frontend.parser.types.ast

import frontend.resolver.Type
import main.frontend.meta.Token


// [it toString]
class CodeBlock(
    val inputList: List<IdentifierExpr>, // [a, b -> a + b]
    val statements: List<Statement>,
    // if CB is not assigned to variable, then it needs to prepended with ; because kt will think its argument for previous call
    var isSingle: Boolean = false,
    type: Type? = null,
    token: Token,
    var isStatement: Boolean = false // means it's not lambda, just block like for if
) : Receiver(type, token) {
    override fun toString(): String {
        return if (statements.isNotEmpty())
            "[\n\t${statements.joinToString("\n\t")}\n]"
        else
            "[]"
    }
}

class ExpressionInBrackets(
    val expr: Expression,
    type: Type?,
    token: Token,
) : Receiver(type, token) {
    override fun toString(): String =
        expr.toString()

}



