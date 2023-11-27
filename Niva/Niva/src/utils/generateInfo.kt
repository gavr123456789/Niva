package main.utils

import frontend.parser.types.ast.UnionBranch
import frontend.typer.Package
import frontend.typer.Protocol
import frontend.typer.Resolver
import frontend.typer.Type
import frontend.typer.Type.RecursiveType.fields

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


fun generateInfo(resolver: Resolver, userOnly: Boolean) = buildString {
    val mainProject = resolver.projects[resolver.projectName]!!
    mainProject.packages.values.forEach {
        if (it.types.isNotEmpty()) {
            append("\n", it.generateInfo(userOnly))
        }
    }
}
