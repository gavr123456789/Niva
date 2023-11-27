package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Parser.ifOrSwitch(): ControlFlow {

    val wasFirstPipe = check(TokenType.Pipe)
    var x = 1 // because first is pipe already matched outside of this function
    do {
        if (check(TokenType.ReturnArrow, x)) {
            peek(x).compileError("-> detected, but => expected")
        }
        // | x > 5 ^ =>
        if (check(TokenType.Then, x)) {
            return ifStatementOrExpression()
        }
        // one-line switch
        if (wasFirstPipe && check(TokenType.Pipe, x)) {
            return switchStatementOrExpression()
        }
        x++
    } while (!check(TokenType.EndOfLine, x))
    // | x > 6 =>
    // 1 echo


    // many line switch
    // | x
    // | 5 => ...
    return switchStatementOrExpression()
}

fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {
        step() // skip Pipe
        val ifExpression = expression()

        matchAssert(TokenType.Then, "\"=>\" expected, but found ${getCurrentToken().lexeme}")
        var (body, isSingleExpression) = methodBody(true)
        if (body[0] is ReturnStatement) isSingleExpression = false

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
    skipNewLinesAndComments()
    val otherPart = ifStatementOrExpression()

    val result = ControlFlow.Switch(
        switch = switchExpression,
        iF = otherPart
    )
    return result

}
