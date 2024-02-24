package main.frontend.resolver.messageResolving

import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.IdentifierExpr
import frontend.parser.types.ast.Receiver
import frontend.parser.types.ast.UnaryMsg
import frontend.parser.types.ast.UnaryMsgKind
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.UnaryMsgMetaData
import frontend.resolver.getTableOfLettersFrom_TypeArgumentListOfType
import frontend.resolver.resolve
import main.CYAN
import main.RESET
import main.WHITE
import main.YEL
import main.frontend.resolver.findAnyMsgType
import main.frontend.resolver.findStaticMessageType

fun Resolver.resolveUnaryMsg(
    statement: UnaryMsg,
    previousAndCurrentScope: MutableMap<String, Type>,
) {
    // if a type already has a field with the same name, then this is getter, not unary send
    val receiver = statement.receiver


    if (receiver.type == null) {
        currentLevel++
        resolve(listOf(receiver), previousAndCurrentScope, statement)
        currentLevel--
        receiver.type
            ?: statement.token.compileError("Can't resolve type of $CYAN${statement.selectorName}${RESET} unary msg: $YEL${receiver.str}")
    }

    fun checkForStatic(receiver: Receiver): Boolean =
        if (receiver.type is Type.UserEnumRootType) false
        else receiver is IdentifierExpr && typeTable[receiver.str] != null


    // if this is message for type
    val isStaticCall = checkForStatic(receiver)

//            val testDB = if (receiver is IdentifierExpr)
//                typeDB.getTypeOfIdentifierReceiver(
//                    receiver.name,
//                    receiver,
//                    getCurrentImports(receiver.token),
//                    currentPackageName,
//                    currentScope,
//                    previousScope,
//                    names = receiver.names
//                ) else null

    val receiverType = receiver.type!!

    val letterToTypeFromReceiver = if (receiverType is Type.UserLike)
        getTableOfLettersFrom_TypeArgumentListOfType(receiverType)
    else mutableMapOf()

    if (receiverType is Type.Lambda) {
        if (statement.selectorName != "do") {
            if (receiverType.args.isNotEmpty())
                statement.token.compileError("Codeblock $WHITE${statement.str}${RESET} takes more than 0 arguments, please use keyword message with it's args names")
            else
                statement.token.compileError("For codeblock $WHITE${statement.str}${RESET} you can use only unary 'do' message")
        }
        if (receiverType.args.isNotEmpty()) {
            statement.token.compileError("Codeblock $WHITE${statement.str}${RESET} takes more than 0 arguments, please use keyword message with it's args names")
        }

        statement.type = receiverType.returnType
        statement.kind = UnaryMsgKind.ForCodeBlock
        return
    }


    val checkForGetter = {
        if (receiverType is Type.UserLike) {
            val fieldWithSameName = receiverType.fields.find { it.name == statement.selectorName }
            Pair(fieldWithSameName != null, fieldWithSameName)
        } else Pair(false, null)
    }

    val (isGetter, field) = checkForGetter()
    if (isGetter) {
        statement.kind = UnaryMsgKind.Getter
        statement.type = field!!.type

    } else {
        // usual message or static message

        // check for recursion
        val resolvingMsgDecl = this.resolvingMessageDeclaration
        if (resolvingMsgDecl?.name == statement.selectorName && !resolvingMsgDecl.isRecursive) {
            resolvingMsgDecl.isRecursive = true
            if (resolvingMsgDecl.isSingleExpression && resolvingMsgDecl.returnTypeAST == null) {
                resolvingMsgDecl.token.compileError("Recursive single expression methods must describe its return type explicitly")
            }
        }

        val msgFromDb = if (!isStaticCall) {
            val msgType = findAnyMsgType(
                receiverType,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Unary
            ) as UnaryMsgMetaData
            statement.kind = if (msgType.isGetter) UnaryMsgKind.Getter else UnaryMsgKind.Unary
            msgType
        } else {
            val (messageReturnType, isGetter2) = findStaticMessageType(
                receiverType,
                statement.selectorName,
                statement.token,
                MessageDeclarationType.Unary
            )
            statement.kind = if (isGetter2) UnaryMsgKind.Getter else UnaryMsgKind.Unary
            messageReturnType
        }//.also { it.returnType.resetGenericParams() }
        val returnTypeFromDb = msgFromDb.returnType
        // add pragmas
        statement.pragmas = msgFromDb.pragmas

        // add receiver if T sas = [...]
        if (returnTypeFromDb is Type.UnknownGenericType && msgFromDb.forGeneric) {
            letterToTypeFromReceiver["T"] = receiverType
        }
        // resolve return type generic
        val typeForStatement =
            resolveReturnTypeIfGeneric(returnTypeFromDb, mutableMapOf(), letterToTypeFromReceiver)
        statement.type = typeForStatement
    }
}
