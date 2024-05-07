package main.frontend.resolver.messageResolving

import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.parser.types.ast.Message


// мне нужно 2 сообщения
// одно которое текущее обрабатываемое
// и второе которое декларация
// если обрабатываемое содержит еррор то добавляем его и текущему


// returns type updated with errors
fun Resolver.addErrorEffect(msgFromDB: MessageMetadata, returnType: Type, statement: Message): Type {
    val currentMsgDecl = resolvingMessageDeclaration
    val errors2 = msgFromDB.errors
    if (errors2 != null && currentMsgDecl != null) {
        val metadataOfCurrentDeclaration = currentMsgDecl.findMetadata(this)
        metadataOfCurrentDeclaration.addErrors(errors2)

        val pairOfSas = Pair(statement, errors2)

        currentMsgDecl.stackOfPossibleErrors.add(pairOfSas)
        val returnTypeWithErrors = returnType.addErrors(errors2)

        return returnTypeWithErrors
    }

    // if current method is null then its top level function!
    return returnType
}
