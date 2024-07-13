@file:Suppress("unused")

package main.frontend.meta

import frontend.parser.parsing.Parser
import frontend.parser.parsing.peek
import main.utils.CYAN
import main.utils.GlobalVariables
import main.utils.PURP
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
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
    Type, Union, Constructor, Mut, ErrorDomain,
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
    Ampersand, // &

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

data class Position(val start: Int, var end: Int)

class Token(
    val kind: TokenType,
    val lexeme: String,
    val line: Int,
    val pos: Position,
    val relPos: Position,
    val file: File,
    val spaces: Int = 0,
    var lineEnd: Int = -1 // it is set only for many-line statements like type decl or kw msg
) {

    fun isMultiline() = lineEnd != -1 && lineEnd != line

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

class CompilerError(text: String, val token: Token, val noColorsMsg: String) : Exception(text)

fun String.removeColors() =
    this.replace(RED, "").replace(WHITE, "").replace(CYAN, "").replace(YEL, "").replace(PURP, "").replace(RESET, "")

fun Token.compileError(text: String): Nothing {
    val fileLine = "(" + file.name + ":" + line + ")"
    val errorText = "${RED}Error:$RESET\n$text$RESET.$fileLine"
    throw CompilerError(errorText, this, text.removeColors())
}

fun Parser.parsingError(text: String): Nothing {
    val f = peek()
    var firstOnLineTok = f
    var counter = 0
    while (firstOnLineTok.pos.start != 0 && firstOnLineTok.kind != TokenType.EndOfFile) {
        firstOnLineTok = peek(counter)
        counter--
    }
    val fileLine = "(" + file.name + ":" + f.line + ")"

    val errorText2 = "${RED}Error:$RESET Syntax \n\t$text$RESET.$fileLine"
    var tok2 = firstOnLineTok
    val errorText = buildString {
        appendLine(errorText2)
        if (!GlobalVariables.isLspMode)
            append("\t${firstOnLineTok.line}| ")
        counter++
        // getting the full line of tokens, with coloring of the wrong one!
        while (tok2.line != firstOnLineTok.line + 1 && tok2.kind != TokenType.EndOfFile) {
            if (tok2 == firstOnLineTok) {
                append("$RED${tok2.lexeme}$RESET")
            } else
                append(tok2.lexeme)

            counter++
            tok2 = peek(counter)
        }
        append("\n\t")
    }



    throw CompilerError(errorText, f, text.removeColors())

}
