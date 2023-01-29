import frontend.Lexer
import frontend.lex
import frontend.meta.TokenType
import frontend.meta.TokenType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LexerTest {
    @Test
    fun emptySource() {
        checkOnKinds("", mutableListOf(EndOfFile))
    }

    @Test
    fun ifStatement() {
        checkOnKinds("", mutableListOf(EndOfFile))
    }

    @Test
    fun sasIdentifier() {
        checkOnKinds("sas", mutableListOf(Identifier, EndOfFile))
    }

    @Test
    fun punctuation() {
        checkOnKinds("{}", mutableListOf(BinarySymbol, BinarySymbol, EndOfFile))
    }

    @Test
    fun typeKW() {
        checkOnKinds("type", mutableListOf(Type, EndOfFile))
    }

    @Test
    fun trueFalseKW() {
        checkOnKinds("true", mutableListOf(True, EndOfFile))
    }

    fun checkOnKinds(source: String, tokens: MutableList<TokenType>, showTokens: Boolean = true) {
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