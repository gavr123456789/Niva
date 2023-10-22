package frontend.parser.parsing

import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.meta.isIdentifier
import frontend.parser.types.ast.*

data class Module(val name: String, var loaded: Boolean)

fun Parser.identifierMayBeTyped(): IdentifierExpr {
    val x = step()
    val dotMatched = match(TokenType.Dot)
    val listOfIdentifiersPath = mutableListOf<String>(x.lexeme)
    if (dotMatched) {
        do {
            val q = matchAssert(TokenType.Identifier, "Identifier expected after dot")
            listOfIdentifiersPath.add(q.lexeme)
        } while (match(TokenType.Dot))
    }


    val isTyped = check(TokenType.DoubleColon)
    return if (isTyped) {
        step() // skip double colon
        val type = parseType()
        IdentifierExpr(listOfIdentifiersPath.last(), listOfIdentifiersPath, type, x)
    } else {
        IdentifierExpr(listOfIdentifiersPath.last(), listOfIdentifiersPath, null, x) // look for type in table
    }
}

fun Parser.primary(): Primary? =
    when (peek().kind) {
        TokenType.True -> LiteralExpression.TrueExpr(step())
        TokenType.False -> LiteralExpression.FalseExpr(step())
        TokenType.Integer -> LiteralExpression.IntExpr(step())
        TokenType.Float -> LiteralExpression.FloatExpr(step())
        TokenType.String -> LiteralExpression.StringExpr(step())
        TokenType.Identifier, TokenType.NullableIdentifier -> identifierMayBeTyped()

//        TokenType.OpenParen -> TODO()
//        TokenType.OpenBraceHash -> TODO() // set or map
//        TokenType.OpenParenHash -> TODO() // ?
        else -> null
    }


fun Parser.varDeclaration(): VarDeclaration {
    // skip mut
    val isMutable = check(TokenType.Mut)
    if (isMutable) {
        step()
    }

    val tok = this.step()
    val typeOrEqual = step()

    val value: Expression
    val valueType: TypeAST?
    when (typeOrEqual.kind) {
        TokenType.Assign -> {
            val isNextReceiver = isNextSimpleReceiver()
            value = if (isNextReceiver) simpleReceiver() else expression()
            valueType = null
        }
        // ::^int
        TokenType.DoubleColon -> {
            valueType = parseType()
            // x::int^ =
            match(TokenType.Assign)
            value = this.simpleReceiver()
        }

        else -> error("after ${peek(-1)} needed type or expression")
    }

    val result = VarDeclaration(tok, tok.lexeme, value, valueType, isMutable)
    return result
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
        TokenType.String -> true

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
        if (check(TokenType.EndOfLine, 1) && check(TokenType.Identifier, 2)) {
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


// message or control flow
// inside x from: y to: z
// we don't have to parse y to: z as new keyword, only y expression
fun Parser.expression(dontParseKeywordsAndUnaryNewLines: Boolean = false): Expression {
    if (check(TokenType.Pipe)) {
        return ifOrSwitch()
    }

    val messageSend = messageSend(dontParseKeywordsAndUnaryNewLines)
    // unwrap unnecessary MessageSend
    return if (messageSend.messages.isEmpty() && messageSend is MessageSendUnary) {
        messageSend.receiver
    } else {
        messageSend
    }
}


// Declaration without end of line
fun Parser.statement(): Statement {
    val tok = peek()
    val kind = tok.kind
    // Checks for declarations that starts from keyword like type/fn

    if ((tok.isIdentifier() && (check(TokenType.DoubleColon, 1) || check(TokenType.Assign, 1)) || kind == TokenType.Mut)
    ) {
        return varDeclaration()
    }
    if (kind == TokenType.Type) {
        return typeDeclaration()
    }
    if (kind == TokenType.Alias) {
        return typeAliasDeclaration()
    }
    if (kind == TokenType.Union) {
        return unionDeclaration()
    }
    if (kind == TokenType.Constructor) {
        return constructorDeclaration()
    }

    if (tok.isIdentifier() && check(TokenType.AssignArrow, 1)) {
        return assignVariableNewValue()
    }
    val isInlineReplWithNum = tok.kind == TokenType.InlineReplWithNum
    if (tok.lexeme == ">" || isInlineReplWithNum) {
        val q = step()
        val w = statement()
        if (w is Expression) {
            w.isInlineRepl = true
            if (isInlineReplWithNum)
                w.inlineReplCounter = tok.lexeme.substring(1).toInt()
            return w
        } else {
            q.compileError("> can only be used with expressions")
        }
    }

    if (kind == TokenType.Return) {
        val returnTok = step()
        val expression = expression()
        return ReturnStatement(
            expression = expression,
            token = returnTok,
        )
    }

    if (kind == TokenType.EndOfFile) {
        tok.compileError("File contains only comments, nothing to compile")
    }


    val isItKeywordDeclaration = checkTypeOfMessageDeclaration()
    if (isItKeywordDeclaration != null) {
        return messageDeclaration(isItKeywordDeclaration)
    }



    return expression()
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
