package frontend.lexer

import frontend.Lexer
import frontend.incLine
import frontend.meta.Position
import frontend.meta.TokenType

fun Lexer.getRelPos(line: Int): Position {
    if (tokens.size == 0 || tokens.last().kind != TokenType.EndOfFile) {
        incLine()
    }
    return lines[line - 1]
}

fun Lexer.getCurrentLinePos() = Position(start = lastLine, end = linePos)
