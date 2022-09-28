import std/[strutils, parseutils, strformat, tables]
import token
import lexerHelpers
import types
import symbolTable

proc incLine(self: Lexer) =
  ## Increments the lexer's line
  ## counter and updates internal 
  ## line metadata
  self.lines.add((self.lastLine, self.current))
  self.lastLine = self.current
  self.line += 1


proc newLexer*(self: Lexer = nil): Lexer =
  ## Initializes the lexer or resets
  ## the state of an existing one
  new(result)
  if self != nil:
      result = self
  result.source = ""
  result.tokens = @[]
  result.line = 1
  result.start = 0
  result.current = 0
  result.file = ""
  result.lines = @[]
  result.lastLine = 0
  result.symbols = newSymbolTable()
  result.spaces = 0

### Tokens
proc createToken(self: Lexer, tokenType: TokenType) =
  ## Creates a token object and adds it to the token
  ## list. The lexeme and position of the token are
  ## inferred from the current state of the tokenizer
  self.spaces = 0
  self.tokens.add Token(
    kind: tokenType,
    lexeme: self.source[self.start..<self.current],
    line: self.line,
    spaces: self.spaces,
    pos: (start: self.start, stop: self.current - 1)
  )

### Stepping Lexer

proc done(self: Lexer): bool =
    ## Returns true if we reached EOF
    result = self.current >= self.source.len

proc peek(self: Lexer, distance: int = 0, length: int = 1): string =
  ## Returns a stream of characters of
  ## at most length bytes from the source
  ## file, starting at the given distance,
  ## without consuming it. The distance
  ## parameter may be negative to retrieve
  ## previously consumed tokens. If the
  ## distance and/or the length are beyond
  ## EOF (even partially), the resulting string
  ## will be shorter than length bytes. The string
  ## may be empty
  var i = distance
  while len(result) < length:
    if self.done() or self.current + i > self.source.high() or self.current + i < 0:
      break
    else:
      result.add(self.source[self.current + i])
    inc(i)


proc error(self: Lexer, message: string) =
  ## Raises a lexing error with info
  ## for error messages
  raise LexingError(msg: message, line: self.line, file: self.file, lexeme: self.peek(), lexer: self)

# проверяет что следующая строка совподает с предоставленной не перематывая лексер
proc check(self: Lexer, s: string, distance: int = 0): bool =
  ## Behaves like self.match(), without consuming the
  ## token. False is returned if we're at EOF
  ## regardless of what the token to check is.
  ## The distance is passed directly to self.peek()
  if self.done():
    return false
  
  return self.peek(distance, len(s)) == s

proc checkMany(self: Lexer, args: openarray[string], distance: int = 0): bool =
  ## Calls self.check() in a loop with
  ## each character from the given set of
  ## strings and returns at the first match.
  ## Useful to check multiple tokens in a situation
  ## where only one of them may match at one time
  for s in args:
    if self.check(s, distance):
      return true
  return false


# проматывает лексер на н символов
proc step(self: Lexer, n: int = 1): string =
  ## Steps n characters forward in the
  ## source file (default = 1). A string
  ## of at most n bytes is returned. If n
  ## exceeds EOF, the string will be shorter
  while len(result) < n:
    if self.done() or self.current > self.source.high():
      break
    else:
      result.add(self.source[self.current])
    inc(self.current)

# true если следующие символы матчатся на s
proc match(self: Lexer, s: string): bool =
  if not self.check(s):
    return false
  discard self.step(len(s))
  return true
proc matchMany(self: Lexer, args: openarray[string]): bool =
  for s in args:
    if self.match(s):
      return true
  return false

### Parsing things
proc parseNumber(self: Lexer) =
  var kind: TokenType = Integer
  # проматываем цифры до точки
  while isDigit(self.peek()) and not self.done():
    discard self.step()
  
  if self.check("."):
    kind = Integer
    # пропускаем точку 
    discard self.step()
    # после точки нет цифр
    if not isDigit(self.peek()):
      self.error("invalid float number literal, you must put number after dot")
    # проматываем цифры после точки
    while isDigit(self.peek()) and not self.done():
      discard self.step()

  self.createToken(kind)

proc parseEscape(self: Lexer) =
  # Boring escape sequence parsing. For more info check out
  # https://en.wikipedia.org/wiki/Escape_sequences_in_C.
  # As of now, \u and \U are not supported, but they'll
  # likely be soon. Another notable limitation is that
  # \xhhh and \nnn are limited to the size of a char
  # (i.e. uint8, or 256 values)
  case self.peek()[0]: # We use a char instead of a string because of how case statements handle ranges with strings
                       # (i.e. not well, given they crash the C code generator)
    of 'a':
      self.source[self.current] = cast[char](0x07)
    of 'b':
      self.source[self.current] = cast[char](0x7f)
    of 'e':
      self.source[self.current] = cast[char](0x1B)
    of 'f':
      self.source[self.current] = cast[char](0x0C)
    of 'n':
      when defined(windows):
        # We natively convert LF to CRLF on Windows, and
        # gotta thank Microsoft for the extra boilerplate!
        self.source[self.current] = cast[char](0x0D)
        self.source.insert(self.current + 1, 0X0A)
      when defined(darwin):
        # Thanks apple, lol
        self.source[self.current] = cast[char](0x0A)
      when defined(linux):
        self.source[self.current] = cast[char](0X0D)
    of 'r':
      self.source[self.current] = cast[char](0x0D)
    of 't':
      self.source[self.current] = cast[char](0x09)
    of 'v':
      self.source[self.current] = cast[char](0x0B)
    of '"':
      self.source[self.current] = '"'
    of '\'':
      self.source[self.current] = '\''
    of '\\':
      self.source[self.current] = cast[char](0x5C)
    of '0'..'9': # This is the reason we're using char instead of string. See https://github.com/nim-lang/Nim/issues/19678
      var code = ""
      var value = 0
      var i = self.current
      while i < self.source.high() and (let c = self.source[i].toLowerAscii(); c in '0'..'7') and len(code) < 3:
        code &= self.source[i]
        i += 1
      assert parseOct(code, value) == code.len()
      if value > uint8.high().int:
        self.error("escape sequence value too large (> 255)")
      self.source[self.current] = cast[char](value)
    of 'u', 'U':
      self.error("unicode escape sequences are not supported (yet)")
    of 'x':
      var code = ""
      var value = 0
      var i = self.current
      while i < self.source.high() and (let c = self.source[i].toLowerAscii(); c in 'a'..'f' or c in '0'..'9'):
        code &= self.source[i]
        i += 1
      assert parseHex(code, value) == code.len()
      if value > uint8.high().int:
        self.error("escape sequence value too large (> 255)")
      self.source[self.current] = cast[char](value)
    else:
      self.error(&"invalid escape sequence '\\{self.peek()}'")

proc parseString(self: Lexer, delimiter: string, mode: string = "single") =
  echo "delimiter = ", delimiter
  echo "mode = ", mode
  ## Parses string literals. They can be expressed using matching pairs
  ## of either single or double quotes. Most C-style escape sequences are
  ## supported, moreover, a specific prefix may be prepended
  ## to the string to instruct the lexer on how to parse it:
  ## - b -> declares a byte string, where each character is
  ##     interpreted as an integer instead of a character
  ## - r -> declares a raw string literal, where escape sequences
  ##     are not parsed and stay as-is
  ## - f -> declares a format string, where variables may be
  ##     interpolated using curly braces like f"Hello, {name}!".
  ##     Braces may be escaped using a pair of them, so to represent
  ##     a literal "{" in an f-string, one would use {{ instead
  ## Multi-line strings can be declared using matching triplets of
  ## either single or double quotes. They can span across multiple
  ## lines and escape sequences in them are not parsed, like in raw
  ## strings, so a multi-line string prefixed with the "r" modifier
  ## is redundant, although multi-line byte/format strings are supported
  var slen = 0
  while not self.check(delimiter) and not self.done():
    if self.match("\n"):
      if mode == "multi":
        self.incLine()
      else:
        self.error("unexpected EOL while parsing string literal")
    
    if mode in ["raw", "multi"]:
      discard self.step()
    elif self.match("\\"):
      # This madness here serves to get rid of the slash, since \x is mapped
      # to a one-byte sequence but the string '\x' is actually 2 bytes (or more,
      # depending on the specific escape sequence)
      self.source = self.source[0..<self.current] & self.source[self.current + 1..^1]
      self.parseEscape()

    if mode == "format" and self.match("{"):
      if self.match("{"):
        self.source = self.source[0..<self.current] & self.source[self.current + 1..^1]
        continue
      while not self.checkMany(["}", "\""]):
        discard self.step()
      if self.check("\""):
        self.error("unclosed '{' in format string")
    elif mode == "format" and self.check("}"):
      if not self.check("}", 1):
        self.error("unmatched '}' in format string")
      else:
        self.source = self.source[0..<self.current] & self.source[self.current + 1..^1]
    discard self.step()
    inc(slen)
    if slen > 1 and delimiter == "'":
      self.error("invalid character literal (length must be one!)")

  if mode == "multi":
    if not self.match(delimiter.repeat(3)):
      self.error("unexpected EOL while parsing multi-line string literal")
  elif self.done() and self.peek(-1) != delimiter:
    self.error("unexpected EOF while parsing string literal")
  else:
    discard self.step()
  if delimiter == "\"":
    self.createToken(String)
  else:
    self.createToken(Char)

proc parseIdentifier(self: Lexer) =
  ## Parses keywords and identifiers.
  ## Note that multi-character tokens
  ## (aka UTF runes) are not supported
  ## by design and *will* break things
  while (self.peek().isAlphaNumeric() or self.check("_")) and not self.done():
    discard self.step()
  let name: string = self.source[self.start..<self.current]
  if self.symbols.existsKeyword(name):
    # It's a keyword!
    self.createToken(self.symbols.keywords[name])
  else:
    # It's an identifier!
    self.createToken(Identifier)

### main part

proc getToken(self: Lexer, lexeme: string): Token =
  ## Gets the matching token object for a given
  ## string according to the symbol table or
  ## returns nil if there's no match
  let table = self.symbols
  var kind = table.symbols.getOrDefault(lexeme, table.keywords.getOrDefault(lexeme, NoMatch))
  if kind == NoMatch:
    return nil
  new(result)
  result.kind = kind
  result.lexeme = self.source[self.start..<self.current]
  result.line = self.line
  result.pos = (start: self.start, stop: self.current)

proc next(self: Lexer) =
  if self.done(): 
    return
  elif self.matchMany(["\r", "\f", "\e"]):
    # We skip characters we don't need
    return
  elif self.match " ":
    self.spaces.inc()
  elif self.match "\n":
    self.incLine()
  elif self.match("\r"):
    self.error("tabs are not allowed in peon code")
  elif self.matchMany(["\"", "'"]):
    # String or character literal
    var mode = "single"
    if self.peek(-1) != "'" and 
       self.check(self.peek(-1)) and 
       self.check(self.peek(-1), 1):

      # Multiline strings start with 3 quotes
      discard self.step(2)
      mode = "multi"
    
    self.parseString(self.peek(-1), mode)
  elif self.peek().isDigit():
    # discard self.step() # Needed because parseNumber reads the next
                          # character to tell the base of the number
    self.parseNumber()
  elif self.peek().isAlphaNumeric() or self.check("_"):
    # Keywords and identifiers
    self.parseIdentifier()
  else:
    # If none of the above conditions matched, there's a few
    # other options left:
    # - The token is a built-in operator, or
    # - it's an expression/statement delimiter, or
    # - it's not a valid token at all
    # We handle all of these cases here by trying to
    # match the longest sequence of characters possible
    # as either an operator or a statement/expression
    # delimiter
    var n = self.symbols.getMaxSymbolSize()
    while n > 0:
      for symbol in self.symbols.getSymbols(n):
        if self.match(symbol):
          # We've found the largest possible match!
          self.tokens.add(self.getToken(symbol))
          return
      dec(n)
    # We just assume what we have in front of us
    # is a symbol
    discard self.step()
    self.createToken(Symbol)


proc lex*(self: Lexer, source, fileName: string): seq[Token] = 
  var symbols = self.symbols
  discard self.newLexer()
  self.symbols = symbols
  self.source = source
  self.file = fileName
  self.lines = @[]

  while not self.done():
    self.next()
    self.start = self.current
  self.tokens.add(endOfFileToken(self.line, self.current))
  self.incLine()
  return self.tokens

when isMainModule:
  let 
    # source = """ 's' 8.67 3 r "sas" """
    source = """x = 23"""
    file = "sas.file"
    lexer = newLexer()
    tokens = lexer.lex(source, file)
  echo tokens