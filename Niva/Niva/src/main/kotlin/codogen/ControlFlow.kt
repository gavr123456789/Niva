package main.codogen

import frontend.resolver.Type
import main.frontend.parser.types.ast.*

fun ControlFlow.If.generateIf(): String = buildString {

    val firstIfBranch = ifBranches[0]

    append("if (")
    append(firstIfBranch.ifExpression.generateExpression())
    append(") {\n")
    append("    ")

    when (firstIfBranch) {
        is IfBranch.IfBranchSingleExpr -> append(firstIfBranch.thenDoExpression.generateExpression())
        is IfBranch.IfBranchWithBody -> append(codegenKt(firstIfBranch.body.statements, 1))
    }
    append("\n}")


    ifBranches.drop(1).forEach { ifBranch ->
        append(" else if (")
        append(ifBranch.ifExpression.generateExpression())
        append(") {\n")
        append("    ")
        when (ifBranch) {
            is IfBranch.IfBranchSingleExpr -> append(ifBranch.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codegenKt(ifBranch.body.statements, 1))
        }
        append("\n}")

    }

    if (elseBranch != null) {
        append(" else {\n")
        append(codegenKt(elseBranch, 1))
        append("} ")
    }

}


fun ControlFlow.Switch.generateSwitch() = buildString {
    append("when (")
    append(switch.generateExpression())
    append(") {\n")
    ifBranches.forEach { it ->
        append("    ")

        if (kind != ControlFlowKind.ExpressionTypeMatch && kind != ControlFlowKind.StatementTypeMatch) {
            append(it.ifExpression.generateExpression())
            append(", " + it.otherIfExpressions.joinToString(", ") {x -> x.generateExpression() })
        } else {
            append("is ", it.ifExpression.generateExpression())
        }

        append(" -> ")
        when (it) {
            is IfBranch.IfBranchSingleExpr -> {
                append(it.thenDoExpression.generateExpression())
            }

            is IfBranch.IfBranchWithBody -> append("{\n",codegenKt(it.body.statements, 1), "\n}\n")
        }
        append("\n")
    }

    if (elseBranch != null) {
        append("    else -> {")
        val elseBranchCode = codegenKt(elseBranch, 0)
        appendLine(elseBranchCode)
        appendLine("    }")
        appendLine("}")
    } else {
        // if this is switch on errors, we need fix kotlins exhaustive, because its possible to check it only on niva side
        // (errors are effects, and set of them exist in current scope)
        val switch = this@generateSwitch.switch
        val type = switch.type
        if (type is Type.Union && type.isError && this@generateSwitch.kind == ControlFlowKind.ExpressionTypeMatch) {
            append("    else -> throw Exception(\"Compiler bug, non exhaustive switch on error, got \${$switch}\")")
        }
        // end of when
        append("}\n")
    }
}


inline fun <T> Iterable<T>.forEach(exceptLastDo: (T) -> Unit, action: (T) -> Unit) {
    val c = count()
    this.forEachIndexed { index, t ->
        action(t)
        if (index != c - 1) {
            exceptLastDo(t)
        }
    }
}

fun ListCollection.generateList() = buildString {
    if (!isMutableCollection)
        append("listOf")
    else
        append("mutableListOf")

    // when we have some alias to that, there is an error since it inputs the real type, not aliased, and Kotlin say error no such function, since alias expected(http4k)

//    val type = this@generateList.type
//    if (type is Type.UserLike && type.typeArgumentList.find { it.name.isGeneric() } == null ) {
//        append("<")
//        type.typeArgumentList.forEach {
//            append(it.toKotlinString(true))
//        }
//        append(">")
//    }
    append("(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}

fun MapCollection.generateMap() = buildString {
    if (!isMutable)
        append("mapOf(")
    else
        append("mutableMapOf(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.first.generateExpression(), " to ", it.second.generateExpression())
    }

    append(")")
}

fun SetCollection.generateSet() = buildString {
    if (!isMutableCollection)
        append("setOf(")
    else
        append("mutableSetOf(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}
