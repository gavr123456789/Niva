package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.ControlFlow
import frontend.parser.types.ast.Expression
import frontend.parser.types.ast.IfBranch

fun Parser.ifOrSwitch(isExpression: Boolean): ControlFlow {

    val wasFirstPipe = check(TokenType.Pipe)
    var wasSecondPipe = false
    var x = 1 // because first is pipe already
    do {
        if (check(TokenType.Then, x)) {
            return ifStatementOrExpression(isExpression)
        }
        // oneline switch
        if (wasFirstPipe && check(TokenType.Pipe, x)) {
            return switchStatementOrExpression(isExpression)
        }
        x++
    } while (!check(TokenType.EndOfLine, x))
    return switchStatementOrExpression(isExpression)
}

fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {

        step() // skip Pipe
        val messageCall = messageCall()

        matchAssert(TokenType.Then, "\"=>\" expected")
        val (body, isSingleExpression) = methodBody()

        result.add(
            if (isSingleExpression) {
                IfBranch.IfBranchSingleExpr(
                    ifExpression = messageCall,
                    body[0] as Expression
                )
            } else {
                IfBranch.IfBranchWithBody(
                    ifExpression = messageCall,
                    body = body
                )
            }
        )


        match(TokenType.EndOfLine)
        match(TokenType.EndOfFile)
    } while (check(TokenType.Pipe))

    return result
}

fun Parser.ifStatementOrExpression(isExpression: Boolean): ControlFlow.If {

    val pipeTok = peek()
    val ifBranches = ifBranches()

    val elseBranch = if (match(TokenType.Else)) {
        methodBody().first.toList()
    } else null


    val result = if (isExpression) {
        if (elseBranch == null) {
            error("else branch is required in control flow expression")
        }
        ControlFlow.IfExpression(
            type = null,
            branches = ifBranches,
            elseBranch = elseBranch,
            token = pipeTok
        )
    } else {
        ControlFlow.IfStatement(
            type = null,
            branches = ifBranches,
            elseBranch = elseBranch,
            token = pipeTok
        )
    }

    return result
}

fun Parser.switchStatementOrExpression(isExpression: Boolean): ControlFlow.Switch {
    val pipeTok = matchAssert(TokenType.Pipe, "")

    val switchExpression = messageCall()

    match(TokenType.EndOfLine)
    val otherPart = ifStatementOrExpression(isExpression)


    return if (isExpression) {
        val result = ControlFlow.SwitchExpression(
            switch = switchExpression,
            iF = otherPart
        )
        result
    } else {
        val result = ControlFlow.SwitchStatement(
            switch = switchExpression,
            iF = otherPart
        )
        result
    }
}
