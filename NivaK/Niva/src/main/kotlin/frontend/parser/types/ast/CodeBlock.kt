package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

// [it toString]
class CodeBlock(

    val inputList: List<IdentifierExpr>, // [a, b -> a + b]
    val statements: List<Statement>,

    type: Type?,
    token: Token,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Receiver(type, token)
