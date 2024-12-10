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
): Type {
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
    val receiverType = receiver.type!!

    if (receiverType is Type.Lambda) {
        if (statement.selectorName != "do") {
            // try to find such message for type, maybe its aliased
            if (receiverType.alias != null) {
                return resolveUnaryAliasLambda(statement, receiverType, receiver.token)
            }
            // try to find same signature in the lambdaTypes
            val w = typeDB.lambdaTypes.values.find { compare2Types(it, receiverType, statement.token) }
            if (w != null) {
                statement.type = w
                statement.kind = UnaryMsgKind.Unary
                return w
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
        return receiverType.returnType
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
        val result = field!!.type
        statement.type = result
        return result
    } else {
        // usual message or static message

        // check for recursion
        val resolvingMsgDecl = this.resolvingMessageDeclaration
        val msgForType = resolvingMsgDecl?.forType
        val unaryReceiverType = statement.receiver.type
        val sameTypes = if (msgForType != null && unaryReceiverType != null) {
            compare2Types(msgForType, unaryReceiverType, statement.token, unpackNull = true)
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

        //check that it was mutable call for mutable type
        if (msgFromDb.declaration != null &&
            msgFromDb.declaration.forTypeAst.mutable && receiver is IdentifierExpr) {
            val receiverFromScope = previousAndCurrentScope[receiver.name]
            if (receiverFromScope != null && !receiverFromScope.isMutable) {
                statement.token.compileError("receiver type $receiverType is not mutable, but ${msgFromDb.declaration} declared for mutable type, use `x::mut $receiverType = ...`")
            }
        }
        //
        val compareThatReceiverIsTheSameGeneric = {
            val forTypeDecl = msgFromDb.declaration?.forType
            if (forTypeDecl != null) {
                // find method declared for T?
                val x = compare2Types(forTypeDecl, receiverType, statement.token, unpackNull = true)
                if (!x) {
                    statement.token.compileError("Receiver is of type $receiverType, but message ${msgFromDb.name} declared for type ${msgFromDb.declaration.forType}")
                }
            }
        }
        compareThatReceiverIsTheSameGeneric()
        statement.declaration = msgFromDb.declaration
        statement.msgMetaData = msgFromDb

        val returnTypeFromDb = msgFromDb.returnType
        // add pragmas
        statement.pragmas = msgFromDb.pragmas

        val letterToTypeFromReceiver: MutableMap<String, Type> = if (receiverType is Type.UserLike && returnTypeFromDb is Type.UserLike) {
            val result2 = mutableMapOf<String, Type>()
//            getTableOfLettersFrom_TypeArgumentListOfType(receiverType, returnTypeFromDb, result)
            val unitializedType = (receiverType.replaceInitializedGenericToUnInitialized(this, statement.token)) as? Type.UserLike ?: statement.token.compileError("Bug, generics can be only inside userLike types")
            getTableOfLettersFromType(receiverType, unitializedType, result2)
            result2
        }
            else
                mutableMapOf()
        // add receiver if its for any generic type like "T sas = [...]"
        if (returnTypeFromDb is Type.UnknownGenericType && msgFromDb.forGeneric) {
            letterToTypeFromReceiver["T"] = receiverType
        }

        val typeForStatement = resolveReturnTypeIfGeneric(returnTypeFromDb, mutableMapOf(), letterToTypeFromReceiver)
//         = typeForStatement

        val result = addErrorEffect(msgFromDb, typeForStatement, statement)
        statement.type = result
        return result
    }


}
