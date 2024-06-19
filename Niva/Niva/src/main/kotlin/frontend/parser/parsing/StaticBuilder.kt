package main.frontend.parser.parsing

import frontend.parser.parsing.*
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
        args = listOf(),
        type = null,
        token = q.token
    )

    return result
}
fun Parser.staticBuilderWithArgs(): StaticBuilder {
    val q = dotSeparatedIdentifiers()!!
    matchAssert(TokenType.OpenParen)

    val b = StringBuilder()
    val (args, _) = keywordSendArgs(b)

    matchAssert(TokenType.CloseParen)
    matchAssert(TokenType.OpenBracket)


    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    // TODO can name can be from another package
    val result = StaticBuilder(
        name = q.name,
        statements = statements,
        defaultAction = defaultAction,
        args = args,
        type = null,
        token = q.token
    )

    return result
}
