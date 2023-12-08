package frontend.meta

import java.io.File
import kotlin.system.exitProcess

enum class TokenType {
    True, False,

    // Literal types
    Integer, Float, String, Char,
    Identifier, NullableIdentifier,
    Binary, Octal, Hex,

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
    Assign, // =,
    DoubleColon,
    EndOfLine, // \n and next line starts not from the dot

    InlineReplWithNum, // >Number, >3
    Underscore, // _
    Enum, // enum

}

data class Position(val start: Int, val end: Int)

class Token(
    val kind: TokenType,
    val lexeme: String,
    val line: Int,
    val pos: Position,
    val relPos: Position,
    val file: File,
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

    override fun toString(): String {
        return lexeme
    }
}

fun Token.isIdentifier() = this.kind == TokenType.Identifier || this.kind == TokenType.NullableIdentifier
fun Token.isNullable() = this.kind == TokenType.NullableIdentifier

fun Token.compileError(text: String): Nothing {
    ":" + this.relPos.start
    val fileLine = "(" + file.name + ":" + line + ")"
    val red = "\u001b[31m"
    val reset = "\u001b[0m"
//    error("\n$red\t$text.$fileLine$reset")
    println("$red\t$text.$fileLine$reset")
    exitProcess(0)
}
