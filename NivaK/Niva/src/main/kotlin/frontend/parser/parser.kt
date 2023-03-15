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
    var current: Int = 0,
    val file: String,
    val tokens: MutableList<Token> = mutableListOf(),
    val binaryMessages: MutableSet<String> = hashSetOf(),
    val unaryMessages: MutableSet<String> = hashSetOf(),
    val keywordMessages: MutableSet<String> = hashSetOf(),
    val currentFunction: Declaration? = null,
    val scopeDepth: Int = 0,
//    val operators: OperatorTable,
    val tree: MutableList<Declaration> = mutableListOf(),
    val lines: MutableList<Position> = mutableListOf(),
    val source: String,
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

fun Parser.error(message: String, token: Token? = null) {
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

fun Parser.check(kind: Iterable<String>): Boolean {
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
fun Parser.match(kind: Iterable<String>): Boolean {
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





fun Parser.addBinaryMessage(lexeme: String) {
    binaryMessages.add(lexeme)
}

fun Parser.addUnaryMessage(lexeme: String) {
    unaryMessages.add(lexeme)
}

fun Parser.addKeywordMessage(lexeme: String) {
    keywordMessages.add(lexeme)
}

