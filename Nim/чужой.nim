import std/[strutils, strformat, re]

type
  ParserError* = object of CatchableError
  ParserKind = enum
    pkString,
    pkRegexp,
    pkSequence

  Parser = ref object
    case kind: ParserKind
    of pkString:
      target: string
    of pkRegexp:
      regexp: Regex
      definition: string
    of pkSequence:
      parsers: seq[Parser]

proc parseString(target: string): Parser =
  Parser(kind: pkString, target: target)

proc parseRegexp(target: string): Parser =
  Parser(kind: pkRegexp, regexp: target.re, definition: target)

proc then(p1: Parser, p2: Parser): Parser =
  Parser(kind: pkSequence, parsers: @[p1, p2])

proc map[T](target: string, op: proc (s: string): T): T =
  op(target)

proc parse(p: Parser, input: string): seq[string] =
  case p.kind
    of pkString:
      if input.startsWith(p.target):
        return @[p.target]
      raise newException(ParserError, fmt"Parser failure: <{p.target}> not found")
    of pkRegexp:
      let res = input.findAll(p.regexp)
      if input.startsWith(p.regexp):
        return @[res[0]]
      raise newException(ParserError, fmt"Parser failure: <{p.definition}> not found")
    of pkSequence:
      let r1 = parse(p.parsers[0], input)[0]
      let input2 = input.substr(r1.len, input.len-1)
      let r2 = parse(p.parsers[1], input2)[0]
      return @[r1, r2]


when isMainModule:
  let fooParser = parseString("foo")
  let barParser = parseString("bar")
  echo fooParser.parse("foobar")
  echo barParser.parse("bar !!")
  let intParser = parseRegexp("[0-9]+")
  echo intParser.parse("123")[0].map(parseInt)
  
  let fooBarParser = fooParser.then(barParser)
  echo fooBarParser.parse("foobar")
  let fooIntParser = fooParser.then(intParser)
  echo fooIntParser.parse("foo123")