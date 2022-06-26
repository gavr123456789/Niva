import test from 'ava';
import { codeDB, generateNimCode } from '../src/niva';
import {  isDebug } from '../src/utils';

isDebug.isDebug = false

// CODEGEN

// Assigment
test('assignment statement', t => {
  const code = 'x = 5. y = 6. z =  7'
  const [_,nimCode] = generateNimCode(code)

  t.is("var x: int = 5\nvar y: int = 6\nvar z: int = 7", nimCode)
});

// Asigment same variables names
// test('Asigment same variables names', t => {
//   const code = 'x = 5.\nx = 6.'
//   const [_statementList,_nimCode, errors] = generateNimCode(code)
  
//   const varError = errors[0]
//   t.truthy(varError)
//   if (!varError) {
//     throw new Error("there no redefinition error");
//   }

//   t.is("RedefinitionOfVariableError", varError.errorKind)

// });

// Expressions



test('Expressions Unary messages', t => {
  const code = '42 echo'
  const [_,nimCode] = generateNimCode(code)

  t.is("42.`echo`()", nimCode)
});

test('Expressions Unary messages two times', t => {
  const code = '42 toString toString'
  const [_,nimCode] = generateNimCode(code)

  t.is("42.`toString`().`toString`()", nimCode)
});

// Binary

test('Expressions Binary messages', t => {
  const code = '1 + 2'
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`+`(2)", nimCode)
});
test('Expressions Binary messages many', t => {
  const code = '1 + 2 + 3' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`+`(2).`+`(3)", nimCode)
});

// Keyword

test('Expressions Keyword messages', t => {
  const code = '1 to: 2 do: [1 echo]'
  const [_, nimCode] = generateNimCode(code)

  t.is(`1.to_do(2):
  1.\`echo\`()`, nimCode)
});

// Combined 
test('Expressions Unary with Binary', t => {
  const code = '1 toString & 2 toString'
  const [_s, nimCode] = generateNimCode(code)
  t.is("1.`toString`().`&`(2.`toString`())", nimCode)
});

test('Expressions two Unary with Binary', t => {
  const code = '1 toString & 2 toString.\n1 toString & 2 toString.'
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`toString`().`&`(2.`toString`())\n1.`toString`().`&`(2.`toString`())", nimCode)
});

test('Expressions Unary with Binary many', t => {
  const code = `1 toString & 2 toString toString toString`
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`toString`().`&`(2.`toString`().`toString`().`toString`())", nimCode)
});

// TYPE DECLARATION
test('Type Declaration', t => {
  const code = 'type Person name: string age: int' 
  const [_, nimCode] = generateNimCode(code)

  t.is("type Person = object\n  name: string\n  age: int\n", nimCode)
});


test('two Type Declaration', t => {
  const code = 'type Person name: string age: int.\ntype AnotherPerson name: string age: int' 
  const [_, nimCode] = generateNimCode(code)

  t.is("type Person = object\n  name: string\n  age: int\n\ntype AnotherPerson = object\n  name: string\n  age: int\n", nimCode)
});


// MESSAGE DECLARATION
// unary
test('Method Declaration Unary no retrun type', t => {
  const code = '-Person sas = [ x echo ]' 
  const [_s, nimCode] = generateNimCode(code)
  
  t.is("proc sas(self: Person): auto =\n  x.`echo`()", nimCode)
});

test('two Method Declaration Unary', t => {
  const code = 'int sas = [ x echo ].\nint sus = [ x echo ].'
  const [_s, nimCode] = generateNimCode(code)
  
  t.is("proc sas(self: int): auto =\n  x.`echo`()\nproc sus(self: int): auto =\n  x.`echo`()", nimCode)
});

test('Method Declaration Unary with retrun type', t => {
  const code = '-int sas -> int = [ 5 ]'
  const [_, nimCode] = generateNimCode(code)

  t.is("proc sas(self: int): int =\n  5", nimCode)
});


test('Method Declaration Binary typed', t => {
  const code = '-Person + x::int -> void = [ x echo ]' 
  const [_, nimCode] = generateNimCode(code)

  t.is("proc `+`(self: Person, x: int): void =\n  x.`echo`()", nimCode)
});

test('Method Declaration Binary untyped', t => {
  const code = '-Person + x = [ x echo ]' 
  const [_, nimCode] = generateNimCode(code)
  codeDB
  t.is("proc `+`(self: Person, x: auto): auto =\n  x.`echo`()", nimCode)
});

// Keyword
test('Method Declaration Keyword typed', t => {
  const code = '-Person from: x::int to: y::int -> void = [ x echo ]' 
  const [_, nimCode] = generateNimCode(code)

  t.is("proc from_to(self: Person, x: int, y: int): void =\n  x.`echo`()", nimCode)
});

test('Method Declaration Keyword untyped', t => {
  const code = '-Person from: x to: y = [ x echo ]' 
  const [_, nimCode] = generateNimCode(code)

  t.is("proc from_to(self: Person, x: auto, y: auto): auto =\n  x.`echo`()", nimCode)
});

// Brackets

test('Brackets expression', t => {
  const code = '(1 + 2) echo.' 
  const [_, nimCode] = generateNimCode(code)

  t.is("(1.`+`(2)).`echo`()", nimCode)
});

test('Brackets after expression', t => {
  const code = '1 + (1 - 1)' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`+`((1.`-`(1)))", nimCode)
});

// Switch
test('Switch Expression assigment with else', t => {
  const code = `| x > 4 => "sas" echo
  | x < 4 => "sus" echo.`
  const [_, nimCode] = generateNimCode(code)
  const result = 
`if x.\`>\`(4):
  "sas".\`echo\`()
elif x.\`<\`(4):
  "sus".\`echo\`()`
  
  t.is(result, nimCode)
});

// Switch Statement


test('Switch Statement', t => {
  const code = 
  `
  x 
  | 5 => "x = 5" echo
  | 7 => "x = 7" echo
  |=> "not 5, not 7" echo`
  const [_, nimCode] = generateNimCode(code)
  const result = 
`case x:
of 5:
  "x = 5".\`echo\`()
of 7:
  "x = 7".\`echo\`()
else:
  "not 5, not 7".\`echo\`()`
  
  t.is(result, nimCode)
});


test('to:do: loop', t => {
  const code =
    `
square = 1. increment = 3. door = 0.

1 to: 100 do: [
  ("door №" & it toString) echo
  | it == square =>
    [
      square    add: increment.
      increment add: 2
    ]
  |=> "is closed" echo
].`
  const [_, nimCode] = generateNimCode(code)
  const result =
    `var square: int = 1
var increment: int = 3
var door: int = 0
1.to_do(100):
  ("door №".\`&\`(it.\`toString\`())).\`echo\`()
  if it.\`==\`(square):
    square.add(increment)
    increment.add(2)
  else:
    "is closed".\`echo\`()`

  t.is(result, nimCode)
});