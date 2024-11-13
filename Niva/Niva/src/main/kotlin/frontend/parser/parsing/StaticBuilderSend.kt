package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.MessageSend
import main.frontend.parser.types.ast.MessageSendBinary
import main.frontend.parser.types.ast.MessageSendKeyword
import main.frontend.parser.types.ast.MessageSendUnary
import main.frontend.parser.types.ast.StaticBuilder

fun Parser.staticBuilderFromBuilder(msg: StaticBuilder): StaticBuilder {

    val name = matchAssert(TokenType.Identifier)

    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    val result = StaticBuilder(
        name = name.lexeme,
        statements = statements,
        defaultAction = defaultAction,
        args = emptyList(),
        type = null,
        receiverOfBuilder = msg,
        token = name,
        declaration = null
    )
    return result
}

fun Parser.staticBuilderFromUnary(msg: MessageSend): StaticBuilder {
    val (builderMsg, receiver) = when  (msg) {
        is MessageSendUnary -> {
            // 1 inc build [...]
            // at first its 2 unary, so we replace the last one(build) with builder(build[...])
            val builderMsg = msg.messages.last()
            val receiver = if (msg.messages.count() == 1)
                msg.receiver
            else
                msg.also { it.messages.removeLast() }
            Pair(builderMsg, receiver)
        }
        is MessageSendBinary -> {
            msg.token.compileError("builder after binary not supported yet")
        }
        is MessageSendKeyword -> {
            msg.token.compileError("builder after binary not supported yet")
        }
    }


    matchAssert(TokenType.OpenBracket)
    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    val result = StaticBuilder(
        name = builderMsg.selectorName,
        statements = statements,
        defaultAction = defaultAction,
        args = emptyList(),
        type = null,
        receiverOfBuilder = receiver,
        token = builderMsg.token,
        declaration = null
    )
    if (checkMany(TokenType.Identifier, TokenType.OpenBracket)) {
        return staticBuilderFromBuilder(result)
    }

    return result
}

fun Parser.staticBuilderFromUnaryWithArgs(msg: MessageSendUnary): StaticBuilder {

    val q = msg.messages.last()
    val receiver = if (msg.messages.count() == 1)
        msg.receiver
    else
        msg.also { it.messages.removeLast() }

    matchAssert(TokenType.OpenParen)

    val b = StringBuilder()
    val (args, _) = keywordSendArgs(b)

    matchAssert(TokenType.CloseParen)
    matchAssert(TokenType.OpenBracket)


    val (statements, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    // TODO can name can be from another package? probably
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
