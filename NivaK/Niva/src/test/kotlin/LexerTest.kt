import frontend.Lexer
import frontend.lex
import frontend.meta.TokenType
import frontend.meta.TokenType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


val helloWorldProgram = """
"Hello w" echo
""".trimIndent()

class LexerTest {
    @Test
    fun emptySource() {
        checkOnKinds("", mutableListOf(EndOfFile))
    }

    @Test
    fun helloWorld() {
        checkOnKinds(helloWorldProgram, mutableListOf( StringToken, Identifier, EndOfFile))
    }

    @Test
    fun sasIdentifier() {
        checkOnKinds("sas", mutableListOf(Identifier, EndOfFile))
    }

    @Test
    fun punctuation() {
        checkOnKinds("{}", mutableListOf(LeftParen, RightParen, EndOfFile))
    }

    @Test
    fun typeKW() {
        checkOnKinds("type", mutableListOf(Type, EndOfFile))
    }

    @Test
    fun trueFalseKW() {
        checkOnKinds("true", mutableListOf(True, EndOfFile))
    }

    private fun checkOnKinds(source: String, tokens: MutableList<TokenType>, showTokens: Boolean = true) {
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