package frontend.parser.parsing

import main.utils.WHITE
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*

fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {
        step() // skip Pipe
        val ifExpression = expression(dot = true)
        // 1^,2,3 => 1 echo
        val otherIfExpressions = if (match(TokenType.Comma)) {
            val q = commaSeparatedExpressions()
            q
        } else listOf()

        matchAssert(TokenType.Then, "\"=>\" expected, but found ${getCurrentToken().lexeme}")
//        skipOneEndOfLineOrFile()
        var (body, isSingleExpression) = methodBody(true)
        if (body.isNotEmpty() && body[0] is ReturnStatement) isSingleExpression = false

        result.add(
            if (isSingleExpression) {
                IfBranch.IfBranchSingleExpr(
                    ifExpression = ifExpression,
                    body[0] as Expression,
                    otherIfExpressions = otherIfExpressions
                )
            } else {
                IfBranch.IfBranchWithBody(
                    ifExpression = ifExpression,
                    body = CodeBlock(listOf(), body, token = ifExpression.token),
                    otherIfExpressions = otherIfExpressions
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
    } else {
        skipNewLinesAndComments()
        peek()
    }


    if (fromSwitch && pipeTok.kind != TokenType.Pipe) {
        pipeTok.compileError("| expected but found: ${WHITE}${pipeTok.lexeme}")
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
    skipOneEndOfLineOrComment()

    val otherPart = ifStatementOrExpression(fromSwitch = true)
    val result = ControlFlow.Switch(
        switch = switchExpression,
        iF = otherPart
    )
    return result
}
