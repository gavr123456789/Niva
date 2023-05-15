import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.util.fillSymbolTable

fun emptySource() {
    check("", mutableListOf(TokenType.EndOfFile))
}

fun punctuation() {
    check("{}", mutableListOf(TokenType.BinarySymbol, TokenType.BinarySymbol, TokenType.EndOfFile))
}

fun check(source: String, tokens: MutableList<TokenType>) {
    val lexer = Lexer(source, "sas")
    lexer.fillSymbolTable()
    val result = lexer.lex().map { it.kind }
    if (tokens != result) {
        throw Throwable("\n\ttokens: $tokens\n\tresult: $result")
    }
}

fun lex(source: String): MutableList<Token> {
    val lexer = Lexer(source, "sas")
    lexer.fillSymbolTable()
    return lexer.lex()
}

fun main() {

//    val source = "x::int = 1"
//    val tokens = lex(source)
//    val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
//    val ast = parser.parse()
//    println(ast)
}
