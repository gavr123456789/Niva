import std/[strutils, parseutils, strformat, tables]
import token

type SymbolTable* = ref object
  ## A table of symbols used
  ## to lex a source file

  # Although we don't parse keywords
  # as symbols, but rather as identifiers,
  # we keep them here for consistency
  # purposes
  keywords*: TableRef[string, TokenType]
  symbols*: TableRef[string, TokenType]


proc newSymbolTable*: SymbolTable =
  ## Initializes a new symbol table
  new(result)
  result.keywords = newTable[string, TokenType]()
  result.symbols = newTable[string, TokenType]()


proc getMaxSymbolSize*(self: SymbolTable): int =
  ## Returns the maximum length of all the symbols
  ## currently in the table. Note that keywords are
  ## not symbols, they're identifiers (or at least
  ## are parsed the same way in Lexer.parseIdentifier)
  for lexeme in self.symbols.keys():
    if len(lexeme) > result:
      result = len(lexeme)


proc getSymbols*(self: SymbolTable, n: int): seq[string] =
  ## Returns all n-bytes symbols
  ## in the symbol table
  for lexeme in self.symbols.keys():
    if len(lexeme) == n:
      result.add(lexeme)


proc existsKeyword*(self: SymbolTable, lexeme: string): bool {.inline.} =
  ## Returns true if a given keyword exists
  ## in the symbol table already
  lexeme in self.keywords