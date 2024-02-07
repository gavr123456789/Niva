package main.frontend.resolver

import frontend.meta.Token
import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.InternalTypes
import frontend.resolver.*
import frontend.resolver.Package
import main.*
import main.utils.isGeneric


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
        val msgData = lens(v, selectorName, kind)

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

fun checkForT(selectorName: String, pkg: Package, kind: MessageDeclarationType): MessageMetadata? {
    val unknownGenericType = Resolver.defaultTypes[InternalTypes.UnknownGeneric]!!
    return findAnyMethod(unknownGenericType, selectorName, pkg, kind)
}

fun throwNotFoundError(receiverType: Type, selectorName: String, token: Token, msgType: String): Nothing {
    val errorText = if (receiverType is Type.NullableType)
        "Cant send $PURP$msgType$RESET message $CYAN$selectorName$RESET to nullable type: $YEL${receiverType}?$RESET, please use $CYAN unpackOrError$RESET/${CYAN}unpackOr: value$RESET/${CYAN}unpack: [it]"
    else
        "Cant find $PURP$msgType$RESET message: $CYAN$selectorName$RESET for type $YEL${receiverType}"
    token.compileError(errorText)
}


// returns true if it is static call, but not constructor(so we generate Clock.System instead of Clock.System())
fun Resolver.findStaticMessageType(
    receiverType: Type,
    selectorName: String,
    token: Token,
    msgType: MessageDeclarationType? = null
): Pair<MessageMetadata, Boolean> {
    receiverType.protocols.forEach { (_, v) ->
        val metadata = v.staticMsgs[selectorName]
        if (metadata != null) {
            val pkg = getCurrentPackage(token)
            pkg.addImport(receiverType.pkg)
            return Pair(metadata, false)
        }
    }

    // if this is binding, then getters are static, calls without ()
    if (msgType != null && getPackage(receiverType.pkg, token).isBinding) {
        if (msgType == MessageDeclarationType.Binary) token.compileError("Binary constructors won't supported! lol whatudoing")
        return Pair(findAnyMsgType(receiverType, selectorName, token, msgType), true)
    }

    if (selectorName == "new") {
        if (receiverType is Type.UserLike && receiverType.fields.isEmpty()) {
            val result = UnaryMsgMetaData(
                name = "new!",
                returnType = receiverType,
                pkg = currentPackageName,
            )
            return Pair(result, false)
        } else {
            token.compileError("${WHITE}new$RESET can't bew used with $YEL$receiverType$RESET, it has fields(use them as constructor), or its basic type ")
        }
    }

    throw Exception("Cant find static message: $selectorName for type ${receiverType.name}")
//    token.compileError("Cant find static message: $selectorName for type ${receiverType.name}")
}

fun Resolver.findAnyMsgType(
    receiverType: Type,
    selectorName: String,
    token: Token,
    msgType: MessageDeclarationType
): MessageMetadata {

    val pkg = getCurrentPackage(token)
    val result = findAnyMethod(receiverType, selectorName, pkg, msgType)
    if (result != null)
        return result

    recursiveSearch(receiverType, selectorName, pkg, msgType)?.let { return it }
    checkForAny(selectorName, pkg, msgType)?.let {
        return it
    }
    checkForT(selectorName, pkg, msgType)?.let {
        it.forGeneric = true
        return it
    }

    throwNotFoundError(receiverType, selectorName, token, msgType.name.lowercase())
}
