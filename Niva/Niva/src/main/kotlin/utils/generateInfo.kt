package main.utils

import main.frontend.meta.compileError
import frontend.resolver.Package
import frontend.resolver.MessageMetadata
import frontend.resolver.Protocol
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.DocComment
import main.frontend.parser.types.ast.InternalTypes

fun StringBuilder.appendnl(s: String) = this.append("$s\n")
fun StringBuilder.appendnlWithCodeBlock(s: String, doc: DocComment?) {
    if (doc != null) {
        this.appendnl(doc.text)
    }
    this.appendnl("```Scala")
    this.append("$s\n")
    this.appendnl("```")

}

private fun MessageMetadata.isDeclaredForNullableReceiver(): Boolean =
    declaration?.forTypeAst?.isNullable == true

private fun Protocol.generateInfoProtocol(messageFilter: (MessageMetadata) -> Boolean = { true }) = buildString {
    val it = this@generateInfoProtocol
//    appendnl("#### ${it.name} protocol\n")
    val unaryMsgs = it.unaryMsgs.values.filter(messageFilter)
    if (unaryMsgs.isNotEmpty()) {
        appendnl("### unary")
        unaryMsgs.forEach { u ->
            appendnlWithCodeBlock(u.toString(), u.docComment)
//            appendnl("\t" + u.toString())
        }
    }
    val binaryMsgs = it.binaryMsgs.values.filter(messageFilter)
    if (binaryMsgs.isNotEmpty()) {
        appendnl("### binary")
        binaryMsgs.forEach { u ->
            appendnlWithCodeBlock(u.toString(), u.docComment)

//            appendnl("\t" + u.toString())
        }
    }
    val keywordMsgs = it.keywordMsgs.values.filter(messageFilter)
    if (keywordMsgs.isNotEmpty()) {
        appendnl("### keyword")
        keywordMsgs.forEach { u ->
            appendnlWithCodeBlock(u.toString(), u.docComment)

//            appendnl("\t" + u.toString())
        }
    }
    val staticMsgs = it.staticMsgs.values.filter(messageFilter)
    if (staticMsgs.isNotEmpty()) {
        appendnl("### static")
        staticMsgs.forEach { u ->
            appendnlWithCodeBlock(u.toString(), u.docComment)

//            appendnl("\t" + u.toString())
        }
    }

}

private fun Type.EnumRootType.generateInfo() = buildString {
    appendnl("\n## enum root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.forEach { (_, protocol) ->
        append(protocol.generateInfoProtocol())
    }
    if (this@generateInfo.branches.isNotEmpty()) {
        append("### branches")
        this@generateInfo.branches.forEach {
            append("- ${it.name}")
            fields.forEach { enumField ->
                appendnl("  - ${enumField.name}: ${enumField.type}  ")
            }
        }
    }
}

fun Type.infoPrint() = buildString {
    append(when (this@infoPrint) {
        is Type.UserType -> this@infoPrint.generateInfoType(userOnly = false)
        is Type.UnionRootType -> this@infoPrint.generateInfoUnionRoot(userOnly = false)
        is Type.EnumRootType -> this@infoPrint.generateInfo()
        is Type.InternalType -> this@infoPrint.generateInfoType(userOnly = false)
        is Type.NullableType -> this@infoPrint.getTypeOrNullType().generateInfoType(userOnly = false, nullableOnly = true)
        is Type.UnresolvedType -> this@infoPrint.realType().generateInfoType(userOnly = false)

        is Type.Lambda -> TODO("Can't print lambda info yet")

        is Type.EnumBranchType -> TODO()
        is Type.UnionBranchType -> TODO()

        is Type.UnknownGenericType -> TODO()

//        is Type.ErrorType -> TODO()



    })

}

private fun Type.hasNullableReceiverInfo(userOnly: Boolean): Boolean =
    protocols
        .filterKeys { protocolName -> !userOnly || protocolName != "dynamic" }
        .values
        .any { protocol ->
            protocol.unaryMsgs.values.any { it.isDeclaredForNullableReceiver() } ||
                    protocol.binaryMsgs.values.any { it.isDeclaredForNullableReceiver() } ||
                    protocol.keywordMsgs.values.any { it.isDeclaredForNullableReceiver() } ||
                    protocol.staticMsgs.values.any { it.isDeclaredForNullableReceiver() }
        }

// only UserType and Internal
private fun Type.generateInfoType(userOnly: Boolean, nullableOnly: Boolean = false) = buildString {
//    this@generateInfo.name

    val nullableSuffix = if (nullableOnly) "?" else ""
    if (this@generateInfoType is Type.UnionBranchType){
        appendnl("\n#### branch $name")
    } else {
        appendnl("\n## type $name$nullableSuffix")
    }

    if (!nullableOnly && this@generateInfoType is Type.UserLike) {
        fields.forEach {
            appendnl("- ${it.name}: ${it.type}  ")
        }
    }
    protocols.forEach { (protocolName, protocol) ->
        if (!userOnly || protocolName != "dynamic") {
            append(protocol.generateInfoProtocol { it.isDeclaredForNullableReceiver() == nullableOnly })
        }
    }
}

private fun Type.generateNullableReceiverInfoType(userOnly: Boolean): String =
    if (hasNullableReceiverInfo(userOnly)) generateInfoType(userOnly = userOnly, nullableOnly = true) else ""

private fun Type.hasGenericReceiverInfo(userOnly: Boolean, messageFilter: (MessageMetadata) -> Boolean): Boolean =
    protocols
        .filterKeys { protocolName -> !userOnly || protocolName != "dynamic" }
        .values
        .any { protocol ->
            protocol.unaryMsgs.values.any(messageFilter) ||
            protocol.binaryMsgs.values.any(messageFilter) ||
            protocol.keywordMsgs.values.any(messageFilter) ||
            protocol.staticMsgs.values.any(messageFilter)
        }

private fun Type.generateGenericReceiverInfoType(
    typeName: String,
    userOnly: Boolean,
    messageFilter: (MessageMetadata) -> Boolean
) = buildString {
    if (!hasGenericReceiverInfo(userOnly, messageFilter)) return@buildString

    appendnl("\n## type $typeName")
    protocols.forEach { (protocolName, protocol) ->
        if (!userOnly || protocolName != "dynamic") {
            append(protocol.generateInfoProtocol(messageFilter))
        }
    }
}

private fun Type.UnionRootType.generateInfoUnionRoot(userOnly: Boolean) = buildString {
    appendnl("\n## union root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.forEach { (protocolName, protocol) ->
        if (!userOnly || protocolName != "dynamic") {
            append(protocol.generateInfoProtocol { !it.isDeclaredForNullableReceiver() })
        }
    }
    if (branches.isNotEmpty()) {
        append("### branches")
        branches.forEach {
            append(it.generateInfoType(userOnly = userOnly))
        }
    }
}


private fun Package.generateInfo(userOnly: Boolean) = buildString {
    if (types.isEmpty()) return@buildString

    appendnl("# package $packageName")
    if (!userOnly) {
        val internalTypes = types.values.filterIsInstance<Type.InternalType>()
        internalTypes.forEach {
            append(it.generateInfoType(userOnly = userOnly))
        }
    }

    val defaultUserTypeNames = setOf("Error", "List", "Set", "Map", "MutableList", "MutableMap", "MutableSet")

    val userLikeTypes = if (userOnly)
        types.values.filterIsInstance<Type.UserType>().filter { !defaultUserTypeNames.contains(it.name) }
    else
        types.values.filterIsInstance<Type.UserType>()

    val importedUserLike = mutableListOf<Type.UserType>()
    val notImportedUserLike = mutableListOf<Type.UserType>()

    userLikeTypes.forEach {
//        append("  \n")
        if (it.isBinding) {
            importedUserLike.add(it)
        } else {
            notImportedUserLike.add(it)
        }
    }

    if (notImportedUserLike.isNotEmpty()) {
        append("  \n")
        notImportedUserLike.forEach {
            append(it.generateInfoType(userOnly = userOnly))
            append(it.generateNullableReceiverInfoType(userOnly))
        }
    }

    if (importedUserLike.isNotEmpty()) {
        append("Bindings  \n")
        importedUserLike.forEach {
            append(it.generateInfoType(userOnly = userOnly))
            append(it.generateNullableReceiverInfoType(userOnly))
        }
    }

    val unionTypes = types.values.filterIsInstance<Type.UnionRootType>()
    if (unionTypes.isNotEmpty()) {
        append("  \n")
        unionTypes.forEach {
            append(it.generateInfoUnionRoot(userOnly))
            append(it.generateNullableReceiverInfoType(userOnly))
        }
    }

}

fun generatePkgInfo(resolver: Resolver, pkgName: String) = buildString {
    val mainProject = resolver.projects[resolver.projectName]!!
    val pkg = mainProject.packages[pkgName] ?: createFakeToken().compileError("Package for info: $WHITE$pkgName$RESET not found")
    append(pkg.generateInfo(false))
}


fun generateInfo(resolver: Resolver, userOnly: Boolean) = buildString {
    val mainProject = resolver.projects[resolver.projectName]!!
    mainProject.packages.values.forEach {
        if (it.types.isNotEmpty()) {
            append("\n", it.generateInfo(userOnly))
        }
    }

    val userDeclaredGenericMessage = { message: MessageMetadata ->
        !userOnly || message.declaration != null
    }

    val genericType = Resolver.defaultTypes[InternalTypes.UnknownGeneric]!!
    append(
        genericType.generateGenericReceiverInfoType(
            typeName = "T",
            userOnly = userOnly,
            messageFilter = { userDeclaredGenericMessage(it) && !it.isDeclaredForNullableReceiver() }
        )
    )
    append(
        Resolver.nullableUnknownGenericType.generateGenericReceiverInfoType(
            typeName = "T?",
            userOnly = userOnly,
            messageFilter = { userDeclaredGenericMessage(it) && it.isDeclaredForNullableReceiver() }
        )
    )
}
