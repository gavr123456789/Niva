package frontend.meta

enum class TokenType {
    True, False,

    // Literal types
    Integer, Float, StringToken, Identifier,
    Binary, Octal, Hex, Char,

    // Keywords
    Type, Union, Use,
    Return, // ^

    Pipe, // |>

    Switch, // |
    Else, // |=>

    // brackets
    LeftParen, RightParen, // ()
    LeftBrace, RightBrace, // {}
    LeftBracket, RightBracket, // []

    // punctuation
    Dot, Semicolon, Comma, Colon, // . ; ,


    EndOfFile,
    NoMatch,   // Used internally by the symbol table
    Comment,   // Useful for documentation comments, pragmas, etc.
    BinarySymbol,    // A generic symbol
    Pragma,
    Equal,
}

data class Position(val start: Int, val end: Int)

data class Token (
    val kind: TokenType,
    val lexeme: String,
    val line: Int,
    val pos: Position,
    val relPos: Position,
    val spaces: Int = 0
    ) {
    fun kindToString() = this.kind.toString()

    override fun equals(other: Any?): Boolean =
        other is Token && kind == other.kind

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + lexeme.hashCode()
        result = 31 * result + line
        result = 31 * result + pos.hashCode()
        result = 31 * result + relPos.hashCode()
        result = 31 * result + spaces
        return result
    }
}

