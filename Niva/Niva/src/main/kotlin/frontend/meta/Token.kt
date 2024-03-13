package main.frontend.meta

import main.utils.RED
import main.utils.RESET
import java.io.File

enum class TokenType {
    True, False, Null,
    On, // on for many messages declarations

    // Literal types
    Integer,
    Float, // 4.2
    String, Char,
    Double, // 4.2d
    Identifier, NullableIdentifier,
//    Binary, Octal, Hex,

    // Keywords
    Alias, Type, Union, Constructor, Mut,
    Return, // ^
    ReturnArrow, // -> for return types
    AssignArrow, // <-


    If, // |
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
    DotOpenBracket, // .[

    EndOfFile,
    NoMatch,   // Used internally by the symbol table
    Comment,   // Useful for documentation comments, pragmas, etc.
    BinarySymbol,    // A generic symbol
    Assign, // =,
    DoubleColon,
    EndOfLine, // \n and next line starts not from the dot

    InlineReplWithNum, // >Number, >3
    InlineReplWithQuestion, // >?
    Underscore, // _
    Enum, // enum
    Builder, // builder
    Bang, // !!

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

private class FakeToken {
    companion object {
        val fakeToken = Token(
            TokenType.Identifier, "Fake Token", 0, Position(0, 1),
            Position(0, 1), File("Compiler Error")
        )
    }
}

fun createFakeToken(): Token = FakeToken.fakeToken


fun Token.isIdentifier() = this.kind == TokenType.Identifier || this.kind == TokenType.NullableIdentifier
fun Token.isNullable() = this.kind == TokenType.NullableIdentifier

class CompilerError(text: String): Exception(text)

fun Token.compileError(text: String): Nothing {
    ":" + this.relPos.start
    val fileLine = "(" + file.name + ":" + line + ")"

//    error("\n$red\t$text.$fileLine$reset")
    val errorText = "$RED Error:$RESET $text$RESET.$fileLine"
//    println("$RED Error:$RESET $text$RESET.$fileLine")
    throw CompilerError(errorText)
//    exitProcess(0)
}
