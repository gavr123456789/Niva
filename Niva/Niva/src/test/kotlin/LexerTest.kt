
import frontend.Lexer
import frontend.lex
import main.frontend.meta.TokenType
import main.frontend.meta.TokenType.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


val helloWorldProgram = """
"Hello w" echo
""".trimIndent()


val rawString = """
x = r"string"
""".trimIndent()


class LexerTest {

    @Test
    fun identifierColon() {
        val manyExpr = "sas:"
        checkWithEnd(manyExpr, listOf(Identifier, Colon))
    }

    @Test
    fun double() {
        val manyExpr = "0.0"
        checkWithEnd(manyExpr, listOf(TokenType.Double))
    }


    @Test
    fun manyExpr() {

        val manyExpr = """
            x sas
            y sus
        """.trimIndent()
        checkWithEnd(manyExpr, listOf(Identifier, Identifier, EndOfLine, Identifier, Identifier))
    }

    @Test
    fun oneManyLinesExpr() {
        """
            x sas: 1
            .ses: 2
            x sas
        """.trimIndent().also {
            // there no end of line after "sas" because there end of file
            checkWithEnd(
                it, listOf(
                    Identifier,
                    Identifier,
                    Colon,
                    Integer,
                    EndOfLine,
                    Dot,
                    Identifier,
                    Colon,
                    Integer,
                    EndOfLine,
                    Identifier,
                    Identifier,
                )
            )
        }
    }

    @Test
    fun emptySource() {
        checkWithEnd("", listOf())
    }

    @Test
    fun string() {
        checkWithEnd("\"saqwf !!! ... pwf s\"", listOf(TokenType.String))
    }

    @Test
    fun helloWorld() {
        checkWithEnd(helloWorldProgram, listOf(TokenType.String, Identifier))
    }

    @Test
    fun createVariable() {
        checkWithEnd("x = 42", listOf(Identifier, Assign, Integer))
    }

    @Test
    fun typedVar() {
        checkWithEnd("x::int", listOf(Identifier, DoubleColon, Identifier))
    }

    @Test
    fun sass() {
        checkWithEnd("|=>", listOf(Else))
    }

    @Test
    fun singleIdentifier() {
        checkWithEnd("sas", listOf(Identifier))
    }

    @Test
    fun rawString() {
        checkWithEnd(rawString, listOf(Identifier, Assign, TokenType.String))
    }

    @Test
    fun functionDeclarationWithBody() {

        val functionDeclaration = """
            int to: x = [
              x echo
            ]       
        """.trimIndent()
        checkWithEnd(
            functionDeclaration, listOf(
                Identifier,
                Identifier,
                Colon,
                Identifier,
                Assign,
                OpenBracket,
                EndOfLine,
                Identifier,
                Identifier,
                EndOfLine,
                CloseBracket
            )
        )
    }

    @Test
    fun brackets() {
        checkWithEnd("{} () []", listOf(OpenBrace, CloseBrace, OpenParen, CloseParen, OpenBracket, CloseBracket))
    }

    @Test
    fun keywords() {
        checkWithEnd("true false type union constructor errordomain", listOf(True, False, Type, Union, Constructor, ErrorDomain))
    }

    @Test
    fun hardcodedBinarySymbols() {
        checkWithEnd(
            "^ |> | |=> = :: ! . .[ &", listOf(
                Return, PipeOperator, If, Else, Assign, DoubleColon, BinarySymbol, Dot, DotOpenBracket, Ampersand
            )
        )
    }

    @Test
    fun binarySymbols2() {
        checkWithEnd(
            "|| && == !=", listOf(
                BinarySymbol, BinarySymbol, BinarySymbol, BinarySymbol
            )
        )
    }

    @Test
    fun punctuation() {
        checkWithEnd(". ; , : ", listOf(Dot, Cascade, Comma, Colon))
    }

    @Test
    fun pipeOperator() {
        checkWithEnd("|>", listOf(PipeOperator))
        checkWithEnd("|||", listOf(BinarySymbol, If)) // || is OR
    }

    @Test
    fun comment() {
        checkWithEnd("// some important info", listOf(Comment))
        checkWithEnd("// some important info\n// another comment", listOf(Comment))
    }

    @Test
    fun docComment() {
        checkWithEnd("/// some @important info", listOf(DocComment))
        checkWithEnd("/// some @important info\n/// another", listOf(DocComment))
    }


    @Test
    fun nn() {
        checkWithEnd(
            """
                    min
    
                    ^
            """.trimIndent(), listOf(Identifier, EndOfLine, EndOfLine, Return)
        )
    }

    @Test
    fun dotDotOp() {
        checkWithEnd("1..2", listOf(Integer, BinarySymbol, Integer))
    }

    @Test
    fun codeAttributes() {
        checkWithEnd(
            """
                @ a: 1 b: "sas"
                type Person
            """.trimIndent(), listOf(
                BinarySymbol,
                Identifier,
                Colon,
                Integer,
                Identifier,
                Colon,
                TokenType.String,
                EndOfLine,
                Type,
                Identifier,
            )
        )
    }

    @Test
    fun inlineQuestion() {
        val manyExpr = ">?"
        checkWithEnd(manyExpr, listOf(InlineReplWithQuestion))
    }

    @Test
    fun nullTok() {
        val manyExpr = "null"
        checkWithEnd(manyExpr, listOf(Null))
    }

    @Test
    fun on() {
        val manyExpr = "on"
        checkWithEnd(manyExpr, listOf(On))
    }

    @Test
    fun multilineString() {
        val multiline = "\"\"\" a\"b\"c \"\"\""
        checkWithEnd(multiline, listOf(TokenType.String))
    }

    @Test
    fun multilineString2() {
        val multiline = "\"\"\" " +
                "type Program " +
                "input = \"\"\"\n" +
                "  qwf\n" +
                "\"\"\"\n" +
                "\n" +
                "\n" +
                "\n" +
                "input = \"\"\"\n" +
                "type Program \n" +
                "  readFile: [String -> String]\n" +
                "  walkDir: [String -> List::String]\n" +
                "\"\"\" trimIndent\n \"\"\""
        checkWithEnd(multiline, listOf(TokenType.String))
    }

    @Test
    fun newLines() {
        val manyExpr = """
            start <- start
            .match: "..<"
        """.trimIndent()
        checkWithEnd(manyExpr, listOf(Identifier, AssignArrow, Identifier, EndOfLine, Dot, Identifier, Colon, TokenType.String))
    }

    @Test
    fun utfChar() {
        val char = """
            '\u0000'
        """.trimIndent()
        checkWithEnd(
            char,
            listOf(TokenType.Char)
        )
    }

    @Test
    fun kwDeclarationColonPosition() {
        val kwDecl = "S kw::Str -> Int = 34"
        val lexer = Lexer(kwDecl, File("Niva.iml"))
        val f = lexer.lex()
        val g = f[2]
        assertTrue { g.relPos.start == 4 && g.relPos.end == 6}
    }


    @Test
    fun escape() {
        val manyExpr = """ "\n\n" echo """
        val lexer = Lexer(manyExpr, File("Niva.iml"))
        val result = lexer.lex()
        val q = result.dropLast(1).last()
        assertTrue(q.lexeme == "echo")
        ////

        val manyExpr2 = """"\n\n\n" echo"""
        val lexer2 = Lexer(manyExpr2, File("Niva.iml"))
        val result2 = lexer2.lex()
        val q2 = result2.dropLast(1).last()
        assertTrue(q2.lexeme == "echo")


        ////

        val manyExpr3 = """"\"" echo"""
        val lexer3 = Lexer(manyExpr3, File("Niva.iml"))
        val result3 = lexer3.lex()
        val q3 = result3.dropLast(1).last()
        assertTrue(q3.lexeme == "echo")
    }


    private fun checkWithEnd(source: String, tokens: List<TokenType>, showTokens: Boolean = true) {
        val lexer = Lexer(source, File("Niva.iml"))
        val result = lexer.lex().map { it.kind }
            .dropLast(1) // drop end of file
        assertEquals(tokens, result)
        if (showTokens) {
            println("$result")
        }
    }
}
