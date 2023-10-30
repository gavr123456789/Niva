package codogen

import frontend.parser.types.ast.ControlFlow
import frontend.parser.types.ast.ControlFlowKind
import frontend.parser.types.ast.IfBranch
import frontend.parser.types.ast.ListCollection
import frontend.typer.Type


fun ControlFlow.If.generateIf(): String = buildString {

    append("if (")
    val firstIfBranch = ifBranches[0]
    append(firstIfBranch.ifExpression.generateExpression())
    append(") {\n")
    append("    ")
    when (firstIfBranch) {
        is IfBranch.IfBranchSingleExpr -> append(firstIfBranch.thenDoExpression.generateExpression())
        is IfBranch.IfBranchWithBody -> append(codegenKt(firstIfBranch.body, 1))
    }
    append("\n}")


    ifBranches.drop(1).forEach { ifBranch ->
        append(" else if (")
        append(ifBranch.ifExpression.generateExpression())
        append(") {\n")
        append("    ")
        when (ifBranch) {
            is IfBranch.IfBranchSingleExpr -> append(ifBranch.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codegenKt(ifBranch.body, 1))
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
    ifBranches.forEach {
        append("    ")

        if (kind != ControlFlowKind.ExpressionTypeMatch && kind != ControlFlowKind.StatementTypeMatch) {
            append(it.ifExpression.generateExpression())
        } else {
            append("is ", it.ifExpression.generateExpression())
            // if this is Generic then we need to add <*>, because type erasing(
            val type = it.ifExpression.type
            if (type is Type.UserLike && type.typeArgumentList.isNotEmpty()) {
                append("<*>")
            }
        }

        append(" -> ")
        when (it) {
            is IfBranch.IfBranchSingleExpr -> append(it.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codegenKt(it.body, 1))
        }
        append("\n")
    }

    if (elseBranch != null) {
        append("    else -> ")
        append(codegenKt(elseBranch, 0))
        append("}\n")
    } else {
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
    append("mutableListOf(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}
