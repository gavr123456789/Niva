package frontend.meta

enum class TokenType {
    True, False,

    // Literal types
    Integer, Float, String, Identifier, NullableIdentifier,
    Binary, Octal, Hex, Char,

    // Keywords
    Alias, Type, Union, Use, Constructor, Mut,
    Return, // ^
    ReturnArrow, // -> for return types
    AssignArrow, // <-


    Pipe, // |
    PipeOperator, // |>


    Then, // =>
    Switch, // |
    Else, // |=>

    // brackets
    OpenParen, CloseParen, // ()
    OpenBrace, CloseBrace, // {}
    OpenBracket, CloseBracket, // []

    OpenBraceHash, // #{
    OpenParenHash, // #(

    // punctuation
    Dot, Cascade, Comma, Colon, Apostrophe,// . ; , `


    EndOfFile,
    NoMatch,   // Used internally by the symbol table
    Comment,   // Useful for documentation comments, pragmas, etc.
    BinarySymbol,    // A generic symbol
    Pragma,
    Assign, Equal, NotEqual, // =, ==, !=
    DoubleColon,
    EndOfLine // \n and next line starts not from the dot
    ,
}

data class Position(val start: Int, val end: Int)

data class Token(
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

fun Token.isIdentifier() = this.kind == TokenType.Identifier || this.kind == TokenType.NullableIdentifier
fun Token.isNullable() = this.kind == TokenType.NullableIdentifier
