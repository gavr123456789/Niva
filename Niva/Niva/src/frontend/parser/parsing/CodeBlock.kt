package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.resolver.Resolver
import main.RESET
import main.WHITE

fun Parser.statementsUntilCloseBracket(bracketType: TokenType): List<Statement> {
    val result = mutableListOf<Statement>()
    do {
        result.add(statementWithEndLine())
    } while (!match(bracketType))

    return result
}

// returns defaultAction = []
fun Parser.statementsUntilCloseBracketWithDefaultAction(bracketType: TokenType): Pair<MutableList<Statement>, CodeBlock?> {
    val result = mutableListOf<Statement>()
    var defaultAction: CodeBlock? = null
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


fun Parser.codeBlockArgs(): List<IdentifierExpr> {

    val isThereBeforeStatementPart =
        checkMany(TokenType.Identifier, TokenType.DoubleColon) ||
        checkMany(TokenType.Identifier, TokenType.Comma) ||
        checkMany(TokenType.Identifier, TokenType.ReturnArrow)


    //checkTokUntilEndOfLine(TokenType.ReturnArrow)
    // [{a, b -> } statements]
    return if (isThereBeforeStatementPart)
            beforeStatementsPart()
        else
            listOf()
}

fun Parser.beforeStatementsPart(): List<IdentifierExpr> {
    val result = mutableListOf<IdentifierExpr>()

    // ^a, b, c ->
    do {
        val identifier = identifierMayBeTyped()
        result.add(identifier)
    } while (match(TokenType.Comma))

    // a, b, c ^->
    matchAssert(TokenType.ReturnArrow, "-> expected inside code block after input list")

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
        listOf()


    val result = CodeBlock(
        inputList = beforeStatementsPart,
        statements = statements,
        type = if (statements.isNotEmpty()) null else Resolver.defaultTypes[InternalTypes.Unit] ,
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
