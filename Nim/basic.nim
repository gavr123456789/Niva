import std/strutils
import std/parseutils

{.experimental: "strictNotNil".}
{.push warning[ProveField]: on.}


type 
  TokenType = enum 
    INTEGER, PLUS, MINUS, EOF
  Token = object 
    case kind: TokenType
    of INTEGER: value: int
    of PLUS: discard
    of MINUS: discard
    of EOF: discard

func toString(self: Token): string =
  case self.kind
  of INTEGER:
    "Token(" & $self.kind & ", " & $self.value & ")"
  else:
    "Token(" & $self.kind & ")"
  
type Interpreter = object 
  text: string
  pos: int
  current_token: Token
  current_char: char
  

proc error(self: Interpreter)  =
  raise newException(OSError, "parsing error")


proc advance(self: var Interpreter): void =
  self.pos += 1
  if self.pos > (len(self.text) - 1):
    self.current_char = '\\'
  else:
    self.current_char = self.text[self.pos]


proc skip_whitespace(self: var Interpreter) =
  while self.current_char != '\\' and self.current_char.isSpaceAscii :
    self.advance()


proc integer(self: var Interpreter): string =
  while self.current_char != '\\' and self.current_char.isDigit :
    result &= self.current_char
    self.advance()


proc get_next_token(self: var Interpreter): Token =
  var text = self.text
  let current_char = self.current_char

  while self.current_char != '\\':
    if current_char.isSpaceAscii():
      self.skip_whitespace()
      continue
    
    if current_char.isDigit:
      return Token(kind: INTEGER, value: (self.integer()).parseInt)

    if current_char == '+':
      self.advance()
      return Token(kind: PLUS)
    if current_char == '-':
      self.advance()
      return Token(kind: MINUS)

    self.error
  
  return Token(kind: EOF)


proc eat(self: var Interpreter, tokenKind: TokenType): void =
  if self.current_token.kind == tokenKind:
    self.current_token = self.get_next_token
  else:
    echo "error: ", self.current_token, ", ", tokenKind
    self.error


proc expr(self: var Interpreter): int =

  # INTEGER PLUS INTEGER
  # INTEGER MINUS INTEGER

  self.current_token = self.get_next_token

  let left = self.current_token
  self.eat INTEGER

  let op = self.current_token
  if op.kind == PLUS:
    self.eat PLUS
  else:
    self.eat MINUS


  let right = self.current_token
  self.eat INTEGER

  echo "left = ", left
  echo "r = ", right

  result = case left.kind
    of INTEGER:
      case right.kind:
      of INTEGER:
        case op.kind
        of PLUS:
          left.value + right.value
        of MINUS:
          left.value - right.value
        else:
          0
      else:
        0
    else:
      0
    

proc main() =
  while true:
    let text = stdin.readLine
    if text == "q":
      break;
    
    var interpreter = Interpreter(text: text)
    let res = interpreter.expr
    echo "res = ",  res

main()