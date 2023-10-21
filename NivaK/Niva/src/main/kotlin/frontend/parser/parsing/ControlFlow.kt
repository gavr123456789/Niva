package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.ControlFlow
import frontend.parser.types.ast.ControlFlowKind
import frontend.parser.types.ast.Expression
import frontend.parser.types.ast.IfBranch

fun Parser.ifOrSwitch(): ControlFlow {

    val wasFirstPipe = check(TokenType.Pipe)
    var x = 1 // because first is pipe already matched
    do {
        if (check(TokenType.Then, x)) {
            return ifStatementOrExpression()
        }
        // one-line switch
        if (wasFirstPipe && check(TokenType.Pipe, x)) {
            return switchStatementOrExpression()
        }
        x++
    } while (!check(TokenType.EndOfLine, x))

    return switchStatementOrExpression()
}

fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {
        step() // skip Pipe
        val ifExpression = expression()

        matchAssert(TokenType.Then, "\"=>\" expected, but found ${getCurrentToken().lexeme}")
        val (body, isSingleExpression) = methodBody()

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


        match(TokenType.EndOfLine)
        match(TokenType.EndOfFile)
    } while (check(TokenType.Pipe))

    return result
}

fun Parser.ifStatementOrExpression(): ControlFlow.If {

    val pipeTok = peek()
    val ifBranches = ifBranches()

    val elseBranch = if (match(TokenType.Else)) {
        methodBody().first.toList()
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
    skipNewLinesAndComments()
    val otherPart = ifStatementOrExpression()

    val result = ControlFlow.Switch(
        switch = switchExpression,
        iF = otherPart
    )
    return result

}
