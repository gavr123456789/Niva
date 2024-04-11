package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.*
import main.utils.CYAN
import main.utils.RESET
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.resolver.findAnyMsgType
import main.utils.WHITE
import main.utils.YEL

fun Resolver.resolveBinaryMsg(
    statement: BinaryMsg,
    previousAndCurrentScope: MutableMap<String, Type>,
) {
    val receiver = statement.receiver

    if (receiver.type == null) {
        resolveSingle((receiver), previousAndCurrentScope, statement)
    }

    val receiverType = receiver.type
        ?: statement.token.compileError("Can't resolve return type of $CYAN${statement.selectorName}${RESET} binary msg")


    // resolve messages
    val isUnaryForArg = statement.unaryMsgsForArg.isNotEmpty()
    if (isUnaryForArg) {
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
    val argument = statement.argument
    resolveSingle((argument), previousAndCurrentScope)
    val argumentType = if (isUnaryForArg) statement.unaryMsgsForArg.last().type else argument.type
    if (argumentType == null) {
        argument.token.compileError("Compiler bug: binary arg: $argument has no type resolved")
    }

    // q = "sas" + 2 toString
    // find message for this type
    val messageTypeFromDb =
        if (isUnaryForReceiver)
            findAnyMsgType(
                statement.unaryMsgsForReceiver.last().type!!,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Binary
            )
        else
            findAnyMsgType(receiverType, statement.selectorName, statement.token, MessageDeclarationType.Binary)


    if (messageTypeFromDb is BinaryMsgMetaData && !compare2Types(argumentType, messageTypeFromDb.argType)) {
//        println("aloxa")
        argument.token.compileError("($YEL$argumentType$RESET != $YEL${messageTypeFromDb.argType}$RESET)\nBinary msg $WHITE$statement$RESET has type: $YEL$messageTypeFromDb$RESET, but argument\n           $WHITE$argument$RESET has type $YEL$argumentType")
    }

    statement.type = messageTypeFromDb.returnType
    statement.pragmas = messageTypeFromDb.pragmas

}
