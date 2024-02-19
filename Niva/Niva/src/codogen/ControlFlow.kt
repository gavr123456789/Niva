package codogen

import frontend.parser.types.ast.*

fun ControlFlow.If.generateIf(): String = buildString {


//    if (ifBranches.count() == 1) {
//        val first = ifBranches[0]
//        if (first is IfBranch.IfBranchWithBody) {
//            return codegenIfLet(first)
//        }
//    }

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

fun MapCollection.generateMap() = buildString {
    append("mutableMapOf(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.first.generateExpression(), " to ", it.second.generateExpression())
    }

    append(")")
}

fun SetCollection.generateSet() = buildString {
    append("mutableSetOf(")

    initElements.forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}
