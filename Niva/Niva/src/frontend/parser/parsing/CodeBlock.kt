package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.CodeBlock
import frontend.parser.types.ast.ExpressionInBrackets
import frontend.parser.types.ast.IdentifierExpr
import frontend.parser.types.ast.Statement

fun Parser.statementsUntilCloseBracket(bracketType: TokenType): List<Statement> {
    val result = mutableListOf<Statement>()
    do {
        result.add(statementWithEndLine())
    } while (!match(bracketType))

    return result
}

fun Parser.codeBlock(): CodeBlock {


    fun beforeStatementsPart(): List<IdentifierExpr> {
        val result = mutableListOf<IdentifierExpr>()

        // a, b, c
        do {
            val identifier = identifierMayBeTyped()
            result.add(identifier)
        } while (match(TokenType.Comma))

        matchAssert(TokenType.ReturnArrow, "-> expected inside code block after input list")

        return result
    }


    val openBracket = matchAssert(TokenType.OpenBracket, "")

    val isThereBeforeStatementPart =
        check(TokenType.Identifier) && check(TokenType.DoubleColon, 1) ||
                check(TokenType.Identifier) && check(TokenType.Comma, 1) ||
                check(TokenType.Identifier) && check(TokenType.ReturnArrow, 1)


    //checkTokUntilEndOfLine(TokenType.ReturnArrow)
    // [{a, b -> } statements]
    val beforeStatementsPart: List<IdentifierExpr> =
        if (isThereBeforeStatementPart)
            beforeStatementsPart()
        else
            listOf()


    val statements = if (!match(TokenType.CloseBracket)) statementsUntilCloseBracket(TokenType.CloseBracket) else listOf()


    val result = CodeBlock(
        inputList = beforeStatementsPart,
        statements = statements,
        type = null,
        token = openBracket
    )

    return result
}

fun Parser.bracketExpression(): ExpressionInBrackets {
    val openBracket = matchAssert(TokenType.OpenParen)

    val statements = statementsUntilCloseBracket(TokenType.CloseParen)

    val result = ExpressionInBrackets(
        statements = statements,
        type = null,
        token = openBracket
    )

//    val closeBracket = matchAssert(TokenType.CloseBracket)


    return result
}
