@file:Suppress("ControlFlowWithEmptyBody")

package frontend.parser.parsing

import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.meta.isIdentifier
import frontend.parser.types.ast.*
import main.frontend.parser.parsing.parseType
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.parsing.staticBuilder
import main.frontend.parser.parsing.varDeclaration


// Declaration without end of line
fun Parser.statement(): Statement {
    val pragmas = if (check("@")) codeAttributes() else mutableListOf()
    val tok = peek()
    val kind = tok.kind


    // List::Int sas = [] - unary
    // x::[Int->Int] = [] - value
    // x::Int = 5

    if (kind == TokenType.Dot) {
        return expression(dot = true)
    }

    if (tok.isIdentifier() && tok.lexeme[0].isLowerCase() &&
        (check(TokenType.Assign, 1) || check(TokenType.DoubleColon, 1))
        || kind == TokenType.Mut
    ) {
        return varDeclaration()
    }
    if (kind == TokenType.Type) {
        return typeDeclaration(pragmas)
    }
    if (kind == TokenType.Enum) {
        return enumDeclaration(pragmas)
    }
//    if (kind == TokenType.Alias) {
//        return typeAliasDeclaration()
//    }
    if (kind == TokenType.Union) {
        return unionDeclaration(pragmas)
    }

    if (kind == TokenType.Constructor) {
        return constructorDeclaration(pragmas)
    }

    if (kind == TokenType.Builder) {
        return builderDeclaration(pragmas)
    }

    if (kind == TokenType.Identifier && tok.lexeme == "extend") {
        return extendDeclaration(pragmas)
    }

    if (tok.isIdentifier() && check(TokenType.AssignArrow, 1)) {
        return assignVariableNewValue()
    }


    val isInlineReplWithNum = kind == TokenType.InlineReplWithNum
    val isInlineReplWithQuestion = kind == TokenType.InlineReplWithQuestion

    if (tok.lexeme == ">" || isInlineReplWithNum || isInlineReplWithQuestion) {
        val inlineTok = step()
        try {
            val inlineExpr = expression(true)

            inlineExpr.isInlineRepl = true
            if (isInlineReplWithNum)
                inlineExpr.inlineReplCounter = tok.lexeme.substring(1).toInt()
            else if (isInlineReplWithQuestion) {
                inlineExpr.isInfoRepl = true
            }


            return inlineExpr

        } catch (_:Exception) {
            inlineTok.compileError("> can only be used with expressions")
        }

    }

    if (kind == TokenType.Return) {
        val returnTok = step()
        val expression = if (checkEndOfLineOrFile()) null else expression(parseSingleIf = true)
        return ReturnStatement(
            expression = expression,
            token = returnTok,
        )
    }

    if (kind == TokenType.EndOfFile) {
        tok.compileError("Nothing to compile :(")
    }

    val isItMsgDeclaration = checkTypeOfMessageDeclaration2()
    if (isItMsgDeclaration != null) {
        return messageDeclaration(isItMsgDeclaration, pragmas)
    }

    skipNewLinesAndComments()
    return expression(parseSingleIf = true)
}

fun Parser.dotSeparatedIdentifiers(): IdentifierExpr? {
    val x = step()
    if (x.kind != TokenType.Identifier) {
        return null
    }
    val dotMatched = match(TokenType.Dot)
    val listOfIdentifiersPath = mutableListOf(x.lexeme)
    if (dotMatched) {
        do {
            val q = matchAssert(TokenType.Identifier, "Identifier expected after dot, but found ${peek().lexeme}")
            listOfIdentifiersPath.add(q.lexeme)
        } while (match(TokenType.Dot))
    }

    return IdentifierExpr(listOfIdentifiersPath.last(), listOfIdentifiersPath, null, x)

}

// if inside var decl with type, then we're getting type from it
fun Parser.identifierMayBeTyped(typeAST: TypeAST? = null): IdentifierExpr {
    val x = step()
    val dotMatched = match(TokenType.Dot)
    val listOfIdentifiersPath = mutableListOf(x.lexeme)
    if (dotMatched) {
        do {
            val q = matchAssert(TokenType.Identifier, "Identifier expected after dot, but found ${peek().lexeme}")
            listOfIdentifiersPath.add(q.lexeme)
        } while (match(TokenType.Dot))
    }


    val isTyped = match(TokenType.DoubleColon)
    return if (isTyped) {
        val type = parseType()
        IdentifierExpr(listOfIdentifiersPath.last(), listOfIdentifiersPath, type, x)
    } else {
        IdentifierExpr(listOfIdentifiersPath.last(), listOfIdentifiersPath, typeAST, x) // look for type in table
    }
}

fun Parser.primary(typeAST: TypeAST? = null): Primary? =

    when (peek().kind) {
        TokenType.True -> LiteralExpression.TrueExpr(step())
        TokenType.False -> LiteralExpression.FalseExpr(step())
        TokenType.Null -> LiteralExpression.NullExpr(typeAST ?: TypeAST.InternalType(InternalTypes.Any, peek()), step())
        TokenType.Integer -> LiteralExpression.IntExpr(step())
        TokenType.Float -> LiteralExpression.FloatExpr(step())
        TokenType.Double -> LiteralExpression.DoubleExpr(step())
        TokenType.String -> LiteralExpression.StringExpr(step())
        TokenType.Char -> LiteralExpression.CharExpr(step())

        TokenType.Identifier, TokenType.NullableIdentifier -> identifierMayBeTyped(typeAST)
        else -> null
    }


fun Parser.assignVariableNewValue(): Assign {
    // x <- expression
    val identTok = this.step()
    matchAssert(TokenType.AssignArrow)

    val isNextReceiver = isNextSimpleReceiver()
    val value = if (isNextReceiver) simpleReceiver() else expression()


    val result = Assign(identTok, identTok.lexeme, value)

    return result

}


fun Token.isPrimaryToken(): Boolean =
    when (kind) {
        TokenType.Identifier,
        TokenType.True,
        TokenType.False,
        TokenType.Integer,
        TokenType.Float,
        TokenType.Double,
        TokenType.Char,
        TokenType.String,
        -> true

        else -> false
    }

// checks is next thing is receiver
// needed for var declaration to know what to parse - message or value
fun Parser.isNextSimpleReceiver(): Boolean {
    val savepoint = current
    if (peek().isPrimaryToken()) {
        // x = 1
        // from: 0
        // to: 3
        if (check(TokenType.EndOfLine, 1) && check(TokenType.Identifier, 2) && check(TokenType.Colon, 3)) {
            identifierMayBeTyped()
            skipNewLinesAndComments()
            if (check(TokenType.Identifier) && check(TokenType.Colon, 1)) {
                current = savepoint
                return false
            }
        }
        when {

            // x = 1
            check(TokenType.EndOfLine, 1) || check(TokenType.EndOfFile, 1) -> return true
            // x = [code]
            check(TokenType.OpenBracket, 1) -> return true
            check(TokenType.OpenParen, 1) -> return true
            check(TokenType.OpenBrace, 1) -> return true
        }
    }

    return false
}

fun Parser.commaSeparatedExpressions(): List<Expression> {
    val result = mutableListOf<Expression>()
    do {
        result.add(expression())
    }while (match(TokenType.Comma))
    return result
}
// message or control flow or static builder
// inside x from: y to: z
// we don't have to parse y to: z as new keyword, only y expression
fun Parser.expression(
    dontParseKeywordsAndUnaryNewLines: Boolean = false, // true if it's a keyword argument
    dot: Boolean = false,
    parseSingleIf: Boolean = false // TODO replace on checking root, make root always required
): Expression {

    if (check(TokenType.Pipe)) {
        return switchStatementOrExpression()
    }

    if (check(TokenType.Underscore)) {
        return ifStatementOrExpression()
    }

    if (checkMany(TokenType.Identifier, TokenType.OpenBracket)) {
        return staticBuilder()
    }



    val messageSend = messageSend(dontParseKeywordsAndUnaryNewLines, dot)
    // unwrap unnecessary MessageSend
    val unwrapped = if (messageSend.messages.isEmpty() && messageSend is MessageSendUnary) {
        messageSend.receiver
    } else {
        messageSend
    }

    // x > 5 ^ => ...
    if (parseSingleIf && match(TokenType.Then)) {
        var codeBlock: CodeBlock? = null
        var singleExpr: Statement? = null
        if (check(TokenType.OpenBracket)) {
            codeBlock = codeBlock()
        } else {
            singleExpr = statementWithEndLine()
        }

        val isSingleExpression = singleExpr is Expression


        skipNewLinesAndComments()
        val elseBranch = if (match(TokenType.Else)) {
            methodBody(true).first.toList()
        } else null

        val singleIf = ControlFlow.If(
            type = null,
            ifBranches = listOf(
                if (isSingleExpression) {

                    IfBranch.IfBranchSingleExpr(
                        ifExpression = unwrapped,
                        thenDoExpression = singleExpr,
                        listOf()
                    )
                }
                else {
                    // this single expression is statement
                    val body = if(singleExpr != null) {
                        // codeBlock With single expr
                        CodeBlock(
                            inputList = listOf(),
                            statements = listOf(singleExpr),
                            type = null,
                            token = singleExpr.token
                        )
                    } else {
                        codeBlock!!
                    }
                    IfBranch.IfBranchWithBody(
                        ifExpression = unwrapped,
                        body = body,
                        listOf()
                    )
                }
            ),
            kind = ControlFlowKind.Statement,
            elseBranch = elseBranch,
            token = unwrapped.token,
        )

        return singleIf
    }

    return unwrapped
}

class CodeAttribute(
    val name: String,
    val value: Primary
)


fun Parser.codeAttributes(): MutableList<CodeAttribute> {
    val codeAttributes: MutableList<CodeAttribute> = mutableListOf()
    step()
    do {
        val name = step()
        matchAssert(TokenType.Colon)
//        step() // skip colon
        val value = primary() ?: name.compileError("Inside code attribute after : value expected")

        codeAttributes.add(
            CodeAttribute(
                name = name.lexeme,
                value = value
            )
        )

    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))
    skipOneEndOfLineOrFile()
    return codeAttributes
}


fun Parser.statementWithEndLine(): Statement {
    skipNewLinesAndComments()
    val result = this.statement()
    skipNewLinesAndComments()

    return result
}

fun Parser.statements(): List<Statement> {

    while (!this.done()) {
        this.tree.add(this.statementWithEndLine())
    }

    return this.tree
}


fun Parser.checkEndOfLineOrFile(i: Int = 0) =
    check(TokenType.EndOfLine, i) || check(TokenType.EndOfFile, i) || check(TokenType.Comment)


fun Parser.skipOneEndOfLineOrFile() =
    match(TokenType.EndOfLine) || match(TokenType.EndOfFile) || match(TokenType.Comment)


fun Parser.skipNewLinesAndComments() {
    while (match(TokenType.EndOfLine) || match(TokenType.Comment)) {
    }
}
