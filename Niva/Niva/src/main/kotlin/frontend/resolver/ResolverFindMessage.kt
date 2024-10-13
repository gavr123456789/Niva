package main.frontend.resolver

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.*
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.MessageSend
import main.utils.CYAN
import main.utils.GlobalVariables
import main.utils.PURP
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.onCompletionExc
import main.utils.findSimilar

fun lens(p: Protocol, selectorName: String, kind: MessageDeclarationType): MessageMetadata? {
    return when (kind) {
        MessageDeclarationType.Unary -> p.unaryMsgs[selectorName]
        MessageDeclarationType.Binary -> p.binaryMsgs[selectorName]
        MessageDeclarationType.Keyword -> p.keywordMsgs[selectorName]
    }
}

fun findAnyMethod(
    receiverType: Type,
    selectorName: String,
    pkg: Package,
    kind: MessageDeclarationType
): MessageMetadata? {
    receiverType.protocols.forEach { (_, v) ->
        val msgData = lens(v, selectorName, kind) ?: v.staticMsgs[selectorName]

        if (msgData != null) {
            // method can be declared in different package than it's receiver type
            pkg.addImport(msgData.pkg)
            return msgData
        }
    }

    return null
}

fun recursiveSearch(
    receiverType: Type,
    selectorName: String,
    pkg: Package,
    kind: MessageDeclarationType
): MessageMetadata? {
    var parent: Type? = receiverType.parent
    while (parent != null) {
        val parentResult = findAnyMethod(parent, selectorName, pkg, kind)
        if (parentResult != null)
            return parentResult
        parent = parent.parent
    }
    return null
}

fun checkForAny(selectorName: String, pkg: Package, kind: MessageDeclarationType): MessageMetadata? {
    val anyType = Resolver.defaultTypes[InternalTypes.Any]!!
    return findAnyMethod(anyType, selectorName, pkg, kind)
}

fun checkForError(receiverType: Type, selectorName: String, pkg: Package): MessageMetadata? {
    val errors = receiverType.errors
        ?: return null
    // y = 4 sas ifErrorDo: [5]
    // y is not errored now

    // y = | 4 sas
    // | Int => ...?? // can be used if this is statement
    // | Error1 => 4
    // | Error2 => 6

    val returnTypeWithoutErrors = receiverType.copyAnyType()
        .also { it.errors = null }

    val ifErrorKW = { returnTypeWithoutErrors2: Type, rootTypeOfAllErrors: Type.UnionRootType ->
        (createKeyword("ifError",
            listOf(
                KeywordArg("ifError",
                    Type.Lambda(
                        mutableListOf(KeywordArg("it", rootTypeOfAllErrors)),
                        returnTypeWithoutErrors2
                    )
                ),
            ),
            returnTypeWithoutErrors2
        ).emitKw("try {\n    $0\n} catch (it: Throwable) $1"))
    }

    val createUnionOfErrorsInCurrentScope = {
        val result = Type.UnionRootType(
            branches = errors.toList(),
            name = "ErrorsOfTheScope",
            typeArgumentList = emptyList(),
            fields = mutableListOf(KeywordArg("message", Resolver.defaultTypes[InternalTypes.String]!!)),
            pkg = pkg.packageName,
//            protocols = mutableMapOf(
//                "error" to Protocol(
//                    name = "error",
//                    keywordMsgs = mutableMapOf(ifErrorKW(returnTypeWithoutErrors))
//                )
//            ),
            isError = true,
            typeDeclaration = null
        )
        result
    }

    return when (selectorName) {
        "ifError" -> {
            ifErrorKW(returnTypeWithoutErrors, createUnionOfErrorsInCurrentScope()).second
        }
        else -> null
    }
}

fun checkForT(selectorName: String, pkg: Package, kind: MessageDeclarationType): MessageMetadata? {
    val unknownGenericType = Resolver.defaultTypes[InternalTypes.UnknownGeneric]!!
    return findAnyMethod(unknownGenericType, selectorName, pkg, kind)
}

fun throwNotFoundError(receiverType: Type, selectorName: String, token: Token, msgType: String): Nothing {

    val cantFind = "Cant send ${PURP}$msgType${RESET} message ${CYAN}$selectorName${RESET}"
    val errorText = if (receiverType is Type.NullableType)
        "$cantFind to nullable type: ${YEL}${receiverType}${RESET}, please use $CYAN unpackOrError${RESET}/${CYAN}unpackOrValue: value${RESET}/${CYAN}unpack: [it]${RESET}/${CYAN}unpack: ${WHITE}[...] ${CYAN}or: ${WHITE}T"
    else
        "$cantFind for type ${YEL}${receiverType}"
    token.compileError(errorText)
}


// returns true if it is static call, but not constructor(so we generate Clock.System instead of Clock.System())
fun Resolver.findStaticMessageType(
    receiverType: Type,
    selectorName: String,
    token: Token,
    msgType: MessageDeclarationType? = null
): Pair<MessageMetadata, Boolean> {

    fun search(type: Type): MessageMetadata? {
        var result: MessageMetadata? = null

        type.protocols.forEach { (_, v) ->
            val metadata = v.staticMsgs[selectorName]
            if (metadata != null) {
                result = metadata
            }
        }
        return result
    }

    var type: Type? = receiverType
    while (type != null) {
        val result = search(type)
        if (result != null) {
            val pkg = getCurrentPackage(token)
            pkg.addImport(receiverType.pkg)
            pkg.addImport(result.pkg)
            return Pair(result, false)
        }
        type = type.parent
    }

    // this is default new message if type doesn't have any fields
    if (selectorName == "new" && receiverType is Type.UserLike) {
        if (receiverType.fields.isEmpty()) {
            // u cant instantiate Root union
            if (receiverType is Type.UnionRootType) {
                token.compileError("You can't instantiate root of the union(${YEL}$receiverType${RESET})")
            }

            val result = UnaryMsgMetaData(
                name = "__new",
                returnType = receiverType,
                pkg = currentPackageName,
                declaration = null
            )
            val pkg = getCurrentPackage(token)
            pkg.addImport(receiverType.pkg)
            return Pair(result, false)
        } else {
            val fields = receiverType.fields.joinToString(" e") { it.name + ": " + it.type.name + "_value"}
            token.compileError("${WHITE}new${RESET} can't be used with ${YEL}$receiverType${RESET}, since it has fields(use $YEL$receiverType$RESET $CYAN$fields)")
        }
    }

    // if this is binding, then getters are static, calls without ()
    if (msgType != null && findPackageOrError(receiverType.pkg, token).isBinding) {
        if (msgType == MessageDeclarationType.Binary) token.compileError("Binary constructors won't supported! lol whatudoing")
        return Pair(findAnyMsgType(receiverType, selectorName, token, msgType), true)
    }
    if (GlobalVariables.isLspMode) {
        token.compileError("Message declaration in progress")
    } else {
        token.compileError("Cant find static message: $selectorName for type ${receiverType.name}")

    }
}

fun Resolver.findAnyMsgType(
    receiverType: Type,
    selectorName: String,
    token: Token,
    msgType: MessageDeclarationType
): MessageMetadata {

    val pkg = getCurrentPackage(token)
    findAnyMethod(receiverType, selectorName, pkg, msgType)?.let {
        return it
    }

    recursiveSearch(receiverType, selectorName, pkg, msgType)?.let {
        return it
    }


    checkForAny(selectorName, pkg, msgType)?.let {
        return it
    }
    checkForT(selectorName, pkg, msgType)?.let {
        it.forGeneric = true
        return it
    }

    // return ifError method if return type have possible errors
    checkForError(receiverType, selectorName, pkg)?.let {

        // remove one decl
        val resolvingMsgDecl = this.resolvingMessageDeclaration
        val msg = if (stack.isNotEmpty()) this.stack.last() else null

        if (resolvingMsgDecl != null && msg is MessageSend) {
//            if (msg.receiver.type?.errors?.isNotEmpty() == true) {
//            }
            // TODO тут можно судить просто по наличию у msg.receiver.type.errors
            if (msg.receiver is MessageSend) {
                msg.receiver.messages.forEach { a ->
                    val b = resolvingMsgDecl.stackOfPossibleErrors.find { it.msg == a }
                    if (b != null) {
                        resolvingMsgDecl.stackOfPossibleErrors.remove(b)
                    }
                }
            }
        }

        return it
    }

    if (GlobalVariables.isDemonMode) {
        findSimilar(to = selectorName, forType = receiverType)
        onCompletionExc(mapOf())
    } else if (GlobalVariables.isLspMode) {
        // IDK, send find similar as array?
        val (list, _) = findSimilar(to = selectorName, forType = receiverType)

        if (list.count() == 1)
            token.compileError("Did you mean ${list.first()}")

        if (list.isNotEmpty()) {
            token.compileError("Did you mean one of $list")
        }

        throwNotFoundError(receiverType, selectorName, token, msgType.name.lowercase())
    } else
        throwNotFoundError(receiverType, selectorName, token, msgType.name.lowercase())
}
