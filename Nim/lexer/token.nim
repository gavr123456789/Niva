import std/strformat
import std/strutils


type TokenType* {.pure.} = enum
  # Literals
  True, False
  Integer, Float
  String, Char
  Identifier
  # Operators
  Equal
  # Others
  EndOfFile
  Symbol # A generic symbol
  NoMatch # Used internally by the symbol table

type Token* = ref object
  kind*: TokenType              # Type of the token
  lexeme*: string               # The lexeme associated to the token
  line*: int                    # The line where the token appears
  pos*: tuple[start, stop: int] # The absolute position in the source file
                                # (0-indexed and inclusive at the beginning)
  spaces*: int                  # Number of spaces before this token

proc endOfFileToken*(line: int, current: int): Token = 
  Token(kind: EndOfFile, lexeme: "", line: line, pos: (current, current))


proc `$`*(self: Token): string =
  ## Strinfifies
  if self != nil:
    result = &"lexeme={self.lexeme.escape()}, Token(kind={self.kind}, line={self.line}, pos=({self.pos.start}, {self.pos.stop}), spaces={self.spaces})"
  else:
    result = "nil"

proc `$`*(tokens: seq[Token]): string =
  ## Strinfifies
  for tok in tokens:
    result.add $tok
    result.add "\n"

proc `==`*(a, b: Token): bool =
  a.kind == b.kind and a.lexeme == b.lexeme