package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.parser.types.ast.CodeBlock
import frontend.parser.types.ast.IdentifierExpr
import frontend.parser.types.ast.Statement
import frontend.util.checkTokUntilEndOfLine

fun Parser.codeBlock(): CodeBlock {
    fun statementsUntilCloseBracket(): List<Statement> {
        val result = mutableListOf<Statement>()
        do {
            result.add(statementWithEndLine())
        } while (!match(TokenType.CloseBracket))

        return result
    }

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

    val isThereBeforeStatementPart = checkTokUntilEndOfLine(TokenType.ReturnArrow)
    // [{a, b -> } statements]
    val beforeStatementsPart: List<IdentifierExpr> =
        if (isThereBeforeStatementPart)
            beforeStatementsPart()
        else
            listOf()


    val statements = statementsUntilCloseBracket()


    val result = CodeBlock(
        inputList = beforeStatementsPart,
        statements = statements,
        type = null,
        token = openBracket
    )
    return result
}
