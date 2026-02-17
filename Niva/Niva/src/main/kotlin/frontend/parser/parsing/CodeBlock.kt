package frontend.parser.parsing

import main.utils.RESET
import main.utils.WHITE
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*

private fun Parser.statementsUntilCloseBracket(bracketType: TokenType, parseMsgDecls: Boolean = true): List<Statement> {
    val result = mutableListOf<Statement>()
//    do {
//        val (a, _) = methodBody()
//        result.addAll(a)
//        result.add(statementWithEndLine(parseMsgDecls))
//    } while (!match(bracketType))

    while (!match(TokenType.CloseBracket)) {
        result.add(statementWithEndLine(parseMsgDecls = false))
    }

    return result
}

// returns defaultAction = []
fun Parser.statementsUntilCloseBracketWithDefaultAction(bracketType: TokenType): Pair<MutableList<Statement>, CodeBlock?> {
    val result = mutableListOf<Statement>()
    var defaultAction: CodeBlock? = null
    if (match(bracketType))
        return Pair(mutableListOf(), null)
    do {
        val q = statementWithEndLine()
        result.add(q)
        if (q is VarDeclaration && q.name == "defaultAction") {
            if (defaultAction != null) {
                q.token.compileError("${WHITE}defaultAction$RESET already defined")
            }
            val value = q.value
            if (value is CodeBlock) {
                defaultAction = value
            } else {
                q.token.compileError("Value of ${WHITE}defaultAction$RESET must be codeblock")
            }
        }
    } while (!match(bracketType))

    return Pair(result, defaultAction)
}


private fun Parser.codeBlockArgs(): List<IdentifierExpr> {
    val isThereBeforeStatementPart =
        checkMany(TokenType.Identifier, TokenType.DoubleColon) ||
        checkMany(TokenType.Identifier, TokenType.Colon) ||
        checkMany(TokenType.Identifier, TokenType.Comma) ||
        checkMany(TokenType.Identifier, TokenType.ReturnArrow)


    //checkTokUntilEndOfLine(TokenType.ReturnArrow)
    // [{a, b -> } statements]
    return if (isThereBeforeStatementPart)
            beforeStatementsPart()
        else
            emptyList()
}

private fun Parser.beforeStatementsPart(): List<IdentifierExpr> {
    val result = mutableListOf<IdentifierExpr>()

    // ^a: Int, b: String, c: Bool ->
    do {
        val identifier = identifierMayBeTyped(useSingleColumn = true)
        result.add(identifier)
    } while (match(TokenType.Comma) || check(TokenType.Identifier))

    // a, b, c ^->
    matchAssert(TokenType.ReturnArrow, "expected statements after codeblock's arg list but found: ${peek().lexeme}")

    return result
}

fun Parser.codeBlock(): CodeBlock {
    val openBracket = matchAssert(TokenType.OpenBracket, "")
    // if we skip line here then typed args may be wrongly parsed as codeblock args

    // [{a, b -> } statements]
    val beforeStatementsPart: List<IdentifierExpr> =
        codeBlockArgs()

    skipNewLinesAndComments()

    val statements = if (!match(TokenType.CloseBracket))
        statementsUntilCloseBracket(TokenType.CloseBracket)
    else
        emptyList()


    val result = CodeBlock(
        inputList = beforeStatementsPart,
        statements = statements,
        token = openBracket,
    )

    return result
}

fun Parser.bracketExpression(): ExpressionInBrackets {
    val openBracket = matchAssert(TokenType.OpenParen)

    val statements = statementsUntilCloseBracket(TokenType.CloseParen, false)
    if (statements.count() != 1) openBracket.compileError("Expected one expression in brackets but got ${statements.count()}")
    val expr = statements.first()
    if (expr !is Expression) openBracket.compileError("Expected expression, not a statement")
    val result = ExpressionInBrackets(
        expr = expr,
        type = null,
        token = openBracket
    )

//    val closeBracket = matchAssert(TokenType.CloseBracket)

    return result
}
