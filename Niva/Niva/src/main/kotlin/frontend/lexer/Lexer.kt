@file:Suppress("ControlFlowWithEmptyBody")

package frontend

import main.frontend.lexer.isAlphaNumeric
import main.frontend.lexer.isDigit
import main.frontend.meta.*
import main.frontend.util.fillSymbolTable
import java.io.File

typealias StringToToken = HashMap<String, TokenType>

class SymbolTable(var keywords: StringToToken = hashMapOf(), var symbols: StringToToken = hashMapOf())

fun SymbolTable.addSymbol(lexeme: String, tokenType: TokenType) = symbols.set(lexeme, tokenType)

fun SymbolTable.existsKeyword(keyword: String) = keyword in keywords

fun SymbolTable.getMaxSymbolSize(): Int = symbols.maxOf { it.key.length }
fun SymbolTable.getSymbols(n: Int) = symbols.keys.filter { it.length == n }


class Lexer(
    var source: String, val file: File
) {
    val symbolTable: SymbolTable = SymbolTable()
    val tokens: MutableList<Token> = mutableListOf()
    var line: Int = 1
    var start: Int = 0
    var current: Int = 0
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
        incLine(false) // EOF
    }
}

fun Lexer.lex() = tokens

fun Lexer.done() = current >= source.length

fun Lexer.incLine(needAddNewLineToken: Boolean = false) {
    line += 1
    linePos = 0
    if (!done() && needAddNewLineToken) { // && getfirstAfterSpaces() != "."
        start = current
        createToken(TokenType.EndOfLine)
    }
}

fun Lexer.step(n: Int = 1): String = buildString {
    while (length < n) {
        if (done() || current > source.lastIndex) {
            break
        } else {
            append(source[current])
        }

        current++
    }
    linePos += n
}

fun Lexer.peek(distance: Int = 0, length: Int = 1): String = buildString {
    var i = distance
    while (this.length < length) {
        if (done() || current + i > source.lastIndex || current + i < 0) break
        else append(source[current + i]) // + 1
        i++
    }
}

fun Lexer.check(arg: String, distance: Int = 0): Boolean = when {
    done() -> false
    else -> {
        val x = peek(distance, arg.length)
        x == arg
    }
}

// TODO I think we need swap true and false here
fun Lexer.check(args: Array<String>, distance: Int = 0): Boolean {
    for (arg in args) {
        if (check(arg, distance)) { // add !
            return true // reverse
        }
    }
    return false // reverse
}

fun Lexer.match(s: String): Boolean = when {
    !check(s) -> {
        false
    }

    else -> {
        step(s.length)
        true
    }
}

// TODO I think we need swap true and false here
fun Lexer.match(args: Array<String>): Boolean {


    for (arg in args) {
        if (match(arg)) {
            return true
        }
    }
    return false
}

fun Lexer.createToken(tokenType: TokenType, endPositionMinus: Int = 0, addToLexeme: String? = null, customLine: Int? = null) {
    try {
        val line = customLine ?: line
        val lexeme2 = source.slice(start until current)// better replace to view
        val lexeme3 = if (addToLexeme == null) lexeme2 else lexeme2 + addToLexeme
        val lexeme = lexeme3.dropLast(endPositionMinus)
        val end = current - 1 - endPositionMinus
        val realEnd = linePos - endPositionMinus
        val tok = Token(
            kind = tokenType,
            lexeme = lexeme,
            line = line,
            spaces = spaces,
            pos = Position(start = start, end = end),
            relPos = Position(start = linePos - lexeme.length, end = realEnd),
            file = file
        )

        tokens.add(tok)
        spaces = 0

    } catch (e: StringIndexOutOfBoundsException) {
        error(
            "Compiler bug: cant create token: $tokenType, slice index out of bound: " + e.message + "\n ${
                source.slice(
                    start until source.length
                )
            }"
        )
    }
}

fun Lexer.error(message: String): Nothing {
    val msg = "${message}\nline: ${line}\n$file\npos: $lineCurrent - ${linePos - 1}\n"
    this.tokens.last().compileError(msg)
}

fun String.set(index: Int, char: Char): String {
    return substring(0, index) + char + substring(index)
}

fun String.set(index: Int, str: String): String {
    return substring(0, index) + str + substring(index)
}

//fun Lexer.parseEscape() {
//    val q = peek()
//
//    source = when (q[0]) {
//        'n' -> source.set(current, 'n')
//        't' -> source.set(current, 't')
//        '\'' -> source.set(current, '\'')
//        '\\' -> source.set(current, '\\')
//        else -> this.error("invalid escape sequence '\\${peek()}'")
//    }
//}

enum class StrMode {
    Single,
    Raw,
    Multi,
    Format,
    Bytes
}

fun Lexer.parseString(delimiter: String, mode: StrMode = StrMode.Single) {
    var slen = 0
    var wasUtfSymbol = false
    while (!this.check(delimiter) && !this.done()) {

        if (this.match("\n")) {
            if (mode == StrMode.Multi) {
                this.incLine(false)// multi-string
            } else {
                this.error("unexpected EOL while parsing string literal")
            }
        }

        if (arrayOf(StrMode.Raw, StrMode.Multi).contains(mode)) {
            this.step()
            continue
        } else {
            wasUtfSymbol = wasUtfSymbol || match("\\u")
            this.match("\\")
        }

        if (mode == StrMode.Format && match("{")) {
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
        } else if (mode == StrMode.Format && check("}")) {
            if (check("}", 1)) {
                error("unmatched '}' in format string")
            } else {
                source = source.slice(0 until current) + source.slice(current + 1 until source.lastIndex)
            }
        }
        step()
        slen++
        if (slen > 1 && delimiter == "'" && !wasUtfSymbol) {
            error("invalid character literal (length of char sequence must be one!)")
        }
    }



    if (mode == StrMode.Multi) {
        if (!match(delimiter)) {
            error("unexpected EOL while parsing multi-line string literal, $delimiter must be repeated 3 times for multi strings")
        }
    } else if (done() && peek(-1) != delimiter) {
        error("unexpected EOF while parsing string literal")
    } else {
        step()
    }

    if (delimiter == "\"" || delimiter == "\"\"\"") {
        createToken(TokenType.String)
    } else {
        createToken(TokenType.Char)
    }
}

//fun Lexer.getfirstAfterSpaces(): String {
//    var x = 0
//    while (true) {
//        val symbol = peek(x)
//        if (symbol == " ") {
//            x += 1
//        } else {
//            return symbol
//        }
//    }
//}

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
        kind = TokenType.Double
        stepWhileDigit()
        if (check(arrayOf("e", "E"))) {
            step()
        }
        stepWhileDigit()
    }
    if (match("'")) {
        stepWhileAlphaNumeric()
    }
    // 3.5f == Float
    if (match("f")) {
        kind = TokenType.Float
        createToken(kind) // don't save d to not generate it in kotlin
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

            createToken(TokenType.NullableIdentifier, 1)
        } else {
            createToken(TokenType.Identifier)
        }
    }
}

enum class CommentType() {
    Doc, Usual
}

fun Lexer.someComment(commentType: CommentType): Boolean {
    fun Lexer.readUntilNewLine() {
        while ((!match("\n")) && !done()) {
            step()
        }
    }

    if (when (commentType) {
            CommentType.Usual -> match("//")
            CommentType.Doc -> match("///")
        }
    ) {

        readUntilNewLine()
        return true
    }
    return false
}


fun Lexer.skipSpaces() {
    while (match(" ")) {
        spaces++
        start += 2 // 1 to go to space ("^__word"), 2 to skip spase ("_^_word")
    }
}

fun Lexer.next() {

    fun Lexer.getTokenFromSymbolTable(lexeme: String): Token? {
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

    val someComment = { commentType: CommentType ->
        val saveLine = line // because there will be wrong line number
        while (someComment(commentType)) {
            incLine(false)// comment
            skipSpaces()
        }
        when (commentType) {
            CommentType.Doc -> createToken(TokenType.DocComment, customLine = saveLine)
            CommentType.Usual -> createToken(TokenType.Comment, customLine = saveLine)
        }
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
        match("\n") ->
            incLine(true)
        match(arrayOf("\"", "'")) -> {
            var mode = StrMode.Single
            // if """ then it must be multiline string
            if (peek(-1) != "'" && check(peek(-1)) && check(peek(-1), 1)) {
                // Multiline strings start with 3 quotes
                step(2)
                mode = StrMode.Multi
            }
            val delimiter = if (mode != StrMode.Multi) peek(-1) else "\"\"\""
            parseString(delimiter, mode)
        }
        // before digit because of floats "3.14"
        match("..<") -> {
            createToken(TokenType.BinarySymbol)
        }
        match("..") -> createToken(TokenType.BinarySymbol)
        // 42
        p.isDigit() -> {
            step()
            parseNumber()
        }
        // -42
        check("-") && peek(1).isDigit() -> {
            step()
            step()
            parseNumber()
        }
        // String
        p.isAlphaNumeric() && check(arrayOf("\"", "'"), 1) -> {

            //  Prefixed string literal (i.e. f"Hi {name}!")
            when (step()) {
                "r" -> parseString(step(), StrMode.Raw)
                "b" -> parseString(step(), StrMode.Bytes)
                "f" -> parseString(step(), StrMode.Format)
                else -> error("unknown string prefix '${peek(-1)}'")
            }
        }

        // if elseif chain
        match("_") -> {
            createToken(TokenType.Underscore)
        }

        // Identifier
        p.isAlphaNumeric() || check("_") ->
            parseIdentifier()

        // Doc comment
        check("///") -> someComment(CommentType.Doc)
        // Comment
        check("//") -> someComment(CommentType.Usual)



        match(">?") -> createToken(TokenType.InlineReplWithQuestion)
        // inlineRepl
        check(">") && peek(1).isDigit() -> {
            step()
            stepWhileDigit()
            createToken(TokenType.InlineReplWithNum)
        }


        match("::") -> createToken(TokenType.DoubleColon)

        match("||") -> createToken(TokenType.BinarySymbol)
        match("&&") -> createToken(TokenType.BinarySymbol)
        match("&") -> createToken(TokenType.Ampersand)

        match("=>") -> createToken(TokenType.Then)
        match("|=>") -> createToken(TokenType.Else)

        match("|>") -> createToken(TokenType.PipeOperator)
        match("|") -> createToken(TokenType.If)

        match("#{") -> createToken(TokenType.OpenBraceHash)
        match("#(") -> createToken(TokenType.OpenParenHash)

        match("->") -> createToken(TokenType.ReturnArrow)
        match("<-") -> createToken(TokenType.AssignArrow)
        match("^") -> createToken(TokenType.Return)
        match("!!") -> createToken(TokenType.Bang)

        match("==") -> createToken(TokenType.BinarySymbol)
        match("!=") -> createToken(TokenType.BinarySymbol)

        match("=") -> createToken(TokenType.Assign)
        match("`") -> createToken(TokenType.Apostrophe)


        else -> {
            var n = symbolTable.getMaxSymbolSize()
            while (n > 0) {
                for (symbol in symbolTable.getSymbols(n)) {
                    if (match(symbol)) {
                        getTokenFromSymbolTable(symbol)?.let { tokens.add(it) }
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
