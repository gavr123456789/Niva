package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.TokenType
import main.frontend.parser.types.ast.MessageSendUnary
import main.frontend.parser.types.ast.StaticBuilder

fun Parser.staticBuilderFromUnary(msg: MessageSendUnary): StaticBuilder {
    // забрать последнее сообщение из msg
    // распарсить боди
    // добавить новое последнее сообщение которое теперь уже является билдером

    val q = msg.messages.last()
    val receiver = if (msg.messages.count() == 1)
        msg.receiver
    else
        msg.also { it.messages.removeLast() }


    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    val result = StaticBuilder(
        name = q.selectorName,
        statements = statements,
        defaultAction = defaultAction,
        args = emptyList(),
        type = null,
        receiverOfBuilder = receiver,
        token = q.token,
        declaration = null
    )

    return result
}

fun Parser.staticBuilderFromUnaryWithArgs(msg: MessageSendUnary): StaticBuilder {

    val q = msg.messages.last()
    val receiver = if (msg.messages.count() == 1)
        msg.receiver
    else
        msg.also { it.messages.removeLast() }
    ///
    matchAssert(TokenType.OpenParen)

    val b = StringBuilder()
    val (args, _) = keywordSendArgs(b)

    matchAssert(TokenType.CloseParen)
    matchAssert(TokenType.OpenBracket)


    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    // TODO can name can be from another package
    val result = StaticBuilder(
        name = q.selectorName,
        statements = statements,
        defaultAction = defaultAction,
        args = args,
        type = null,
        receiverOfBuilder = receiver,
        token = q.token,
        declaration = null

    )

    return result
}

fun Parser.staticBuilder(): StaticBuilder {
    val q = dotSeparatedIdentifiers()!!
    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    // TODO can name can be from another package
    val result = StaticBuilder(
        name = q.name,
        statements = statements,
        defaultAction = defaultAction,
        args = emptyList(),
        type = null,
        receiverOfBuilder = null,
        token = q.token,
        declaration = null

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
        receiverOfBuilder = null,
        token = q.token,
        declaration = null

    )

    return result
}
