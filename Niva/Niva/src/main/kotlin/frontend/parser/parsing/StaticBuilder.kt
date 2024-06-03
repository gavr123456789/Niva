package main.frontend.parser.parsing

import frontend.parser.parsing.Parser
import frontend.parser.parsing.dotSeparatedIdentifiers
import frontend.parser.parsing.matchAssert
import frontend.parser.parsing.statementsUntilCloseBracketWithDefaultAction
import main.frontend.meta.TokenType
import main.frontend.parser.types.ast.StaticBuilder

fun Parser.staticBuilder(): StaticBuilder {
    val q = dotSeparatedIdentifiers()!!
    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    // TODO can name can be from another package
    val result = StaticBuilder(
        name = q.name,
        statements = statements,
        defaultAction = defaultAction,
        type = null,
        token = q.token
    )

    return result
}
