import std/[strutils, parseutils, strformat, tables]
import token
import symbolTable

type
    Lexer* = ref object
        ## A lexer object
        symbols*: SymbolTable
        source*: string
        tokens*: seq[Token]
        line*: int
        start*: int
        current*: int
        file*: string
        lines*: seq[tuple[start, stop: int]]
        lastLine*: int
        spaces*: int
    LexingError* = ref object of CatchableError
        ## A lexing error
        lexer*: Lexer
        file*: string
        lexeme*: string
        line*: int