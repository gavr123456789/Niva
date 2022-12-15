import frontend.Lexer
import frontend.lex
import frontend.meta.TokenType
import frontend.meta.TokenType.*
//import frontend.meta.TokenType.BinarySymbol
//import frontend.meta.TokenType.EndOfFile
import frontend.util.fillSymbolTable
import org.testng.annotations.Test

class LexerTest {
    @Test
    fun emptySource() {
        checkOnKinds("", mutableListOf(EndOfFile))
    }

    @Test
    fun punctuation() {
        checkOnKinds("{}", mutableListOf(BinarySymbol, BinarySymbol, EndOfFile))
    }

    fun checkOnKinds(source: String, tokens: MutableList<TokenType>) {
        val lexer = Lexer(source,"sas")
        lexer.fillSymbolTable()
        val result = lexer.lex().map { it.kind }
        if (tokens != result) {
            throw Throwable("\n\ttokens: $tokens\n\tresult: $result")
        }
    }
}