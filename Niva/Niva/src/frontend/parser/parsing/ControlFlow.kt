package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {
        step() // skip Pipe
        val ifExpression = expression(dot = true)

        matchAssert(TokenType.Then, "\"=>\" expected, but found ${getCurrentToken().lexeme}")
//        skipOneEndOfLineOrFile()
        var (body, isSingleExpression) = methodBody(true, true)
        if (body.isNotEmpty() && body[0] is ReturnStatement) isSingleExpression = false

        result.add(
            if (isSingleExpression) {
                IfBranch.IfBranchSingleExpr(
                    ifExpression = ifExpression,
                    body[0] as Expression
                )
            } else {
                IfBranch.IfBranchWithBody(
                    ifExpression = ifExpression,
                    body = body
                )
            }
        )

        skipNewLinesAndComments()

    } while (check(TokenType.Pipe))

    return result
}

fun Parser.ifStatementOrExpression(fromSwitch: Boolean = false): ControlFlow.If {
    // pure is _|
    // !pure is for switch parsing branches reuse |
    val pipeTok = if (!fromSwitch) {
        val token = matchAssert(TokenType.Underscore)
        skipNewLinesAndComments()
        token
    } else peek()

    if (fromSwitch && pipeTok.kind != TokenType.Pipe) {
        pipeTok.compileError("| expected but found: ${pipeTok.lexeme}")
    }
    val ifBranches = ifBranches()

    val elseBranch = if (match(TokenType.Else)) {
        methodBody(true).first.toList()
    } else null


    val result = ControlFlow.If(
        type = null,
        ifBranches = ifBranches,
        kind = ControlFlowKind.Statement,
        elseBranch = elseBranch,
        token = pipeTok,
    )

    return result
}

fun Parser.switchStatementOrExpression(): ControlFlow.Switch {
    matchAssert(TokenType.Pipe, "| expected")

    val switchExpression = expression()
    skipOneEndOfLineOrFile()
//    skipNewLinesAndComments()
    val otherPart = ifStatementOrExpression(fromSwitch = true)
    val result = ControlFlow.Switch(
        switch = switchExpression,
        iF = otherPart
    )
    return result
}
