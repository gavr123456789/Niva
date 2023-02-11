package frontend.util

import frontend.Lexer
import frontend.addSymbol
import frontend.meta.TokenType

val POSSIBLE_BINARY_MESSAGES = setOf(">", "<", "=", "~", "/", "+", "-", "_", "*", "?", "@", "==", "!=",
    ">=", "<=", "+=", "-=", "/=", "*=", "**=", "!", "%", "&", "^",
    ">>", "<<")

fun Lexer.fillSymbolTable() {
    this.symbolTable.symbols = hashMapOf(
        "{" to TokenType.LeftParen,
        "}" to TokenType.RightParen,
        "(" to TokenType.LeftBrace,
        ")" to TokenType.RightBrace,
        "[" to TokenType.LeftBracket,
        "]" to TokenType.RightBracket,

        "." to TokenType.Dot,
        "," to TokenType.Comma,
        ":" to TokenType.Colon,
        ";" to TokenType.Semicolon,

        "|" to TokenType.Switch,
        "|=>" to TokenType.Else,
        "|>" to TokenType.Pipe,

        "=" to TokenType.Equal,

        "^" to TokenType.Return,
    )
    this.symbolTable.keywords = hashMapOf(
        // Keywords
        "type" to TokenType.Type,
        "union" to TokenType.Union,
        "use" to TokenType.Use,

        "true" to TokenType.True,
        "false" to TokenType.False,
    )

    // add possible binary
    for (possibleBinaryMessage in POSSIBLE_BINARY_MESSAGES) {
        this.symbolTable.addSymbol(possibleBinaryMessage, TokenType.BinarySymbol)
    }

}