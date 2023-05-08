package frontend.parser

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType

// Unari messages
//class OperatorTable (
//    val tokens: List<String> =
//)

data class Module(val name: String, var loaded: Boolean)

class Parser(
    val file: String,
    val tokens: MutableList<Token>,
    val source: String,
//    val lines: MutableList<Position>,

//    val binaryMessages: MutableSet<String> = hashSetOf(),
//    val unaryMessages: MutableSet<String> = hashSetOf(),
//    val keywordMessages: MutableSet<String> = hashSetOf(),
    val currentFunction: Declaration? = null,
    val scopeDepth: Int = 0,
//    val operators: OperatorTable,
    val tree: MutableList<Declaration> = mutableListOf(),
    var current: Int = 0,
    val modules: MutableList<Module> = mutableListOf(),
)


fun Parser.getCurrent() = current
fun Parser.getCurrentToken() =
    if (getCurrent() >= tokens.size - 1 || getCurrent() - 1 < 0)
        tokens.elementAt(tokens.size - 1)
    else
        tokens.elementAt(current - 1)

fun Parser.getSource() = source
fun Parser.getCurrentFunction() = currentFunction
fun endOfFile() = Token(
    kind = TokenType.EndOfFile,
    lexeme = "",
    line = -1,
    pos = Position(-1, -1),
    relPos = Position(-1, -1)
)
//fun endOfLine(msg: String, tok: Token? = null) = expect()


fun Parser.peek(distance: Int = 0): Token =
    // check
    if (tokens.size == 0 || current + distance > tokens.size - 1 || current + distance < 0)
        endOfFile()
    else
        tokens[current + distance]

fun Parser.done(): Boolean =
    peek().kind == TokenType.EndOfFile

fun Parser.step(n: Int = 1): Token {
    val result =
        if (done())
            peek()
        else
            tokens[current]
    current++
    return result
}

fun Parser.error(message: String, token: Token? = null): Nothing {
    var realToken = if (token == null) getCurrentToken() else token
    if (realToken.kind == TokenType.EndOfFile) {
        realToken = peek(-1)
    }
    throw Error("$message\ntoken: $token\nline: ${realToken.line}\nfile: $file\nparser: $this")
}

fun Parser.check(kind: TokenType, distance: Int = 0) =
    peek(distance).kind == kind

fun Parser.check(kind: String, distance: Int = 0) =
    peek(distance).lexeme == kind

fun Parser.check(kind: Iterable<TokenType>): Boolean {
    kind.forEach {
        if (check(it)) {
            return true
        }
    }
    return false
}

fun Parser.checkString(kind: Iterable<String>): Boolean {
    kind.forEach {
        if (check(it)) {
            step()
            return true
        }
    }
    return false
}

fun Parser.match(kind: TokenType) =
    if (check(kind)) {
        step()
        true
    } else {
        false
    }

fun Parser.match(kind: String) =
    if (check(kind)) {
        step()
        true
    } else {
        false
    }

fun Parser.match(kind: Iterable<TokenType>): Boolean {
    kind.forEach {
        if (match(it)) {
            return true
        }
    }
    return false
}

fun Parser.matchString(kind: Iterable<String>): Boolean {
    kind.forEach {
        if (match(it)) {
            return true
        }
    }
    return false
}

fun Parser.expect(kind: TokenType, message: String = "", token: Token? = null) {
    if (!match(kind)) {
        if (message.isEmpty()) {
            error("expecting token of kind $kind, found ${peek().kind}", token)
        } else {
            error(message)
        }
    }
}

fun Parser.expect(kind: String, message: String = "", token: Token? = null) {
    if (!match(kind)) {
        if (message.isEmpty()) {
            error("expecting token of kind $kind, found ${peek().kind}", token)
        } else {
            error(message)
        }
    }
}
//fun Parser.expect(kind: Iterable<String>, message: String = "", token: Token? = null) {
//
//}

fun Parser.primary(): Expression {
    val x = this.peek().kind
    when (x) {
        TokenType.True -> TODO()
        TokenType.False -> TODO()
        TokenType.Integer -> return LiteralExpression.IntExpr(file, step())
        TokenType.Float -> TODO()
        TokenType.StringToken -> TODO()
        TokenType.Identifier -> TODO()
        TokenType.LeftParen -> TODO()
        else -> this.error("expected primary, but got $x")
    }
}


fun Parser.expression(): Expression {
    // пока токо инты
    if (peek().kind == TokenType.Integer) {
        return primary()
    } else {
        TODO()
    }
}

fun Parser.varDeclaration(): Declaration {
    val tok = this.peek()
    this.expect(TokenType.Identifier, "")
    val identifierExpr = IdentifierExpr(file, tok, tok, 0)
    val value: Expression
    if (this.match("=")) {
        value = this.expression()
    } else {
        error("!!")
    }

    val result = VarDeclaration(file, tok, identifierExpr, value)
    return result
}


fun Parser.declaration(): Declaration {
    val x = peek().kind
    when (x) {
        TokenType.Identifier -> {
            // x = 1
            return this.varDeclaration()
        }

        TokenType.Type -> TODO()
        TokenType.Union -> TODO()
        TokenType.Use -> TODO()
        TokenType.Return -> TODO()
        TokenType.Pragma -> TODO()
        else -> TODO()
    }
}

fun Parser.parse(): List<Declaration> {

    while (!this.done()) {
        this.tree.add(this.declaration())
    }

    return this.tree
}

//fun Parser.addBinaryMessage(lexeme: String) {
//    binaryMessages.add(lexeme)
//}
//
//fun Parser.addUnaryMessage(lexeme: String) {
//    unaryMessages.add(lexeme)
//}
//
//fun Parser.addKeywordMessage(lexeme: String) {
//    keywordMessages.add(lexeme)
//}

