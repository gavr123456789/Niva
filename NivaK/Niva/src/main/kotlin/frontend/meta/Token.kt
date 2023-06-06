package frontend.meta

enum class TokenType {
    True, False,

    // Literal types
    Integer, Float, String, Identifier,
    Binary, Octal, Hex, Char,

    // Keywords
    Type, Union, Use, Constructor,
    Return, // ^
    ReturnArrow, // -> for return types

    Pipe, // |>

    Then, // =>
    Switch, // |
    Else, // |=>

    // brackets
    LeftParen, RightParen, // ()
    LeftBrace, RightBrace, // {}
    LeftBracket, RightBracket, // []

    LeftBraceHash, // #{
    LeftParenHash, // #(

    // punctuation
    Dot, Semicolon, Comma, Colon, Apostrophe,// . ; , `


    EndOfFile,
    NoMatch,   // Used internally by the symbol table
    Comment,   // Useful for documentation comments, pragmas, etc.
    BinarySymbol,    // A generic symbol
    Pragma,
    Equal,
    DoubleColon,
    EndOfLine // \n and next line starts not from the dot
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

