package frontend.util

import frontend.Lexer
import frontend.addSymbol
import frontend.meta.TokenType

val POSSIBLE_BINARY_MESSAGES = setOf(
    ">", "<", "=", "~", "/", "+", "-", "_", "*", "?", "@", "==", "!=",
    ">=", "<=", "+=", "-=", "/=", "*=", "**=", "!", "%", "&", "^",
    ">>", "<<"
)

fun Lexer.fillSymbolTable() {
    this.symbolTable.symbols = hashMapOf(
        "(" to TokenType.OpenParen,
        ")" to TokenType.CloseParen,
        "{" to TokenType.OpenBrace,
        "}" to TokenType.CloseBrace,
        "[" to TokenType.OpenBracket,
        "]" to TokenType.CloseBracket,

        "." to TokenType.Dot,
        "," to TokenType.Comma,
        ":" to TokenType.Colon,
        ";" to TokenType.Cascade,

        "_" to TokenType.Underscore,
        "|" to TokenType.Switch,
        "=>" to TokenType.Then,
        "|=>" to TokenType.Else,
        "|>" to TokenType.Pipe,

        "=" to TokenType.Assign,

        "^" to TokenType.Return,
    )
    this.symbolTable.keywords = hashMapOf(
        // Keywords
        "type" to TokenType.Type,
        "mut" to TokenType.Mut,
        "alias" to TokenType.Alias,
        "union" to TokenType.Union,
        "enum" to TokenType.Enum,
        "constructor" to TokenType.Constructor,
//        "use" to TokenType.Use,

        "true" to TokenType.True,
        "false" to TokenType.False,
        "null" to TokenType.Null
    )

    // add possible binary
    for (possibleBinaryMessage in POSSIBLE_BINARY_MESSAGES) {
        this.symbolTable.addSymbol(possibleBinaryMessage, TokenType.BinarySymbol)
    }

}
