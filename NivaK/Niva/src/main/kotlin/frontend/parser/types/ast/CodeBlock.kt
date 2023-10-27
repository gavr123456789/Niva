package frontend.parser.types.ast

import frontend.meta.Token
import frontend.parser.parsing.CodeAttribute
import frontend.typer.Type

// [it toString]
class CodeBlock(

    val inputList: List<IdentifierExpr>, // [a, b -> a + b]
    val statements: List<Statement>,
    // if CB is not assigned to variable, then it needs to prepended with ; because kt will think its argument for previous call
    var isSingle: Boolean = false,
    type: Type?,
    token: Token,
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
    isPrivate: Boolean = false,
) : Receiver(type, token)

class ExpressionInBrackets(
    val statements: List<Statement>,
    type: Type?,
    token: Token,
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
    isPrivate: Boolean = false,
) : Receiver(type, token)
