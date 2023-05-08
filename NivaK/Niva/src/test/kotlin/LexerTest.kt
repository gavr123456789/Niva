import frontend.Lexer
import frontend.lex
import frontend.meta.TokenType
import frontend.meta.TokenType.*
import org.testng.Assert.assertEquals
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.Test
import org.testng.annotations.Test


val helloWorldProgram = """
"Hello w" echo
""".trimIndent()

val functionDeclaration = """
int to: x = [
  x echo
]
""".trimIndent()

val functionDeclarationWithType = """
int to: x(int) = [
  code
]
""".trimIndent()

val rawString = """
x = r"string"
""".trimIndent()

class LexerTest {

    @Test
    fun emptySource() {
        check("", listOf(EndOfFile))
    }

    @Test
    fun helloWorld() {
        check(helloWorldProgram, listOf(StringToken, Identifier, EndOfFile))
    }

    @Test
    fun createVariable() {
        check("x = 42", listOf(Identifier, Equal, Integer, EndOfFile))
    }

    @Test
    fun singleIdentifier() {
        check("sas", listOf(Identifier, EndOfFile))
    }

    @Test
    fun rawString() {
        check(rawString, listOf(Identifier, Equal, StringToken, EndOfFile))
    }

    @Test
    fun functionDeclarationWithBody() {
        check(
            functionDeclaration,
            listOf(
                Identifier,
                Identifier,
                Colon,
                Identifier,
                Equal,
                LeftBracket,
                Identifier,
                Identifier,
                RightBracket,
                EndOfFile
            )
        )
    }

    @Test
    fun functionDeclarationWithBodyWithType() {
        check(
            functionDeclarationWithType,
            listOf(
                Identifier,
                Identifier,
                Colon,
                Identifier,
                LeftBrace,
                Identifier,
                RightBrace,
                Equal,
                LeftBracket,
                Identifier,
                RightBracket,
                EndOfFile
            )
        )
    }

    @Test
    fun brackets() {
        check("{} () []", listOf(LeftParen, RightParen, LeftBrace, RightBrace, LeftBracket, RightBracket, EndOfFile))
    }

    @Test
    fun keywords() {
        check("true false type use union ", listOf(True, False, Type, Use, Union, EndOfFile))
    }

    @Test
    fun hardcodedBinarySymbols() {
        check("^ |> | |=> =", listOf(Return, Pipe, BinarySymbol, Pipe, Else, Equal, BinarySymbol, Equal, EndOfFile))
    }

    @Test
    fun punctuation() {
        check(". ; , : ", listOf(Dot, Semicolon, Comma, Colon, EndOfFile))
    }

    private fun check(source: String, tokens: List<TokenType>, showTokens: Boolean = true) {
        val lexer = Lexer(source, "sas")
//        lexer.fillSymbolTable()
        val result = lexer.lex().map { it.kind }
        assertEquals(tokens, result)
        if (showTokens) {
            println("$result")
        }
//        if (tokens != result) {
//            throw Throwable("\n\ttokens: $tokens\n\tresult: $result")
//        }
    }
}