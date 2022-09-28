# read file .niva
# run lexer on it
# run parser
# run code generator


###

import std/strutils
import std/parseutils
{.experimental: "strictNotNil".}

type 
  TokenType = enum 
    INTEGER, EOF, IDENTIFIER, EQUAL, SPACES
  Token {. requiresInit .} = object 
    kind*: TokenType
    lexeme*: string
    # pos*: tuple[start, stop: int]
    # line*: int
  
proc IntegerToken(data: int): Token = 
  Token(kind: INTEGER, lexeme: $data)
proc EqualToken(): Token = 
  Token(kind: EQUAL, lexeme: "")
proc EOFToken(): Token = 
  Token(kind: EOF, lexeme: "")

type Parser {. requiresInit .} = object 
  text: string
  pos: int
  current_token: Token
  current_char: char

func initParser(text: static string): Parser =
  static: assert text != ""
  Parser(
    pos: 0, 
    text: text, 
    current_char: text[0], 
    current_token: Token(kind: EOF, lexeme: "")
  )


proc error(self: Parser)  =
  let errorMessage = "parsing error, current Parser = " & $self
  raise newException(OSError, errorMessage)

# step the 'pos' pointer and set the 'current_char' variable
proc step(self: var Parser): void =
  self.pos += 1
  if self.pos > len(self.text) - 1:
    self.current_char = '\\' # indicates end
    echo "EOF"
    echo "self pos = ", self.pos
    echo len(self.text) - 1
  else:
    self.current_char = self.text[self.pos]


# Щагаем пока там пробелы
proc skip_whitespace(self: var Parser) =
  while self.current_char != '\\' and self.current_char.isSpaceAscii() :
    self.step()

# Щагаем пока там цифры, набирая эти цифры в результ
proc integer(self: var Parser): int =
  var stringResult: string = ""
  while self.current_char != '\\' and self.current_char.isDigit() :
    stringResult &= self.current_char
    # echo "str result = ", stringResult
    self.step()
  echo "final str result = ", stringResult
  # echo "str len = ", stringResult.len(), "\n"
  result = parseInt(stringResult)

# Токенайзер, бъет текст на токены
proc get_next_token(self: var Parser): Token =
  while self.current_char != '\\':
    let current_char = self.current_char
    
    if current_char.isSpaceAscii():
      self.skip_whitespace()
      continue

    if current_char.isDigit():
      return self.integer().IntegerToken()
      
    if current_char == '=':
      self.step()
      return EqualToken()

    # Произошло чтото странное
    self.error()
  return EofToken()


# Забирает следующий токен такого то типа
proc eat(self: var Parser, tokenKind: TokenType): void =
  if self.current_token.kind == tokenKind:
    self.current_token = self.get_next_token()
  else:
    echo "error: ", self.current_token, ", ", tokenKind
    self.error()



proc declaration(self: var Parser): int =
  self.current_token = self.get_next_token()

  self.eat IDENTIFIER
  let ident = self.current_token

  self.eat SPACES
  self.eat SPACES
  let rightPart = self.literal()


  case self.current_token.kind:
    of IDENTIFIER:
      discard
    of INTEGER: 
      discard
    of EQUAL: 
      discard
    of SPACES:
      discard
    of EOF: 
      discard
  


  # let left = self.current_token
  # self.eat INTEGER

  # let op = self.current_token
  # case op.kind:
  # of PLUS:
  #   self.eat PLUS
  # of MINUS:
  #   self.eat MINUS
  # of MUL:
  #   self.eat MUL
  # of DEL:
  #   self.eat DEL
  # else: 
  #   discard


  # let right = self.current_token
  # self.eat INTEGER

  # # here current token will be EOF

  # echo "left token = ", left
  # echo "right token = ", right

  # result = case op.kind:
  # of PLUS:
  #   left.value + right.value
  # of MINUS:
  #   left.value - right.value
  # of MUL:
  #   left.value * right.value
  # of DEL:
  #   left.value /% right.value
  # else: 
  #   0


proc main() =
  # while true:
  #   let text = stdin.readLine
  #   if text == "q":
  #     break;
    
    var Parser = initParser("x =  2")
    let res = Parser.declaration()
    echo "res = ",  res

main()
###