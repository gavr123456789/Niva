package main.frontend.resolver

import frontend.meta.Token
import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.InternalTypes
import frontend.resolver.*
import frontend.resolver.Package
import main.CYAN
import main.RESET
import main.WHITE
import main.YEL
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
        val q = lens(v, selectorName, kind) //v.unaryMsgs[selectorName]

        if (q != null) {
            // method can be declared in different package than it's receiver type
            pkg.addImport(q.pkg)
            return q
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
    val messageFromAny = findAnyMethod(anyType, selectorName, pkg, kind)
    if (messageFromAny != null) {
        return messageFromAny
    }
    return null
}

fun checkForNullableOrNotFound(receiverType: Type, selectorName: String, token: Token): Nothing {
    val errorText = if (receiverType is Type.NullableType)
        "Cant send message $CYAN$selectorName$RESET to nullable type: $YEL${receiverType.name}?$RESET, please use $CYAN unpackOrError$RESET/${CYAN}unpackOr: value$RESET/${CYAN}unpack: [it]"
    else
        "Cant find message: $CYAN$selectorName$RESET for type $YEL${receiverType.pkg}$RESET.$YEL${receiverType.name}"
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
        val q = v.staticMsgs[selectorName]
        if (q != null) {
            val pkg = getCurrentPackage(token)
            pkg.addImport(receiverType.pkg)
            return Pair(q, false)
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
    if (receiverType.name.isGeneric()) {
        throw Exception("Compiler bug, receiver is unresolved generic")
    }

    val pkg = getCurrentPackage(token)
    val result = findAnyMethod(receiverType, selectorName, pkg, msgType)
    if (result != null)
        return result

    recursiveSearch(receiverType, selectorName, pkg, msgType)?.let { return it }
    checkForAny(selectorName, pkg, msgType)?.let { return it }
    checkForNullableOrNotFound(receiverType, selectorName, token)
}
