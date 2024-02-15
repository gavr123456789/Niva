package frontend.parser.types.ast

import frontend.meta.Token
import frontend.resolver.Type

// [it toString]
class CodeBlock(
    val inputList: List<IdentifierExpr>, // [a, b -> a + b]
    val statements: List<Statement>,
    // if CB is not assigned to variable, then it needs to prepended with ; because kt will think its argument for previous call
    var isSingle: Boolean = false,
    type: Type? = null,
    token: Token,
) : Receiver(type, token)

class ExpressionInBrackets(
    val statements: List<Statement>,
    type: Type?,
    token: Token,
) : Receiver(type, token) {
    override fun toString(): String {
        return if (statements.isNotEmpty())
            "(${statements.first()})"
        else
            "()"
    }
}



