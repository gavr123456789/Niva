import std/strutils
import std/parseutils
{.experimental: "strictNotNil".}

type 
  TokenType = enum 
    INTEGER, PLUS, EOF
  Token = object 
    case kind: TokenType
    of INTEGER: value: int
    of PLUS: discard
    of EOF: discard


func toString(self: Token): string =
  "Tonen(" & $self.kind & ", " & $self.value & ")"
  

type Interpreter = object 
  text: string
  pos: int
  current_token: Token


proc error(self: Interpreter)  =
  raise newException(OSError, "parsing error")

# Токенайзер, бъет текст на токены
proc get_next_token(self: var Interpreter): Token =
  var text = self.text

  # Если строка кончилась то EOF
  if self.pos > (len(text) - 1):
    return Token(kind: EOF)

  var current_char = text[self.pos]
  
  if current_char.isDigit:
    self.pos += 1
    return Token(kind: INTEGER, value: ($current_char).parseInt)

  if current_char == '+':
    self.pos += 1
    return Token(kind: PLUS)

  # Произошло чтото странное
  self.error()


var eatCounter = 0


# Забирает следующий токен такого то типа
proc eat(self: var Interpreter, tokenKind: TokenType): void =
  eatCounter += 1
  if self.current_token.kind == tokenKind:
    self.current_token = self.get_next_token
  else:
    echo "error: ", self.current_token, ", ", tokenKind
    self.error


proc expr(self: var Interpreter): int =
  self.current_token = self.get_next_token

  let left = self.current_token
  self.eat INTEGER

  # let op = self.current_token
  self.eat PLUS

  let right = self.current_token
  self.eat INTEGER

  echo "left = ", left
  echo "r = ", right

  result = left.value + right.value


proc main() =
  while true:
    let text = stdin.readLine
    if text == "q":
      break;
    
    var interpreter = Interpreter(text: text)
    let res = interpreter.expr
    echo "res = ",  res

main()