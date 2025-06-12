package main.frontend.resolver.messageResolving

import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.PairOfErrorAndMessage
import main.utils.isGeneric


// мне нужно 2 сообщения
// одно которое текущее обрабатываемое
// и второе которое декларация
// если обрабатываемое содержит еррор то добавляем его и текущему


// returns type updated with errors
fun Resolver.addErrorEffect(msgFromDB: MessageMetadata?, returnType: Type, statement: Message): Type {
    val errors = if (msgFromDB == null) returnType.errors else msgFromDB.errors// msgFromDB?.errors

    // fast exit
    if ((errors == null || errors.isEmpty()) && returnType.errors == null) {
        return returnType
    }

    // if its a codeblock then add errors to it and exit, do not add error effects to currentMsgDecl
    val codeBlock = stack.firstOrNull()
    if (codeBlock != null && codeBlock is CodeBlock) {
        if (errors?.isNotEmpty() == true)
            codeBlock.errors += errors
        return returnType
    }

    val currentMsgDecl = resolvingMessageDeclaration
    // temp fix for ifTrue:ifFalse: that returns errors in each branch
    if (errors == null && returnType.errors != null && (msgFromDB?.returnType?.name?.isGeneric() == false)) {
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

        if (statement.selectorName == "lex") {
            1
        }
        return returnTypeWithErrors
    }

    // if current method is null then its top level function!
    return returnType
}
