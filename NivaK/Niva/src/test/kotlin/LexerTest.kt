import frontend.Lexer
import frontend.lex
import frontend.meta.TokenType
import frontend.meta.TokenType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


val helloWorldProgram = """
"Hello w" echo
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
    fun identifierColon() {
        val manyExpr = "sas:"
        check(manyExpr, listOf(Identifier, Colon, EndOfFile))
    }


    @Test
    fun manyExpr() {
        val manyExpr = """
            x sas
            y sus
        """.trimIndent()
        check(manyExpr, listOf(Identifier, Identifier, EndOfLine, Identifier, Identifier, EndOfFile))
    }

    @Test
    fun oneManyLinesExpr() {
        val oneExpr = """x sas: 1
  .ses: 2
x sas
"""
        // there no end of line after "sas" because there end of file
        check(
            oneExpr,
            listOf(
                Identifier,
                Identifier,
                Colon,
                Integer,
                Dot,
                Identifier,
                Colon,
                Integer,
                EndOfLine,
                Identifier,
                Identifier,
                EndOfFile
            )
        )
    }

    @Test
    fun emptySource() {
        check("", listOf(EndOfFile))
    }

    @Test
    fun string() {
        check("\"sas\"", listOf(TokenType.String, EndOfFile))
    }

    @Test
    fun helloWorld() {
        check(helloWorldProgram, listOf(TokenType.String, Identifier, EndOfFile))
    }

    @Test
    fun createVariable() {
        check("x = 42", listOf(Identifier, Assign, Integer, EndOfFile))
    }

    @Test
    fun typedVar() {
        check("x::int", listOf(Identifier, DoubleColon, Identifier, EndOfFile))
    }

    @Test
    fun sass() {
        check("|=>", listOf(Else, EndOfFile))
    }

    @Test
    fun singleIdentifier() {
        check("sas", listOf(Identifier, EndOfFile))
    }

    @Test
    fun rawString() {
        check(rawString, listOf(Identifier, Assign, TokenType.String, EndOfFile))
    }

    @Test
    fun functionDeclarationWithBody() {

        val functionDeclaration = """
int to: x = [
  x echo
]
""".trimIndent()
        check(
            functionDeclaration,
            listOf(
                Identifier, Identifier, Colon, Identifier, Assign, OpenBracket, EndOfLine,
                Identifier, Identifier, EndOfLine,
                CloseBracket,
                EndOfFile
            )
        )
    }

    @Test
    fun brackets() {
        check("{} () []", listOf(OpenBrace, CloseBrace, OpenParen, CloseParen, OpenBracket, CloseBracket, EndOfFile))
    }

    @Test
    fun keywords() {
        check("true false type use union constructor", listOf(True, False, Type, Use, Union, Constructor, EndOfFile))
    }

    @Test
    fun hardcodedBinarySymbols() {
        check(
            "^ |> | |=> = ::",
            listOf(Return, PipeOperator, Pipe, Else, Assign, DoubleColon, EndOfFile)
        )
    }

    @Test
    fun punctuation() {
        check(". ; , : ", listOf(Dot, Cascade, Comma, Colon, EndOfFile))
    }

    @Test
    fun pipeOperator() {
        check("|>", listOf(PipeOperator, EndOfFile))
        check("|||", listOf(Pipe, Pipe, Pipe, EndOfFile))
    }

    @Test
    fun typeAlias() {
        check("alias", listOf(Alias, EndOfFile))
    }

    @Test
    fun nn() {
        check(
            """
                min

                ^
        """.trimIndent(), listOf(Identifier, EndOfLine, EndOfLine, Return, EndOfFile)
        )
    }

    @Test
    fun dotDotOp() {
        check("1..2", listOf(Integer, BinarySymbol, Integer, EndOfFile))
    }


    private fun check(source: String, tokens: List<TokenType>, showTokens: Boolean = true) {
        val lexer = Lexer(source, "sas")
//        lexer.fillSymbolTable()
        val result = lexer.lex().map { it.kind }
        assertEquals(tokens, result)
        if (showTokens) {
            println("$result")
        }
    }
}
