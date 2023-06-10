package frontend.parser.parsing

import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.isIdentifier
import frontend.parser.types.ast.*

data class Module(val name: String, var loaded: Boolean)


fun Parser.primary(): Primary? =
    when (peek().kind) {
        TokenType.True -> LiteralExpression.TrueExpr(step())
        TokenType.False -> LiteralExpression.FalseExpr(step())
        TokenType.Integer -> LiteralExpression.IntExpr(step())
        TokenType.Float -> LiteralExpression.FloatExpr(step())
        TokenType.String -> LiteralExpression.StringExpr(step())
        TokenType.Identifier, TokenType.NullableIdentifier -> {
            val x = step()
            val isTyped = check(TokenType.DoubleColon)
            if (isTyped) {
                step() // skip double colon
                val type = parseType()
                IdentifierExpr(x.lexeme, type, x)
            } else {
                IdentifierExpr(x.lexeme, null, x) // look for type in table
            }
        }

        TokenType.OpenParen -> TODO()
        TokenType.LeftBraceHash -> TODO() // set or map
        TokenType.LeftParenHash -> TODO() // ?
        else -> null
    }


fun Parser.varDeclaration(): VarDeclaration {

    val tok = this.step()
    val typeOrEqual = step()

    val value: Expression
    val valueType: Type?
    when (typeOrEqual.kind) {
        TokenType.Equal -> {
            val isNextReceiver = isNextReceiver()
            value = if (isNextReceiver) primaryReceiver() else messageOrControlFlow()
            valueType = value.type
        }
        // ::^int
        TokenType.DoubleColon -> {
            valueType = parseType()
            // x::int^ =
            match(TokenType.Equal)
            value = this.primaryReceiver()
        }

        else -> error("after ${peek(-1)} needed type or expression")
    }

    val result = VarDeclaration(tok, tok.lexeme, value, valueType)
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
fun Parser.isNextReceiver(): Boolean {
    if (peek().isPrimaryToken()) {
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


fun Parser.messageOrControlFlow(): Expression {
    if (check(TokenType.Pipe)) {
        val isExpression =
            current != 0 && (check(TokenType.Equal, -1) || check(TokenType.Return, -1))
        return ifOrSwitch(isExpression)
    }

    return messageCall()
}


// Declaration without end of line
fun Parser.statement(): Statement {
    val tok = peek()
    val kind = tok.kind
    // Checks for declarations that starts from keyword like type/fn

    if (tok.isIdentifier() &&
        (check(TokenType.DoubleColon, 1) || check(TokenType.Equal, 1))
    ) {
        return varDeclaration()
    }
    if (kind == TokenType.Type) {
        return typeDeclaration()
    }
    if (kind == TokenType.Union) {
        return unionDeclaration()
    }
    if (kind == TokenType.Constructor) {
        return constructorDeclaration()
    }


    val isItKeywordDeclaration = isItKeywordDeclaration()
    if (isItKeywordDeclaration != null) {
        return messageDeclaration(isItKeywordDeclaration)
    }


    return messageOrControlFlow()
}


fun Parser.statementWithEndLine(): Statement {
    val result = this.statement()
    if (check(TokenType.EndOfLine)) {
        step()
    }
    return result
}

fun Parser.statements(): List<Statement> {

    while (!this.done()) {
        this.tree.add(this.statementWithEndLine())
    }

    return this.tree
}