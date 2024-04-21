package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.*

import main.utils.CYAN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.Receiver
import main.frontend.parser.types.ast.UnaryMsg
import main.frontend.parser.types.ast.UnaryMsgKind
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
        resolveSingle((receiver), previousAndCurrentScope, statement)
        currentLevel--
        receiver.type
            ?: statement.token.compileError("Can't resolve type of $CYAN${statement.selectorName}${RESET} unary msg: $YEL${receiver.str}")
    }

    fun checkForStatic(receiver: Receiver): Boolean =
        if (receiver.type is Type.EnumRootType) false
        else receiver is IdentifierExpr && typeTable[receiver.name] != null


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
            // try to find such message for type, maybe its aliased
            if (receiverType.alias != null) {
                val type = typeDB.lambdaTypes[receiverType.alias] ?: receiver.token.compileError("Can't find type alias for lambda ${receiverType.alias}::$receiverType")
                statement.type = type
                statement.kind = UnaryMsgKind.Unary
                return
            }
            // try to find same signature in the lambdaTypes
            val w = typeDB.lambdaTypes.values.find { compare2Types(it, receiverType) }
            if (w != null) {
                statement.type = w
                statement.kind = UnaryMsgKind.Unary
                return
            }

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
        val msgForType = resolvingMsgDecl?.forType
        val statementType = statement.receiver.type
        val sameTypes = if (msgForType != null && statementType !=null) {
            compare2Types(statementType, msgForType, unpackNull = true)
        } else false


        if (resolvingMsgDecl?.name == statement.selectorName && sameTypes && !resolvingMsgDecl.isRecursive) {
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
        }
        val returnTypeFromDb = msgFromDb.returnType
        // add pragmas
        statement.pragmas = msgFromDb.pragmas

        // add receiver if T sas = [...]
        if (returnTypeFromDb is Type.UnknownGenericType && msgFromDb.forGeneric) {
            letterToTypeFromReceiver["T"] = receiverType
        }

        val typeForStatement =
            resolveReturnTypeIfGeneric(returnTypeFromDb, mutableMapOf(), letterToTypeFromReceiver)
        statement.type = typeForStatement

        if (returnTypeFromDb is Type.Union && returnTypeFromDb.isError) {
            TODO()
        }
    }
}
