package main.frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.parsing.*
import frontend.parser.types.ast.StaticBuilder

fun Parser.staticBuilder(): StaticBuilder {
    val q = dotSeparatedIdentifiers()!!
    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    val result = StaticBuilder(
        statements = statements,
        defaultAction = defaultAction,
        type = null,
        token = q.token
    )

    return result
}
