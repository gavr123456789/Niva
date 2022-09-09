import std/strutils
import std/parseutils
{.experimental: "strictNotNil".}

type 
  TokenType = enum 
    INTEGER, PLUS, EOF, MINUS, DEL, MUL
  Token {. requiresInit .} = object 
    case kind: TokenType
    of INTEGER: value: int
    of PLUS: discard
    of MINUS: discard
    of DEL: discard
    of MUL: discard
    of EOF: discard

func IntegerToken(value: int): Token =
  Token(kind: INTEGER, value: value)
func PlusToken(): Token =
  Token(kind: PLUS)
func MinusToken(): Token =
  Token(kind: MINUS)
func MulToken(): Token =
  Token(kind: MUL)
func DelToken(): Token =
  Token(kind: DEL)
func EofToken(): Token =
  Token(kind: EOF)


# func toString(self: Token): string =
#   "Tonen(" & $self.kind & ", " & $self.value & ")"
  


type Interpreter {. requiresInit .} = object 
  text: string
  pos: int
  current_token: Token
  current_char: char

func initInterpreter(text: static string): Interpreter =
  static: assert text != ""
  Interpreter(
    pos: 0, 
    text: text, 
    current_char: text[0], 
    current_token: EofToken()
  )
  # result = Interpreter(
  #   pos: 0, 
  #   text: text, 
  #   current_char: text[0], 
  #   current_token: EofToken()
  # )


proc error(self: Interpreter)  =
  let errorMessage = "parsing error, current interpreter = " & $self
  raise newException(OSError, errorMessage)

# Advance the 'pos' pointer and set the 'current_char' variable
proc advance(self: var Interpreter): void =
  self.pos += 1
  if self.pos > len(self.text) - 1:
    self.current_char = '\\' # indicates end
    echo "EOF"
    echo "self pos = ", self.pos
    echo len(self.text) - 1
  else:
    self.current_char = self.text[self.pos]


# Щагаем пока там пробелы
proc skip_whitespace(self: var Interpreter) =
  while self.current_char != '\\' and self.current_char.isSpaceAscii() :
    self.advance()

# Щагаем пока там цифры, набирая эти цифры в результ
proc integer(self: var Interpreter): int =
  var stringResult: string = ""
  while self.current_char != '\\' and self.current_char.isDigit() :
    stringResult &= self.current_char
    # echo "str result = ", stringResult
    self.advance()
  echo "final str result = ", stringResult
  # echo "str len = ", stringResult.len(), "\n"
  result = parseInt(stringResult)



# Токенайзер, бъет текст на токены
proc get_next_token(self: var Interpreter): Token =
  while self.current_char != '\\':
    let current_char = self.current_char
    
    if current_char.isSpaceAscii():
      self.skip_whitespace()
      continue

    if current_char.isDigit():
      return self.integer().IntegerToken()
      

    if current_char == '+':
      self.advance()
      return PlusToken()
      

    if current_char == '-':
      self.advance()
      return MinusToken()
  
    if current_char == '*':
      self.advance()
      return MulToken()
      

    if current_char == '/':
      self.advance()
      return DelToken()

    # Произошло чтото странное
    self.error()
  return EofToken()


# Забирает следующий токен такого то типа
proc eat(self: var Interpreter, tokenKind: TokenType): void =
  if self.current_token.kind == tokenKind:
    self.current_token = self.get_next_token()
  else:
    echo "error: ", self.current_token, ", ", tokenKind
    self.error()


proc expr(self: var Interpreter): int =
  self.current_token = self.get_next_token()

  let left = self.current_token
  self.eat INTEGER

  let op = self.current_token
  case op.kind:
  of PLUS:
    self.eat PLUS
  of MINUS:
    self.eat MINUS
  of MUL:
    self.eat MUL
  of DEL:
    self.eat DEL
  else: 
    discard


  let right = self.current_token
  self.eat INTEGER

  # here current token will be EOF

  echo "left token = ", left
  echo "right token = ", right

  result = case op.kind:
  of PLUS:
    left.value + right.value
  of MINUS:
    left.value - right.value
  of MUL:
    left.value * right.value
  of DEL:
    left.value /% right.value
  else: 
    0


proc main() =
  # while true:
  #   let text = stdin.readLine
  #   if text == "q":
  #     break;
    
    var interpreter = initInterpreter("13 * 2")
    let res = interpreter.expr
    echo "res = ",  res

main()