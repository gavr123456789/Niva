package main.frontend.resolver.messageResolving

import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.MessageSend


// мне нужно 2 сообщения
// одно которое текущее обрабатываемое
// и второе которое декларация
// если обрабатываемое содержит еррор то добавляем его и текущему


// returns type updated with errors
fun Resolver.addErrorEffect(msgFromDB: MessageMetadata, returnType: Type, statement: Message): Type {
    val currentMsgDecl = resolvingMessageDeclaration
    if (currentMsgDecl != null) {
//        fun getMetadata(): MessageMetadata {
//            val msgKind = when (currentMsgDecl) {
//                is MessageDeclarationUnary -> MessageDeclarationType.Unary
//                is MessageDeclarationBinary -> MessageDeclarationType.Binary
//                is MessageDeclarationKeyword -> MessageDeclarationType.Keyword
//                is ConstructorDeclaration -> TODO()
//            }
//            val msgType = findAnyMsgType(
//                currentMsgDecl.forType!!,
//                currentMsgDecl.name,
//                currentMsgDecl.token,
//                msgKind
//            )
//
//            return msgType
//        }

        val errors2 = msgFromDB.errors

        if (errors2 != null) {
            val metadataOfCurrentDeclaration = currentMsgDecl.findMetadata(this)

            metadataOfCurrentDeclaration.addErrors(errors2)
            val pairOfSas = Pair(statement, errors2)
            currentMsgDecl.stackOfPossibleErrors.add(pairOfSas)

            val returnTypeWithErrors = returnType.addErrors(errors2)

            return returnTypeWithErrors
        }
//        else {
//            // check that declared return type dosnt contain errors
//            val returnAst = currentMsgDecl.returnTypeAST
//            if (returnAst != null) {
//                println()
//            }
//        }
    }

    // if current method is null then its top level function!
    return returnType
}
