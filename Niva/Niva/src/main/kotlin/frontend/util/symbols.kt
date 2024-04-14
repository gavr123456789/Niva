package main.frontend.util

import frontend.Lexer
import frontend.addSymbol
import main.frontend.meta.TokenType

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

        ".[" to TokenType.DotOpenBracket,
        "." to TokenType.Dot,
        "," to TokenType.Comma,
        ":" to TokenType.Colon,
        ";" to TokenType.Cascade,

        "_" to TokenType.Underscore,
        "|" to TokenType.Switch,
        "=>" to TokenType.Then,
        "|=>" to TokenType.Else,
        "|>" to TokenType.If,

        "=" to TokenType.Assign,

        "^" to TokenType.Return,
    )
    this.symbolTable.keywords = hashMapOf(
        // Keywords
        "type" to TokenType.Type,
        "mut" to TokenType.Mut,
//        "alias" to TokenType.Alias,
        "union" to TokenType.Union,
        "builder" to TokenType.Builder,
        "enum" to TokenType.Enum,
        "constructor" to TokenType.Constructor,
//        "use" to TokenType.Use,

        "true" to TokenType.True,
        "false" to TokenType.False,
        "null" to TokenType.Null,
        "on" to TokenType.On
    )

    // add possible binary
    for (possibleBinaryMessage in POSSIBLE_BINARY_MESSAGES) {
        this.symbolTable.addSymbol(possibleBinaryMessage, TokenType.BinarySymbol)
    }

}
