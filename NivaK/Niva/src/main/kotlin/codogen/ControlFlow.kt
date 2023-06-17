package codogen

import frontend.parser.types.ast.ControlFlow
import frontend.parser.types.ast.IfBranch


fun ControlFlow.If.generateIf(): String = buildString {

    append("if (")
    val firstIfBranch = ifBranches[0]
    append(firstIfBranch.ifExpression.generateExpression())
    append(") {\n")
    append("    ")
    when (firstIfBranch) {
        is IfBranch.IfBranchSingleExpr -> append(firstIfBranch.thenDoExpression.generateExpression())
        is IfBranch.IfBranchWithBody -> append(codogenKt(firstIfBranch.body, 1))
    }
    append("\n}")


    ifBranches.drop(1).forEach { ifBranch ->
        append(" else if (")
        append(ifBranch.ifExpression.generateExpression())
        append(") {\n")
        append("    ")
        when (ifBranch) {
            is IfBranch.IfBranchSingleExpr -> append(ifBranch.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codogenKt(ifBranch.body, 1))
        }
        append("\n}")

    }

    if (elseBranch != null) {
        append(" else {\n")
        append(codogenKt(elseBranch, 1))
        append("} ")
    }

}


fun ControlFlow.Switch.generateSwitch() = buildString {
    append("when (")
    append(switch.generateExpression())
    append(") {\n")
    ifBranches.forEach {
        append("    ")

        append(it.ifExpression.generateExpression())

        append(" -> ")
        when (it) {
            is IfBranch.IfBranchSingleExpr -> append(it.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codogenKt(it.body, 1))
        }
        append("\n")
    }

    if (elseBranch != null) {
        append("    else -> ")
        append(codogenKt(elseBranch, 0))
        append("}\n")
    }
}
