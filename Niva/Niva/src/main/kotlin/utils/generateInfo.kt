package main.utils

import main.frontend.meta.compileError
import frontend.resolver.Package
import frontend.resolver.Protocol
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.createFakeToken

fun StringBuilder.appendnl(s: String) = this.append("$s\n")

private fun Protocol.generateInfo() = buildString {
    val it = this@generateInfo
//    appendnl("#### ${it.name} protocol\n")
    if (it.unaryMsgs.isNotEmpty()) {
        appendnl("### unary")
        it.unaryMsgs.values.forEach { u ->
            appendnl("\t" + u.toString())
        }
    }
    if (it.binaryMsgs.isNotEmpty()) {
        appendnl("### binary")
        it.binaryMsgs.values.forEach { u ->
            appendnl("\t" + u.toString())
        }
    }
    if (it.keywordMsgs.isNotEmpty()) {
        appendnl("### keyword")
        it.keywordMsgs.values.forEach { u ->
            appendnl("\t" + u.toString())
        }
    }
    if (it.staticMsgs.isNotEmpty()) {
        appendnl("### static")
        it.staticMsgs.values.forEach { u ->
            appendnl("\t" + u.toString())
        }
    }

}

private fun Type.UserEnumRootType.generateInfo() = buildString {
    appendnl("\n## enum root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.values.forEach {
        append(it.generateInfo())
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
        is Type.UserType -> this@infoPrint.generateInfo()
        is Type.UserUnionRootType -> this@infoPrint.generateInfo()
        is Type.UserEnumRootType -> this@infoPrint.generateInfo()
        is Type.InternalType -> this@infoPrint.generateInfo()
        is Type.NullableType -> this@infoPrint.getTypeOrNullType().generateInfo()

        is Type.Lambda -> TODO("Can't print lambda info yet")

        is Type.UserEnumBranchType -> TODO()
        is Type.UserUnionBranchType -> TODO()

        is Type.UnknownGenericType -> TODO()
        is Type.KnownGenericType -> TODO()

        Type.RecursiveType -> TODO()
    })

}

// only UserType and Internal
private fun Type.generateInfo() = buildString {
    this@generateInfo.name

    appendnl("\n## type $name")

    if (this@generateInfo is Type.UserType) {
        fields.forEach {
            appendnl("- ${it.name}: ${it.type}  ")
        }
    }
    protocols.values.forEach {
        append(it.generateInfo())
    }
}

private fun Type.UserUnionRootType.generateInfo() = buildString {
    appendnl("\n## union root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.values.forEach {
        append(it.generateInfo())
    }
    if (this@generateInfo.branches.isNotEmpty()) {
        append("### branches")
        this@generateInfo.branches.forEach {
            it.generateInfo()
        }
    }
}


private fun Package.generateInfo(userOnly: Boolean) = buildString {
    if (types.isEmpty()) return@buildString

    appendnl("# package $packageName")
    if (!userOnly) {
        val internalTypes = types.values.filterIsInstance<Type.InternalLike>()
        internalTypes.forEach {
            append(it.generateInfo())
        }
    }

    val defaultUserTypeNames = setOf("Error", "List", "MutableList", "MutableMap", "MutableSet")

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
            append(it.generateInfo())
        }
    }

    if (importedUserLike.isNotEmpty()) {
        append("Bindings  \n")
        importedUserLike.forEach {
            append(it.generateInfo())
        }
    }

    val unionTypes = types.values.filterIsInstance<Type.UserUnionRootType>()
    if (unionTypes.isNotEmpty()) {
        append("  \n")
        unionTypes.forEach {
            append(it.generateInfo())
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
}
