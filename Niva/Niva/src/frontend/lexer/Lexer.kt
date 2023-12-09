package frontend

import frontend.lexer.isAlphaNumeric
import frontend.lexer.isDigit
import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.util.fillSymbolTable
import java.io.File

typealias StringToToken = HashMap<String, TokenType>

class SymbolTable(var keywords: StringToToken = hashMapOf(), var symbols: StringToToken = hashMapOf())

fun SymbolTable.addSymbol(lexeme: String, tokenType: TokenType) =
    symbols.set(lexeme, tokenType)

fun SymbolTable.existsKeyword(keyword: String) =
    keyword in keywords

fun SymbolTable.getMaxSymbolSize(): Int = symbols.maxOf { it.key.length }


fun SymbolTable.getSymbols(n: Int) =
    symbols.keys.filter { it.length == n }


class Lexer(
    var source: String,
    val file: File
) {
    val symbolTable: SymbolTable = SymbolTable()
    val tokens: MutableList<Token> = mutableListOf()
    var line: Int = 1
    var start: Int = 0
    var current: Int = 0
    val lines: MutableList<Position> = mutableListOf()
    var lastLine: Int = 0
    var linePos: Int = 0
    var lineCurrent: Int = 0
    var spaces: Int = 0

    init {
        fillSymbolTable()

        while (!done()) {
            next()
            start = current
            lineCurrent = linePos
        }

        tokens.add(
            Token(
                kind = TokenType.EndOfFile,
                lexeme = "EOF",
                line = line,
                pos = Position(current, current),
                relPos = Position(0, linePos - 1),
                file = file
            )
        )
        incLine()
    }
}

fun Lexer.lex() = tokens

fun Lexer.done() = current >= source.length

fun Lexer.incLine(needAddNewLineToken: Boolean = false) {
    lines.add(Position(start = lastLine, end = current))
    lastLine = current
    line += 1
    linePos = 0
    if (!done() && needAddNewLineToken && getfirstAfterSpaces() != ".") {
        createToken(TokenType.EndOfLine)
    }
}

fun Lexer.step(n: Int = 1): String =
    buildString {
        while (length < n) {
            if (done() || current > source.lastIndex) {
                break
            } else {
                append(source[current])
            }

            current++
        }
        linePos++
    }

fun Lexer.peek(distance: Int = 0, length: Int = 1): String =
    buildString {
        var i = distance
        while (this.length < length) {
            if (done() || current + i > source.lastIndex || current + i < 0)
                break
            else
                append(source[current + i]) // + 1

            i++
        }
    }

fun Lexer.check(arg: String, distance: Int = 0): Boolean =
    when {
        done() -> false
        else -> {
            val x = peek(distance, arg.length)
            x == arg
        }
    }


fun Lexer.check(args: Array<String>, distance: Int = 0): Boolean {
    for (arg in args) {
        if (check(arg, distance)) {
            return true
        }
    }
    return false
}

fun Lexer.match(s: String): Boolean =
    when {
        !check(s) -> {
            false
        }

        else -> {
            step(s.length)
            true
        }
    }


fun Lexer.match(args: Array<String>): Boolean {


    for (arg in args) {
        if (match(arg)) {
            return true
        }
    }
    return false
}

fun Lexer.createToken(tokenType: TokenType, endPositionMinus: Int = 0, addToLexeme: String? = null) {
    try {
        val lexeme2 = source.slice(start until current)
        val end = current - 1 - endPositionMinus
        val lexeme3 = if (addToLexeme == null) lexeme2 else lexeme2 + addToLexeme
        val lexeme = lexeme3.dropLast(endPositionMinus)
        tokens.add(
            Token(
                kind = tokenType,
                lexeme = lexeme,
                line = line,
                spaces = spaces,
                pos = Position(start = start, end = end),
                relPos = Position(start = linePos - lexeme.lastIndex - 1, end = end),
                file = file
            )
        )
        spaces = 0

    } catch (e: StringIndexOutOfBoundsException) {
        error("cant create token: $tokenType, slice index out of bound: " + e.message + "\n ${source.slice(start until source.length)}")
    }
}

fun Lexer.error(message: String): Nothing {
    val msg = "${message}\nline: ${line}\n$file\npos: $lineCurrent - ${linePos - 1}\n"
    throw Throwable(msg)
}

fun String.set(index: Int, char: Char): String {
    return substring(0, index) + char + substring(index)
}

fun String.set(index: Int, str: String): String {
    return substring(0, index) + str + substring(index)
}

fun Lexer.parseEscape(q: String) {
    source = when (q[0]) {
        'n' -> source.set(current, 'n')
        '\'' -> source.set(current, '\'')
        '\\' -> source.set(current, '\\')
        else -> this.error("invalid escape sequence '\\${peek()}'")
    }
}


fun Lexer.parseString(delimiter: String, mode: String = "single") {
    var slen = 0
    while (!this.check(delimiter) && !this.done()) {

        if (this.match("\n")) {
            if (mode == "multi") {
                this.incLine(false)
            } else {
                this.error("unexpected EOL while parsing string literal")
            }
        }

        if (mode in arrayOf("raw", "multy")) {
            this.step()
        } else if (this.match("\\")) {
            val q = peek()

            source = source.slice(0 until current) + source.slice(current + 1 until source.lastIndex) //..^1
            parseEscape(q)
        }

        if (mode == "format" && match("{")) {
            if (match("{")) {
                source = source.slice(0 until current) + source.slice(current + 1 until source.lastIndex)
                continue
            }
            val x = arrayOf("}", "\"")
            while (!check(x)) {
                step()
            }
            if (check("\"")) {
                error("unclosed '{' in format string")
            }
        } else if (mode == "format" && check("}")) {
            if (check("}", 1)) {
                error("unmatched '}' in format string")
            } else {
                source = source.slice(0 until current) + source.slice(current + 1 until source.lastIndex)
            }
        }
        step()
        slen++
        if (slen > 1 && delimiter == "'") {
            error("invalid character literal (length must be one!)")
        }

    }



    if (mode == "multi") {
        if (!match(delimiter.repeat(3))) {
            error("unexpected EOL while parsing multi-line string literal, $delimiter must be repeated 3 times for multi strings")
        }
    } else if (done() && peek(-1) != delimiter) {
        error("unexpected EOF while parsing string literal")
    } else {
        step()
    }

    if (delimiter == "\"") {
        createToken(TokenType.String)
    } else {
        createToken(TokenType.Char)
    }
}

fun Lexer.getfirstAfterSpaces(): String {
    var x = 0
    while (true) {
        val symbol = peek(x)
        if (symbol == " ") {
            x += 1
        } else {
            return symbol
        }
    }
}

fun Lexer.stepWhileDigit() {
    while (peek().isDigit() && !done()) {
        step()
    }
}

fun Lexer.stepWhileAlphaNumeric() {
    while ((peek().isAlphaNumeric() || check("_")) && !done()) {
        step()
    }
}

fun Lexer.parseNumber() {
    var kind: TokenType = TokenType.Integer
    peek()
    stepWhileDigit()

    // .. operator
    val a = check("..<")
    val b = check("..")
    if (a || b) {
        createToken(TokenType.Integer)
        start += 1
        if (a) match("..<")
        if (b) match("..")

        createToken(TokenType.BinarySymbol)
        return
    }

    if (check(arrayOf("e", "E"))) {
        kind = TokenType.Float
        step()
        stepWhileDigit()

    } else if (check(".")) {
        step()



        if (!peek().isDigit()) {
            error("invalid float number literal")
        }
        kind = TokenType.Float
        stepWhileDigit()
        if (check(arrayOf("e", "E"))) {
            step()
        }
        stepWhileDigit()
    }
    if (match("'")) {
        stepWhileAlphaNumeric()
    }
    // 3.5d == Double
    if (match("d")) {
        kind = TokenType.Double
        createToken(kind, 1) // don't generate this d in kotlin
    } else if (kind == TokenType.Float) {
        createToken(kind, addToLexeme = "f")
    } else
        createToken(kind)

}

fun Lexer.parseIdentifier() {
    stepWhileAlphaNumeric()
    val name = source.slice(start until current)
    if (symbolTable.existsKeyword(name)) {
        symbolTable.keywords[name]?.let { createToken(it) }
    } else {
        if (match("?")) {
            createToken(TokenType.NullableIdentifier)
        } else {
            createToken(TokenType.Identifier)
        }
    }
}


fun Lexer.next() {
    fun Lexer.getToken(lexeme: String): Token? {
        val table = symbolTable
        val kind = table.symbols.getOrDefault(lexeme, table.keywords.getOrDefault(lexeme, TokenType.NoMatch))
        if (kind == TokenType.NoMatch) {
            return null
        }
        val result = Token(
            kind = kind,
            lexeme = source.slice(start until current),
            line = line,
            pos = Position(start, current - 1),
            relPos = Position(linePos - lexeme.lastIndex - 1, linePos - 1),
            spaces = spaces,
            file = file
        )
        spaces = 0
        return result
    }

    val p = peek()
    when {
        done() -> return
        match("\r") -> return
        match(" ") -> {
            spaces++
            start += 2 // 1 to go to space, 2 to skip spase
        }

        match("\t") -> error("tabs are not allowed dud")
        match("\n") -> incLine(true)

        match(arrayOf("\"", "'")) -> {
            var mode = "single"

            // if """ then it must be multiline string
            if (peek(-1) != "'" && check(peek(-1)) && check(peek(-1), 1)) {
                // Multiline strings start with 3 quotes
                step(2)
                mode = "multi"
            }
            parseString(peek(-1), mode)
        }

        // before digit because of floats "3.14"
        match("..<") -> {
            createToken(TokenType.BinarySymbol)
        }

        match("..") -> createToken(TokenType.BinarySymbol)

        p.isDigit() -> {
            step()
            parseNumber()
        }

        check("-") && peek(1).isDigit() -> {
            step()
            step()
            parseNumber()
        }

        // String
        p.isAlphaNumeric() && check(arrayOf("\"", "'"), 1) -> {

            //  Prefixed string literal (i.e. f"Hi {name}!")
            when (step()) {
                "r" -> parseString(step(), "raw")
                "b" -> parseString(step(), "bytes")
                "f" -> parseString(step(), "format")
                else -> error("unknown string prefix '${peek(-1)}'")
            }
        }

        // if elseif chain
        match("_") -> {
            createToken(TokenType.Underscore)
        }

        // Identifier
        p.isAlphaNumeric() || check("_") -> parseIdentifier()
        // Comment
        match("//") -> {
            // inline comments
            while (!match("\n") && !done()) {
                step()
            }
            createToken(TokenType.Comment)
            incLine(true)
        }

        // inlineRepl
        check(">") && peek(1).isDigit() -> {
            step()
            stepWhileDigit()
            createToken(TokenType.InlineReplWithNum)
        }


        match("::") -> createToken(TokenType.DoubleColon)

        match("||") -> createToken(TokenType.BinarySymbol)
        match("&&") -> createToken(TokenType.BinarySymbol)

        match("=>") -> createToken(TokenType.Then)
        match("|=>") -> createToken(TokenType.Else)

        match("|>") -> createToken(TokenType.PipeOperator)
        match("|") -> createToken(TokenType.Pipe)

        match("#{") -> createToken(TokenType.OpenBraceHash)
        match("#(") -> createToken(TokenType.OpenParenHash)

        match("->") -> createToken(TokenType.ReturnArrow)
        match("<-") -> createToken(TokenType.AssignArrow)
        match("^") -> createToken(TokenType.Return)

        match("==") -> createToken(TokenType.BinarySymbol)
        match("!=") -> createToken(TokenType.BinarySymbol)

        match("=") -> createToken(TokenType.Assign)
        match("`") -> createToken(TokenType.Apostrophe)


        else -> {
            var n = symbolTable.getMaxSymbolSize()
            while (n > 0) {
                for (symbol in symbolTable.getSymbols(n)) {
                    if (match(symbol)) {
                        getToken(symbol)?.let { tokens.add(it) }
                        return
                    }
                }
                n--
            }
            step()
            createToken(TokenType.BinarySymbol)
        }
    }
}
