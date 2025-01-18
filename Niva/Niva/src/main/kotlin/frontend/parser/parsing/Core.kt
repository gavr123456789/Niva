package frontend.parser.parsing

import main.frontend.meta.*
import main.frontend.parser.types.ast.ListCollection
import main.frontend.parser.types.ast.Statement
import java.io.File

class Parser(
    val file: File,
    val tokens: MutableList<Token>,
    val source: String,
    val tree: MutableList<Statement> = mutableListOf(),
    var current: Int = 0,
    var lastListCollection: ListCollection? = null
)

fun Parser.getCurrent() = current
fun Parser.getCurrentToken() =
    if (getCurrent() >= tokens.size - 1 || getCurrent() - 1 < 0)
        tokens.elementAt(tokens.size - 1)
    else
        tokens.elementAt(current - 1)

fun endOfFile(file: File) = Token(
    kind = TokenType.EndOfFile,
    lexeme = "",
    line = -1,
    pos = Position(-1, -1),
    relPos = Position(-1, -1),
    file = file
)

fun Parser.peek(distance: Int = 0): Token =
    // check
    if (tokens.size == 0 || current + distance > tokens.size - 1 || current + distance < 0)
        endOfFile(file)
    else
        tokens[current + distance]

fun Parser.done(): Boolean =
    check(TokenType.EndOfFile)

fun Parser.step(n: Int = 1): Token {
    val result =
        if (done())
            peek()
        else
            tokens[current]
    current += n
    if (current > this.tokens.count()) {
        throw Exception("Reached out of tokens somehow")
    }
    return result
}

@Suppress("UnusedReceiverParameter")
fun Parser.error(message: String): Nothing {
//    var realToken = token ?: getCurrentToken()
//    if (realToken.kind == TokenType.EndOfFile) {
//        realToken = peek(-1)
//    }
    peek().compileError(message)
}

fun Parser.check(kind: TokenType, distance: Int = 0) =
    peek(distance).kind == kind


fun Parser.checkIdentifier(distance: Int = 0) =
    peek(distance).kind.let { it == TokenType.Identifier || it == TokenType.NullableIdentifier}


fun Parser.check(kind: String, distance: Int = 0) =
    peek(distance).lexeme == kind

fun Parser.checkMany(vararg kind: TokenType, distance: Int = 0): Boolean {
    kind.forEachIndexed { i, it ->
        if (!check(it, i + distance)) {
            return false
        }
    }
    return true
}


//fun Parser.checkString(kind: Iterable<String>): Boolean {
//    kind.forEach {
//        if (check(it)) {
//            step()
//            return true
//        }
//    }
//    return false
//}

fun Parser.match(kind: TokenType) =
    if (check(kind)) {
        step()
        true
    } else {
        false
    }

// skip lines and comments, and then match token
fun Parser.matchAfterSkip(kind: TokenType): Boolean {
    val savePoint = current
    skipNewLinesAndComments()

    return if (match(kind)) {
        true
    } else {
        current = savePoint
        false
    }
}

fun Parser.checkAfterSkip(kind: TokenType): Boolean {
    val savePoint = current
    skipNewLinesAndComments()

    return if (check(kind)) {
        true
    } else {
        current = savePoint
        false
    }
}

//fun Parser.checkIdentifier(): Boolean {
//    val tok = peek()
//    return tok.isIdentifier()
//}

fun Parser.matchAssertAnyIdent(errorMessage: String): Token {
    val tok = peek()

    return if (tok.isIdentifier()) {
        step()
        tok
    } else {
        peek(-1).compileError(errorMessage)
    }
}

fun Parser.matchAssert(kind: TokenType, errorMessage: String? = null): Token {
    val tok = peek()

    val realErrorMessage = errorMessage
        ?: "Parsing error, ${kind.name} expected, but found \"${tok.lexeme}\""

    return if (tok.kind == kind) {
        step()
        tok
    } else {
        peek(-1).compileError(realErrorMessage)
    }
}


fun Parser.matchAssertOr(vararg kinds: TokenType, errorMessage: String? = null): Token {
    val tok = peek()

    val realErrorMessage = errorMessage
        ?: "Parsing error, one of ${kinds.map { it.name }} expected, but found \"${tok.lexeme}\""

    val result = kinds.find { it == tok.kind }

    if (result == null) {
        peek(-1).compileError(realErrorMessage)
    } else {
        step()
        return tok
    }

}

fun Parser.match(kind: String) =
    if (check(kind)) {
        step() // TODO тут наверн надо делать степ на kind.length
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

//fun Parser.matchString(kind: Iterable<String>): Boolean {
//    kind.forEach {
//        if (match(it)) {
//            return true
//        }
//    }
//    return false
//}
//
//fun Parser.expect(kind: TokenType, message: String = "", token: Token? = null) {
//    if (!match(kind)) {
//        if (message.isEmpty()) {
//            error("expecting token of kind $kind, found ${peek().kind}")
//        } else {
//            error(message)
//        }
//    }
//}
//
//fun Parser.expect(kind: String, message: String = "", token: Token? = null) {
//    if (!match(kind)) {
//        if (message.isEmpty()) {
//            error("expecting token of kind $kind, found ${peek().kind}")
//        } else {
//            error(message)
//        }
//    }
//}
