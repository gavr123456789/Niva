package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.resolve
import frontend.resolver.resolveExpressionInBrackets
import main.utils.CYAN
import main.utils.RESET
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.parser.types.ast.ExpressionInBrackets
import main.frontend.resolver.findAnyMsgType

fun Resolver.resolveBinaryMsg(
    statement: BinaryMsg,
    previousAndCurrentScope: MutableMap<String, Type>,
    )
{
    val receiver = statement.receiver

    if (receiver.type == null) {
        resolve(listOf(receiver), previousAndCurrentScope, statement)
    }

    val receiverType = receiver.type
        ?: statement.token.compileError("Can't resolve return type of $CYAN${statement.selectorName}${RESET} binary msg")


    // resolve messages
    if (statement.unaryMsgsForArg.isNotEmpty()) {
        currentLevel++
        resolve(statement.unaryMsgsForArg, previousAndCurrentScope, statement)
        currentLevel--
    }

    val isUnaryForReceiver = statement.unaryMsgsForReceiver.isNotEmpty()
    if (isUnaryForReceiver) {
        currentLevel++
        resolve(statement.unaryMsgsForReceiver, previousAndCurrentScope, statement)
        currentLevel--
    }

    // 1 < (this at: 0)
    if (statement.argument is ExpressionInBrackets) {
        resolveExpressionInBrackets(statement.argument, previousAndCurrentScope)
    }

    // q = "sas" + 2 toString
    // find message for this type
    val messageReturnType =
        (if (isUnaryForReceiver)
            findAnyMsgType(
                statement.unaryMsgsForReceiver.last().type!!,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Binary
            )
        else
            findAnyMsgType(receiverType, statement.selectorName, statement.token, MessageDeclarationType.Binary)
                )

    statement.type = messageReturnType.returnType
    statement.pragmas = messageReturnType.pragmas

}
