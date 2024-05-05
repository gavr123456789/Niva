package main.utils

import main.frontend.meta.compileError
import frontend.resolver.Package
import frontend.resolver.Protocol
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.createFakeToken

fun StringBuilder.appendnl(s: String) = this.append("$s\n")

private fun Protocol.generateInfoProtocol() = buildString {
    val it = this@generateInfoProtocol
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

private fun Type.EnumRootType.generateInfo() = buildString {
    appendnl("\n## enum root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.values.forEach {
        append(it.generateInfoProtocol())
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
        is Type.UserType -> this@infoPrint.generateInfoType()
        is Type.UnionRootType -> this@infoPrint.generateInfoUnionRoot()
        is Type.EnumRootType -> this@infoPrint.generateInfo()
        is Type.InternalType -> this@infoPrint.generateInfoType()
        is Type.NullableType -> this@infoPrint.getTypeOrNullType().generateInfoType()
        is Type.UnresolvedType -> this@infoPrint.realType().generateInfoType()

        is Type.Lambda -> TODO("Can't print lambda info yet")

        is Type.EnumBranchType -> TODO()
        is Type.UnionBranchType -> TODO()

        is Type.UnknownGenericType -> TODO()
        is Type.KnownGenericType -> TODO()

//        is Type.ErrorType -> TODO()


        Type.RecursiveType -> TODO()

    })

}

// only UserType and Internal
private fun Type.generateInfoType() = buildString {
//    this@generateInfo.name

    if (this@generateInfoType is Type.UnionBranchType){
        append("\n#### branch $name")
    } else {
        appendnl("\n## type $name")
    }

    if (this@generateInfoType is Type.UserLike) {
        fields.forEach {
            appendnl("- ${it.name}: ${it.type}  ")
        }
    }
    protocols.values.forEach {
        append(it.generateInfoProtocol())
    }
}

private fun Type.UnionRootType.generateInfoUnionRoot() = buildString {
    appendnl("\n## union root $name")
    fields.forEach {
        appendnl("- ${it.name}: ${it.type}  ")
    }
    protocols.values.forEach {
        append(it.generateInfoProtocol())
    }
    if (branches.isNotEmpty()) {
        append("### branches")
        branches.forEach {
            append(it.generateInfoType())
        }
    }
}


private fun Package.generateInfo(userOnly: Boolean) = buildString {
    if (types.isEmpty()) return@buildString

    appendnl("# package $packageName")
    if (!userOnly) {
        val internalTypes = types.values.filterIsInstance<Type.InternalLike>()
        internalTypes.forEach {
            append(it.generateInfoType())
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
            append(it.generateInfoType())
        }
    }

    if (importedUserLike.isNotEmpty()) {
        append("Bindings  \n")
        importedUserLike.forEach {
            append(it.generateInfoType())
        }
    }

    val unionTypes = types.values.filterIsInstance<Type.UnionRootType>()
    if (unionTypes.isNotEmpty()) {
        append("  \n")
        unionTypes.forEach {
            append(it.generateInfoUnionRoot())
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
