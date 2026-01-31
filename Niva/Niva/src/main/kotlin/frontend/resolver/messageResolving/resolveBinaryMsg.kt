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
): Pair<Type, MessageMetadata?> {
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
    val msgFromDb =
        if (isUnaryForReceiver)
            findAnyMsgType(
                statement.unaryMsgsForReceiver.last().type!!,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Binary
            )
        else
            findAnyMsgType(receiverType, statement.selectorName, statement.token, MessageDeclarationType.Binary)

    statement.declaration = msgFromDb.declaration
    statement.msgMetaData = msgFromDb

    if (msgFromDb is BinaryMsgMetaData && !compare2Types(argumentType, msgFromDb.argType, argument.token)) {
        argument.token.compileError("($YEL$argumentType$RESET != $YEL${msgFromDb.argType}$RESET)\nBinary msg $WHITE$statement$RESET has type: $YEL$msgFromDb$RESET, but argument\n           $WHITE$argument$RESET has type $YEL$argumentType")
    }

    // Resolve generics in return type
    val receiverGenericsTable = mutableMapOf<String, Type>()
    if (receiverType is Type.UserLike && receiverType.typeArgumentList.isNotEmpty()) {
        receiverType.typeArgumentList.forEach {
            val beforeResolveName = it.beforeGenericResolvedName
            if (beforeResolveName != null) {
                receiverGenericsTable[beforeResolveName] = it
            }
        }
    }

    val letterToRealType = mutableMapOf<String, Type>()
    if (msgFromDb is BinaryMsgMetaData && msgFromDb.argType is Type.UnknownGenericType) {
        letterToRealType[msgFromDb.argType.name] = argumentType
    }

    val returnTypeFromDb = msgFromDb.returnType
    val returnType2 = resolveReturnTypeIfGeneric(returnTypeFromDb, letterToRealType, receiverGenericsTable)

    // if return type was unwrapped nullable, we need to wrap it again
    val returnType = if (returnTypeFromDb is Type.NullableType && returnType2 !is Type.NullableType) 
        Type.NullableType(realType = returnType2) 
    else returnType2

    statement.type = returnType
    statement.pragmas = msgFromDb.pragmas

//    addErrorEffect(msgFromDb, msgFromDb.returnType, statement)
    return Pair(returnType, msgFromDb)
}
