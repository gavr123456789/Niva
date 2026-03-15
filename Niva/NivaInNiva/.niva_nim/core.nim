import std/[algorithm, options, random, sequtils, sets, strutils, tables, unicode]

randomize()

type
  NivaUnit* = object

  IntRange* = object
    start*: int
    endInclusive*: int

  CharRange* = object
    start*: char
    endInclusive*: char

let Unit* = NivaUnit()

proc throwWithMessage*(msg: string) {.noreturn.} =
  raise newException(ValueError, msg)

proc Error_throw*[T](self: T) {.noreturn.} =
  raise newException(ValueError, $self)

proc Error_throwWithMsg*[T](self: T, msg: string) {.noreturn.} =
  discard self
  raise newException(ValueError, msg)

iterator iterateIntRange(range: IntRange): int =
  if range.start <= range.endInclusive:
    for i in range.start .. range.endInclusive:
      yield i

iterator iterateCharRange(range: CharRange): char =
  let startCode = ord(range.start)
  let endCode = ord(range.endInclusive)
  if startCode <= endCode:
    for code in startCode .. endCode:
      yield chr(code)

proc Int_inc*(self: int): int =
  self + 1

proc Int_dec*(self: int): int =
  self - 1

proc Int_toFloat*(self: int): float32 =
  float32(self)

proc Int_toDouble*(self: int): float64 =
  float64(self)

proc Int_toLong*(self: int): int64 =
  int64(self)

proc Int_toChar*(self: int): char =
  chr(self and 0xFF)

proc Int_toDo*(self: int, to: int, fn: proc(it: int) {.closure.}) =
  for i in self .. to:
    fn(i)

proc Int_downToDo*(self: int, downTo: int, fn: proc(it: int) {.closure.}) =
  for i in countdown(self, downTo):
    fn(i)

proc Int_rangeTo*(self: int, to: int): IntRange =
  IntRange(start: self, endInclusive: to)

proc Int_plus*(self, other: int): int =
  self + other

proc Int_minus*(self, other: int): int =
  self - other

proc Int_times*(self, other: int): int =
  self * other

proc Int_div*(self, other: int): int =
  self div other

proc Int_rem*(self, other: int): int =
  self mod other

proc Int_gt*(self, other: int): bool =
  self > other

proc Int_lt*(self, other: int): bool =
  self < other

proc Int_gte*(self, other: int): bool =
  self >= other

proc Int_lte*(self, other: int): bool =
  self <= other

proc Int_equals*(self, other: int): bool =
  self == other

proc Int_notEquals*(self, other: int): bool =
  self != other

proc IntRange_forEach*(self: IntRange, fn: proc(it: int) {.closure.}) =
  for value in iterateIntRange(self):
    fn(value)

proc IntRange_map*[R](self: IntRange, fn: proc(it: int): R {.closure.}): seq[R] =
  for value in iterateIntRange(self):
    result.add(fn(value))

proc IntRange_filter*(self: IntRange, fn: proc(it: int): bool {.closure.}): seq[int] =
  for value in iterateIntRange(self):
    if fn(value):
      result.add(value)

proc IntRange_contains*(self: IntRange, value: int): bool =
  value >= self.start and value <= self.endInclusive

proc IntRange_isEmpty*(self: IntRange): bool =
  self.start > self.endInclusive

proc CharRange_forEach*(self: CharRange, fn: proc(it: char) {.closure.}) =
  for value in iterateCharRange(self):
    fn(value)

proc CharRange_map*[R](self: CharRange, fn: proc(it: char): R {.closure.}): seq[R] =
  for value in iterateCharRange(self):
    result.add(fn(value))

proc CharRange_filter*(self: CharRange, fn: proc(it: char): bool {.closure.}): seq[char] =
  for value in iterateCharRange(self):
    if fn(value):
      result.add(value)

proc CharRange_contains*(self: CharRange, value: char): bool =
  let valueCode = ord(value)
  let startCode = ord(self.start)
  let endCode = ord(self.endInclusive)
  valueCode >= startCode and valueCode <= endCode

proc CharRange_isEmpty*(self: CharRange): bool =
  ord(self.start) > ord(self.endInclusive)

proc Nullable_unpackOrPANIC*[T](self: Option[T]): T =
  if self.isNone:
    throwWithMessage("Nullable_unpackOrPANIC on null")
  self.get

proc Nullable_unpack*[T](self: Option[T], fn: proc(v: T) {.closure.}) =
  if self.isSome:
    fn(self.get)

proc Nullable_unpackOr*[T, R](self: Option[T], fn: proc(v: T): R {.closure.}, orValue: R): R =
  if self.isSome:
    fn(self.get)
  else:
    orValue

proc Nullable_unpackOrValue*[T](self: Option[T], v: T): T =
  if self.isSome: self.get else: v

proc Nullable_unpackOrPANIC*[T](self: T): T =
  self

proc Nullable_unpack*[T](self: T, fn: proc(v: T) {.closure.}) =
  fn(self)

proc Nullable_unpackOr*[T, R](self: T, fn: proc(v: T): R {.closure.}, orValue: R): R =
  discard orValue
  fn(self)

proc Nullable_unpackOrValue*[T](self: T, v: T): T =
  discard v
  self

proc Any_toString*[T](value: T, indent: int = 0): string

proc seqToString[T](value: seq[T], indent: int): string =
  let space = "    ".repeat(indent)
  if value.len == 0:
    return "{}"
  var buf = "{\n"
  for item in value:
    buf.add(space)
    buf.add("    ")
    buf.add(Any_toString(item, indent + 1))
    buf.add("\n")
  buf.add(space)
  buf.add("}")
  buf

proc setToString[T](value: HashSet[T], indent: int): string =
  let space = "    ".repeat(indent)
  if value.len == 0:
    return "Set {}"
  var buf = "#(\n"
  for item in value:
    buf.add(space)
    buf.add("    ")
    buf.add(Any_toString(item, indent + 1))
    buf.add(",\n")
  buf.add(space)
  buf.add(")")
  buf

proc tableToString[K, V](value: Table[K, V], indent: int): string =
  let space = "    ".repeat(indent)
  if value.len == 0:
    return "Map {}"
  var buf = "#{\n"
  for key, val in value:
    buf.add(space)
    buf.add("    ")
    buf.add(Any_toString(key, indent + 1))
    buf.add(" => ")
    buf.add(Any_toString(val, indent + 1))
    buf.add(",\n")
  buf.add(space)
  buf.add("}")
  buf

proc Any_toString*[T](value: T, indent: int = 0): string =
  when T is NivaUnit:
    "()"
  elif T is string:
    "\"" & value & "\""
  elif T is seq:
    seqToString(value, indent)
  elif T is HashSet:
    setToString(value, indent)
  elif T is Table:
    tableToString(value, indent)
  elif T is Option:
    if value.isSome:
      Any_toString(value.get, indent)
    else:
      "null"
  else:
    $value

proc Any_echo*[T](obj: T) =
  echo Any_toString(obj)

proc String_uppercase*(str: string): string =
  str.toUpperAscii

proc String_reversed*(str: string): string =
  str.reversed

proc String_count*(str: string): int =
  str.len

proc String_trim*(str: string): string =
  str.strip

proc String_isEmpty*(str: string): bool =
  str.len == 0

proc String_isNotEmpty*(str: string): bool =
  str.len != 0

proc String_isBlank*(str: string): bool =
  str.strip.len == 0

proc String_isNotBlank*(str: string): bool =
  str.strip.len != 0

proc String_toFloat*(str: string): float32 =
  parseFloat(str).float32

proc String_toDouble*(str: string): float64 =
  parseFloat(str)

proc String_lowercase*(str: string): string =
  str.toLowerAscii

proc String_first*(str: string): char =
  if str.len == 0:
    throwWithMessage("String is empty")
  str[0]

proc String_last*(str: string): char =
  if str.len == 0:
    throwWithMessage("String is empty")
  str[^1]

proc String_at*(str: string, index: int): char =
  if index < 0 or index >= str.len:
    throwWithMessage("Index buf of bounds")
  str[index]

proc String_get*(str: string, index: int): char =
  String_at(str, index)

proc String_split*(str: string, delimiter: string): seq[string] =
  str.split(delimiter)

proc String_substring*(str: string, start: int, `end`: int): string =
  let a = clamp(start, 0, str.len)
  let b = clamp(`end`, 0, str.len)
  if a <= b:
    str[a ..< b]
  else:
    str[b ..< a]

proc String_drop*(str: string, count: int): string =
  if count <= 0:
    return str
  if count >= str.len:
    return ""
  str[count .. ^1]

proc String_dropLast*(str: string, count: int): string =
  if count <= 0:
    return str
  if count >= str.len:
    return ""
  str[0 ..< (str.len - count)]

proc String_contains*(str: string, substring: string): bool =
  str.contains(substring)

proc String_replace_with*(str: string, oldVal: string, newVal: string): string =
  str.replace(oldVal, newVal)

proc String_map*(str: string, callback: proc(ch: char): char {.closure.}): string =
  var buf = newStringOfCap(str.len)
  for ch in str:
    buf.add(callback(ch))
  buf

proc String_map*(str: string, callback: proc(ch: char, index: int, original: string): char {.closure.}): string =
  var buf = newStringOfCap(str.len)
  for i, ch in str:
    buf.add(callback(ch, i, str))
  buf

proc String_forEach*(str: string, fn: proc(ch: char) {.closure.}) =
  for ch in str:
    fn(ch)

proc String_filter*(str: string, predicate: proc(ch: char): bool {.closure.}): string =
  var buf = newStringOfCap(str.len)
  for ch in str:
    if predicate(ch):
      buf.add(ch)
  buf

proc digitValue(ch: char): int =
  if ch in {'0' .. '9'}:
    return ord(ch) - ord('0')
  if ch in {'a' .. 'z'}:
    return ord(ch) - ord('a') + 10
  if ch in {'A' .. 'Z'}:
    return ord(ch) - ord('A') + 10
  -1

proc String_toInt*(self: string, radix: int = 10): int =
  if radix < 2 or radix > 36:
    throwWithMessage("Invalid radix: " & $radix)

  if self.len == 0:
    throwWithMessage("Invalid integer format: \"\"")

  var negative = false
  var startIdx = 0

  if self[0] == '-' or self[0] == '+':
    negative = self[0] == '-'
    startIdx = 1
    if self.len == 1:
      throwWithMessage("Invalid integer format: \"" & self & "\"")

  var acc = 0'i64
  for i in startIdx ..< self.len:
    let d = digitValue(self[i])
    if d < 0 or d >= radix:
      throwWithMessage("Invalid integer format: \"" & self & "\"")
    acc = acc * radix.int64 + d.int64

  if negative:
    acc = -acc

  if acc < -2147483648'i64 or acc > 2147483647'i64:
    throwWithMessage("Integer overflow: \"" & self & "\"")

  acc.int

proc Collection_joinTransform*[C, T](self: C, transform: proc(it: T): string {.closure.}): string =
  var first = true
  var buf = ""
  for it in self:
    if not first:
      buf.add(", ")
    first = false
    buf.add(transform(it))
  buf

proc Collection_joinWith*[C, T](self: C, sep: string): string =
  var first = true
  var buf = ""
  for it in self:
    if not first:
      buf.add(sep)
    first = false
    buf.add($it)
  buf

proc Collection_joinWithTransform*[C, T](self: C, sep: string, transform: proc(it: T): string {.closure.}): string =
  var first = true
  var buf = ""
  for it in self:
    if not first:
      buf.add(sep)
    first = false
    buf.add(transform(it))
  buf

proc Char_toInt*(ch: char): int =
  ord(ch)

proc Char_toInt*(ch: char, base: int): int =
  let d = digitValue(ch)
  if d < 0 or d >= base:
    throwWithMessage("Invalid digit for base")
  d

proc Bool_toString*(self: bool): string =
  if self: "true" else: "false"

proc Bool_not*(self: bool): bool =
  not self

proc Bool_isTrue*(self: bool): bool =
  self

proc Bool_isFalse*(self: bool): bool =
  not self

proc Bool_or*(self: bool, other: bool): bool =
  self or other

proc Bool_and*(self: bool, other: bool): bool =
  self and other

proc Bool_ifTrue*(self: bool, fn: proc() {.closure.}) =
  if self:
    fn()

proc Bool_ifFalse*(self: bool, fn: proc() {.closure.}) =
  if not self:
    fn()

proc Bool_ifTrue_ifFalse*[T](self: bool, blockTrue: proc(): T {.closure.}, blockFalse: proc(): T {.closure.}): T =
  if self:
    blockTrue()
  else:
    blockFalse()

proc Bool_ifFalse_ifTrue*[T](self: bool, blockFalse: proc(): T {.closure.}, blockTrue: proc(): T {.closure.}): T =
  if self:
    blockTrue()
  else:
    blockFalse()

proc Char_code*(self: char): int =
  ord(self)

proc Char_inc*(self: char): char =
  chr((ord(self) + 1) and 0xFF)

proc Char_dec*(self: char): char =
  chr((ord(self) - 1) and 0xFF)

proc Char_isDigit*(self: char): bool =
  self in {'0' .. '9'}

proc Char_isLetter*(self: char): bool =
  (self in {'A' .. 'Z'}) or (self in {'a' .. 'z'})

proc Char_isLetterOrDigit*(self: char): bool =
  Char_isLetter(self) or Char_isDigit(self)

proc Char_isLowerCase*(self: char): bool =
  self in {'a' .. 'z'}

proc Char_isUpperCase*(self: char): bool =
  self in {'A' .. 'Z'}

proc Char_isWhitespace*(self: char): bool =
  self in {' ', '\t', '\n', '\r', '\f', '\v'}

proc Char_lowercaseChar*(self: char): char =
  toLowerAscii(self)

proc Char_uppercaseChar*(self: char): char =
  toUpperAscii(self)

proc Char_rangeTo*(self: char, to: char): CharRange =
  CharRange(start: self, endInclusive: to)

proc Char_equals*(self, other: char): bool =
  self == other

proc Char_notEquals*(self, other: char): bool =
  self != other

proc whileTrue*(cond: proc(): bool {.closure.}, body: proc() {.closure.}) =
  while cond():
    body()

proc List_count*[T](self: seq[T]): int =
  self.len

proc List_first*[T](self: seq[T]): T =
  if self.len == 0:
    throwWithMessage("List is empty")
  self[0]

proc List_last*[T](self: seq[T]): T =
  if self.len == 0:
    throwWithMessage("List is empty")
  self[^1]

proc List_firstOrNull*[T](self: seq[T]): Option[T] =
  if self.len == 0: none(T) else: some(self[0])

proc List_lastOrNull*[T](self: seq[T]): Option[T] =
  if self.len == 0: none(T) else: some(self[^1])

proc List_toList*[T](self: seq[T]): seq[T] =
  self

proc List_toMutableList*[T](self: seq[T]): seq[T] =
  self

proc List_shuffled*[T](self: seq[T]): seq[T] =
  result = self
  shuffle(result)

proc List_asSequence*[T](self: seq[T]): seq[T] =
  self

proc List_isEmpty*[T](self: seq[T]): bool =
  self.len == 0

proc List_toSet*[T](self: seq[T]): HashSet[T] =
  toHashSet(self)

proc List_isNotEmpty*[T](self: seq[T]): bool =
  self.len != 0

proc List_reversed*[T](self: seq[T]): seq[T] =
  result = self
  reverse(result)

proc List_sum*[T: SomeNumber](self: seq[T]): T =
  for x in self:
    result += x

proc List_plus*[T](self: seq[T], other: seq[T]): seq[T] =
  result = self
  result.add(other)

proc List_plus*[T](self: seq[T], other: T): seq[T] =
  result = self
  result.add(other)

proc List_minus*[T](self: seq[T], other: seq[T]): seq[T] =
  let otherSet = toHashSet(other)
  for x in self:
    if x notin otherSet:
      result.add(x)

proc List_minus*[T](self: seq[T], other: T): seq[T] =
  for x in self:
    if x != other:
      result.add(x)

proc List_forEach*[T](self: seq[T], fn: proc(it: T) {.closure.}) =
  for it in self:
    fn(it)

proc List_onEach*[T](self: seq[T], fn: proc(it: T) {.closure.}): seq[T] =
  for it in self:
    fn(it)
  self

proc List_forEachIndexed*[T](self: seq[T], fn: proc(index: int, it: T) {.closure.}) =
  for i, it in self:
    fn(i, it)

proc List_map*[T, R](self: seq[T], fn: proc(it: T): R {.closure.}): seq[R] =
  for it in self:
    result.add(fn(it))

proc List_mapIndexed*[T, R](self: seq[T], fn: proc(index: int, it: T): R {.closure.}): seq[R] =
  for i, it in self:
    result.add(fn(i, it))

proc List_filter*[T](self: seq[T], fn: proc(it: T): bool {.closure.}): seq[T] =
  for it in self:
    if fn(it):
      result.add(it)

proc List_at*[T](self: seq[T], index: int): T =
  if index < 0 or index >= self.len:
    throwWithMessage("Index buf of bounds")
  self[index]

proc List_getOrNull*[T](self: seq[T], index: int): Option[T] =
  if index < 0 or index >= self.len:
    none(T)
  else:
    some(self[index])

proc List_contains*[T](self: seq[T], element: T): bool =
  element in self

proc List_drop*[T](self: seq[T], n: int): seq[T] =
  if n <= 0:
    return self
  if n >= self.len:
    return @[]
  self[n .. ^1]

proc List_dropLast*[T](self: seq[T], n: int): seq[T] =
  if n <= 0:
    return self
  if n >= self.len:
    return @[]
  self[0 ..< (self.len - n)]

proc List_chunked*[T](self: seq[T], size: int): seq[seq[T]] =
  if size <= 0:
    throwWithMessage("chunk size must be > 0")
  var i = 0
  while i < self.len:
    result.add(self[i ..< min(i + size, self.len)])
    i += size

proc List_joinToString*[T](self: seq[T], separator: string): string =
  self.join(separator)

proc List_joinToString*[T](self: seq[T], transform: proc(it: T): string {.closure.}): string =
  self.map(transform).join(", ")

proc List_indexOfFirst*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): int =
  for i, it in self:
    if predicate(it):
      return i
  -1

proc List_indexOfLast*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): int =
  for i in countdown(self.len - 1, 0):
    if predicate(self[i]):
      return i
  -1

proc List_sortedBy*[T, R](self: seq[T], transform: proc(it: T): R {.closure.}): seq[T] =
  result = self
  result.sort(proc(a, b: T): int = cmp(transform(a), transform(b)))

proc List_joinWithTransform*[T](self: seq[T], separator: string, transform: proc(it: T): string {.closure.}): string =
  self.map(transform).join(separator)

proc List_joinWith*[T](self: seq[T], separator: string): string =
  self.join(separator)

proc List_injectInto*[T, A](self: seq[T], initial: A, operation: proc(acc: A, item: T): A {.closure.}): A =
  var acc = initial
  for it in self:
    acc = operation(acc, it)
  acc

proc List_reduce*[T](self: seq[T], operation: proc(acc: T, item: T): T {.closure.}): T =
  if self.len == 0:
    throwWithMessage("Empty list cannot be reduced")
  var acc = self[0]
  for i in 1 ..< self.len:
    acc = operation(acc, self[i])
  acc

proc List_partition*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): seq[seq[T]] =
  var trueList: seq[T] = @[]
  var falseList: seq[T] = @[]
  for it in self:
    if predicate(it):
      trueList.add(it)
    else:
      falseList.add(it)
  @[trueList, falseList]

proc List_sumOf*[T, R: SomeNumber](self: seq[T], selector: proc(it: T): R {.closure.}): R =
  for it in self:
    result += selector(it)

proc List_find*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): Option[T] =
  for it in self:
    if predicate(it):
      return some(it)
  none(T)

proc List_viewFromTo*[T](self: seq[T], `from`: int, `to`: int): seq[T] =
  let a = clamp(`from`, 0, self.len)
  let b = clamp(`to`, 0, self.len)
  if a <= b:
    self[a ..< b]
  else:
    @[]

proc List_mut_clear*[T](self: var seq[T]) =
  self.setLen(0)

proc List_mut_add*[T](self: var seq[T], arg1: T) =
  self.add(arg1)

proc List_mut_add*[T](self: var seq[T], arg1: int, arg2: T) =
  self.insert(arg2, arg1)

proc List_mut_addFirst*[T](self: var seq[T], item: T) =
  self.insert(item, 0)

proc List_mut_addAll*[T](self: var seq[T], other: seq[T]) =
  self.add(other)

proc List_mut_removeAt*[T](self: var seq[T], index: int) =
  if index >= 0 and index < self.len:
    self.delete(index)

proc List_mut_remove*[T](self: var seq[T], item: T): bool =
  for i, it in self:
    if it == item:
      self.delete(i)
      return true
  false

proc List_mut_set*[T](self: var seq[T], index: int, item: T) =
  if index < 0 or index >= self.len:
    throwWithMessage("Index buf of bounds")
  self[index] = item

proc Set_count*[T](self: HashSet[T]): int =
  self.len

proc Set_mut_clear*[T](self: var HashSet[T]) =
  self.clear()

proc Set_first*[T](self: HashSet[T]): T =
  if self.len == 0:
    throwWithMessage("Set is empty")
  for it in self:
    return it
  throwWithMessage("Set is empty")

proc Set_last*[T](self: HashSet[T]): T =
  if self.len == 0:
    throwWithMessage("Set is empty")
  for it in self:
    result = it

proc Set_toList*[T](self: HashSet[T]): seq[T] =
  toSeq(self)

proc Set_toMutableList*[T](self: HashSet[T]): seq[T] =
  toSeq(self)

proc Set_toMutableSet*[T](self: HashSet[T]): HashSet[T] =
  self

proc Set_toSet*[T](self: HashSet[T]): HashSet[T] =
  self

proc Set_plus*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] =
  result = self
  for item in other:
    result.incl(item)

proc Set_minus*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] =
  result = self
  for item in other:
    result.excl(item)

proc Set_forEach*[T](self: HashSet[T], fn: proc(it: T) {.closure.}) =
  for it in self:
    fn(it)

proc Set_onEach*[T](self: HashSet[T], fn: proc(it: T) {.closure.}): HashSet[T] =
  for it in self:
    fn(it)
  self

proc Set_map*[T, R](self: HashSet[T], fn: proc(it: T): R {.closure.}): seq[R] =
  for it in self:
    result.add(fn(it))

proc Set_mapIndexed*[T, R](self: HashSet[T], fn: proc(index: int, it: T): R {.closure.}): seq[R] =
  var index = 0
  for it in self:
    result.add(fn(index, it))
    inc index

proc Set_filter*[T](self: HashSet[T], fn: proc(it: T): bool {.closure.}): HashSet[T] =
  for it in self:
    if fn(it):
      result.incl(it)

proc Set_intersect*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] =
  for it in self:
    if it in other:
      result.incl(it)

proc Set_contains*[T](self: HashSet[T], item: T): bool =
  item in self

proc Set_containsAll*[T](self: HashSet[T], other: HashSet[T]): bool =
  for it in other:
    if it notin self:
      return false
  true

proc Set_mut_add*[T](self: var HashSet[T], item: T) =
  self.incl(item)

proc Set_mut_remove*[T](self: var HashSet[T], item: T): bool =
  let had = item in self
  self.excl(item)
  had

proc Set_mut_addAll*[T](self: var HashSet[T], other: HashSet[T]): bool =
  for it in other:
    self.incl(it)
  true

proc Map_count*[K, V](self: Table[K, V]): int =
  self.len

proc Map_isEmpty*[K, V](self: Table[K, V]): bool =
  self.len == 0

proc Map_isNotEmpty*[K, V](self: Table[K, V]): bool =
  self.len != 0

proc Map_keys*[K, V](self: Table[K, V]): HashSet[K] =
  for k in self.keys:
    result.incl(k)

proc Map_values*[K, V](self: Table[K, V]): HashSet[V] =
  for v in self.values:
    result.incl(v)

proc Map_toMap*[K, V](self: Table[K, V]): Table[K, V] =
  self

proc Map_toMutableMap*[K, V](self: Table[K, V]): Table[K, V] =
  self

proc Map_plus*[K, V](self: Table[K, V], other: Table[K, V]): Table[K, V] =
  result = self
  for key, value in other:
    result[key] = value

proc Map_minus*[K, V](self: Table[K, V], key: K): Table[K, V] =
  result = self
  result.del(key)

proc Map_forEach*[K, V](self: Table[K, V], fn: proc(key: K, value: V) {.closure.}) =
  for key, value in self:
    fn(key, value)

proc Map_map*[K, V, R](self: Table[K, V], fn: proc(key: K, value: V): R {.closure.}): seq[R] =
  for key, value in self:
    result.add(fn(key, value))

proc Map_filter*[K, V](self: Table[K, V], fn: proc(key: K, value: V): bool {.closure.}): Table[K, V] =
  for key, value in self:
    if fn(key, value):
      result[key] = value

proc Map_at*[K, V](self: Table[K, V], key: K): V =
  if self.hasKey(key):
    self[key]
  else:
    default(V)

proc Map_containsKey*[K, V](self: Table[K, V], key: K): bool =
  self.hasKey(key)

proc Map_containsValue*[K, V](self: Table[K, V], value: V): bool =
  for v in self.values:
    if v == value:
      return true
  false

proc Map_mut_clear*[K, V](self: var Table[K, V]) =
  self.clear()

proc Map_mut_remove*[K, V](self: var Table[K, V], key: K): bool =
  let had = self.hasKey(key)
  self.del(key)
  had

proc Map_mut_putAll*[K, V](self: var Table[K, V], other: Table[K, V]) =
  for key, value in other:
    self[key] = value

proc Map_mut_atPut*[K, V](self: var Table[K, V], key: K, value: V) =
  self[key] = value

proc Map_mut_getOrPut*[K, V](self: var Table[K, V], key: K, fn: proc(): V {.closure.}): V =
  if self.hasKey(key):
    return self[key]
  let value = fn()
  self[key] = value
  value

proc Map_mut_putIfAbsent*[K, V](self: var Table[K, V], key: K, value: V) =
  if not self.hasKey(key):
    self[key] = value

proc List_mut_count*[T](self: seq[T]): int = List_count(self)
proc List_mut_first*[T](self: seq[T]): T = List_first(self)
proc List_mut_last*[T](self: seq[T]): T = List_last(self)
proc List_mut_firstOrNull*[T](self: seq[T]): Option[T] = List_firstOrNull(self)
proc List_mut_lastOrNull*[T](self: seq[T]): Option[T] = List_lastOrNull(self)
proc List_mut_toList*[T](self: seq[T]): seq[T] = List_toList(self)
proc List_mut_toMutableList*[T](self: seq[T]): seq[T] = List_toMutableList(self)
proc List_mut_shuffled*[T](self: seq[T]): seq[T] = List_shuffled(self)
proc List_mut_asSequence*[T](self: seq[T]): seq[T] = List_asSequence(self)
proc List_mut_isEmpty*[T](self: seq[T]): bool = List_isEmpty(self)
proc List_mut_toSet*[T](self: seq[T]): HashSet[T] = List_toSet(self)
proc List_mut_isNotEmpty*[T](self: seq[T]): bool = List_isNotEmpty(self)
proc List_mut_reversed*[T](self: seq[T]): seq[T] = List_reversed(self)
proc List_mut_sum*[T: SomeNumber](self: seq[T]): T = List_sum(self)
proc List_mut_plus*[T](self: seq[T], other: seq[T]): seq[T] = List_plus(self, other)
proc List_mut_plus*[T](self: seq[T], other: T): seq[T] = List_plus(self, other)
proc List_mut_minus*[T](self: seq[T], other: seq[T]): seq[T] = List_minus(self, other)
proc List_mut_minus*[T](self: seq[T], other: T): seq[T] = List_minus(self, other)
proc List_mut_forEach*[T](self: seq[T], fn: proc(it: T) {.closure.}) = List_forEach(self, fn)
proc List_mut_onEach*[T](self: seq[T], fn: proc(it: T) {.closure.}): seq[T] = List_onEach(self, fn)
proc List_mut_forEachIndexed*[T](self: seq[T], fn: proc(index: int, it: T) {.closure.}) = List_forEachIndexed(self, fn)
proc List_mut_map*[T, R](self: seq[T], fn: proc(it: T): R {.closure.}): seq[R] = List_map(self, fn)
proc List_mut_mapIndexed*[T, R](self: seq[T], fn: proc(index: int, it: T): R {.closure.}): seq[R] = List_mapIndexed(self, fn)
proc List_mut_filter*[T](self: seq[T], fn: proc(it: T): bool {.closure.}): seq[T] = List_filter(self, fn)
proc List_mut_at*[T](self: seq[T], index: int): T = List_at(self, index)
proc List_mut_getOrNull*[T](self: seq[T], index: int): Option[T] = List_getOrNull(self, index)
proc List_mut_contains*[T](self: seq[T], element: T): bool = List_contains(self, element)
proc List_mut_drop*[T](self: seq[T], n: int): seq[T] = List_drop(self, n)
proc List_mut_dropLast*[T](self: seq[T], n: int): seq[T] = List_dropLast(self, n)
proc List_mut_chunked*[T](self: seq[T], size: int): seq[seq[T]] = List_chunked(self, size)
proc List_mut_joinToString*[T](self: seq[T], separator: string): string = List_joinToString(self, separator)
proc List_mut_joinToString*[T](self: seq[T], transform: proc(it: T): string {.closure.}): string = List_joinToString(self, transform)
proc List_mut_indexOfFirst*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): int = List_indexOfFirst(self, predicate)
proc List_mut_indexOfLast*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): int = List_indexOfLast(self, predicate)
proc List_mut_sortedBy*[T, R](self: seq[T], transform: proc(it: T): R {.closure.}): seq[T] = List_sortedBy(self, transform)
proc List_mut_joinWithTransform*[T](self: seq[T], separator: string, transform: proc(it: T): string {.closure.}): string = List_joinWithTransform(self, separator, transform)
proc List_mut_injectInto*[T, A](self: seq[T], initial: A, operation: proc(acc: A, item: T): A {.closure.}): A = List_injectInto(self, initial, operation)
proc List_mut_reduce*[T](self: seq[T], operation: proc(acc: T, item: T): T {.closure.}): T = List_reduce(self, operation)
proc List_mut_partition*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): seq[seq[T]] = List_partition(self, predicate)
proc List_mut_sumOf*[T, R: SomeNumber](self: seq[T], selector: proc(it: T): R {.closure.}): R = List_sumOf(self, selector)
proc List_mut_find*[T](self: seq[T], predicate: proc(it: T): bool {.closure.}): Option[T] = List_find(self, predicate)
proc List_mut_viewFromTo*[T](self: seq[T], `from`: int, `to`: int): seq[T] = List_viewFromTo(self, `from`, `to`)

proc Set_mut_count*[T](self: HashSet[T]): int = Set_count(self)
proc Set_mut_first*[T](self: HashSet[T]): T = Set_first(self)
proc Set_mut_last*[T](self: HashSet[T]): T = Set_last(self)
proc Set_mut_toList*[T](self: HashSet[T]): seq[T] = Set_toList(self)
proc Set_mut_toMutableList*[T](self: HashSet[T]): seq[T] = Set_toMutableList(self)
proc Set_mut_toMutableSet*[T](self: HashSet[T]): HashSet[T] = Set_toMutableSet(self)
proc Set_mut_toSet*[T](self: HashSet[T]): HashSet[T] = Set_toSet(self)
proc Set_mut_plus*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] = Set_plus(self, other)
proc Set_mut_minus*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] = Set_minus(self, other)
proc Set_mut_forEach*[T](self: HashSet[T], fn: proc(it: T) {.closure.}) = Set_forEach(self, fn)
proc Set_mut_onEach*[T](self: HashSet[T], fn: proc(it: T) {.closure.}): HashSet[T] = Set_onEach(self, fn)
proc Set_mut_map*[T, R](self: HashSet[T], fn: proc(it: T): R {.closure.}): seq[R] = Set_map(self, fn)
proc Set_mut_mapIndexed*[T, R](self: HashSet[T], fn: proc(index: int, it: T): R {.closure.}): seq[R] = Set_mapIndexed(self, fn)
proc Set_mut_filter*[T](self: HashSet[T], fn: proc(it: T): bool {.closure.}): HashSet[T] = Set_filter(self, fn)
proc Set_mut_intersect*[T](self: HashSet[T], other: HashSet[T]): HashSet[T] = Set_intersect(self, other)
proc Set_mut_contains*[T](self: HashSet[T], item: T): bool = Set_contains(self, item)
proc Set_mut_containsAll*[T](self: HashSet[T], other: HashSet[T]): bool = Set_containsAll(self, other)

proc Map_mut_count*[K, V](self: Table[K, V]): int = Map_count(self)
proc Map_mut_isEmpty*[K, V](self: Table[K, V]): bool = Map_isEmpty(self)
proc Map_mut_isNotEmpty*[K, V](self: Table[K, V]): bool = Map_isNotEmpty(self)
proc Map_mut_keys*[K, V](self: Table[K, V]): HashSet[K] = Map_keys(self)
proc Map_mut_values*[K, V](self: Table[K, V]): HashSet[V] = Map_values(self)
proc Map_mut_toMap*[K, V](self: Table[K, V]): Table[K, V] = Map_toMap(self)
proc Map_mut_toMutableMap*[K, V](self: Table[K, V]): Table[K, V] = Map_toMutableMap(self)
proc Map_mut_plus*[K, V](self: Table[K, V], other: Table[K, V]): Table[K, V] = Map_plus(self, other)
proc Map_mut_minus*[K, V](self: Table[K, V], key: K): Table[K, V] = Map_minus(self, key)
proc Map_mut_forEach*[K, V](self: Table[K, V], fn: proc(key: K, value: V) {.closure.}) = Map_forEach(self, fn)
proc Map_mut_map*[K, V, R](self: Table[K, V], fn: proc(key: K, value: V): R {.closure.}): seq[R] = Map_map(self, fn)
proc Map_mut_filter*[K, V](self: Table[K, V], fn: proc(key: K, value: V): bool {.closure.}): Table[K, V] = Map_filter(self, fn)
proc Map_mut_at*[K, V](self: Table[K, V], key: K): V = Map_at(self, key)
proc Map_mut_containsKey*[K, V](self: Table[K, V], key: K): bool = Map_containsKey(self, key)
proc Map_mut_containsValue*[K, V](self: Table[K, V], value: V): bool = Map_containsValue(self, value)
