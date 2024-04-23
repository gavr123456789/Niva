package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.MessageDeclarationBinary
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.resolver.findAnyMsgType


// мне нужно 2 сообщения
// одно которое текущее обрабатываемое
// и второе которое декларация
// если обрабатываемое содержит еррор то добавляем его и текущему


// returns type updated with errors
fun Resolver.addErrorEffect(msgFromDB: MessageMetadata, returnType: Type): Type {
    val currentMsgDecl = resolvingMessageDeclaration
    if (currentMsgDecl != null) {
        fun getMetadata(): MessageMetadata {
            val msgKind = when (currentMsgDecl) {
                is MessageDeclarationUnary -> MessageDeclarationType.Unary
                is MessageDeclarationBinary -> MessageDeclarationType.Binary
                is MessageDeclarationKeyword -> MessageDeclarationType.Keyword
                is ConstructorDeclaration -> TODO()
            }
            val msgType = findAnyMsgType(
                currentMsgDecl.forType!!,
                currentMsgDecl.name,
                currentMsgDecl.token,
                msgKind
            )

            return msgType
        }

        val metadataOfCurrentDeclaration = getMetadata()
        val errors2 = msgFromDB.errors

        if (errors2 != null) {
            metadataOfCurrentDeclaration.addErrors(errors2)
            val returnTypeWithErrors = returnType.addErrors(errors2)
            return returnTypeWithErrors
        }
    }

    // if current method is null then its top level function!
    return returnType
}
