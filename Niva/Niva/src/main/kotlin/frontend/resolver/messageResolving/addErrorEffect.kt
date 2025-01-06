package main.frontend.resolver.messageResolving

import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.PairOfErrorAndMessage
import main.utils.isGeneric


// мне нужно 2 сообщения
// одно которое текущее обрабатываемое
// и второе которое декларация
// если обрабатываемое содержит еррор то добавляем его и текущему


// returns type updated with errors
fun Resolver.addErrorEffect(msgFromDB: MessageMetadata, returnType: Type, statement: Message): Type {
    val currentMsgDecl = resolvingMessageDeclaration
    val errors = msgFromDB.errors
    // temp fix for ifTrue:ifFalse: that returns errors in each branch
    if (errors == null && returnType.errors != null && !msgFromDB.returnType.name.isGeneric()) {
        statement.token.compileError("Compiler bug: msgFromDB doesnt contain errors, but return type contain")
    }
    if (errors != null) {
        if (currentMsgDecl != null) {
            (currentMsgDecl.messageData ?: currentMsgDecl.findMetadata(this)).addErrors(errors)
        }

        val pairOfStAndErrorSet = PairOfErrorAndMessage(statement, errors)
        assert(errors.isNotEmpty())

        // maybe we are on the top level, so no decl to add errors
        currentMsgDecl?.stackOfPossibleErrors?.add(pairOfStAndErrorSet)
        val returnTypeWithErrors =
            if(statement.selectorName == "throwWithMessage" && errors == returnType.errors)
                returnType
            else
                returnType.copyAndAddErrors(errors)

        return returnTypeWithErrors
    }

    // if current method is null then its top level function!
    return returnType
}
