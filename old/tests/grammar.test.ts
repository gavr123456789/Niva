import test, { ExecutionContext } from 'ava';
import { generateNimCode } from '../src/niva';
import {  grammarMatch, isDebug } from '../src/utils';

isDebug.isDebug = false


function match(t: ExecutionContext<unknown>, code: string, startRule: string = "statement") {
  const matchedGrammar = grammarMatch(code, startRule)
  if (matchedGrammar.failed()) {
    console.error(matchedGrammar.message);
  } 
  t.is(matchedGrammar.succeeded(), true)
 
}
// Messages
test('binary message send', t => {
  match(t, "5 + 5", "expression")
  match(t, "5 ++ 5", "expression")
  match(t, "5 |> 5", "expression")
});

test('unary message send', t => {
  match(t, "\"reverce this!\" reverce", "expression")
  match(t, "5 factorial", "expression")
});

// Code blocks
test('code block', t => {
  match(t, "[ 5 factorial ]", "expression")
});
test('multy-line code block', t => {
  match(t, "[\n 5 factorial \n]", "expression")
});
test('empty code block', t => {
  match(t, "[\n\n]")
});

// Switch Expression
test('Switch Expression code block', t => {
  match(t, "| x > 5 => [ x echo ]\n| x < 5 => y", "switchExpression")
});

test('Switch Expression return only, with else', t => {
  const code = `-int sas: x = [ 
    x =
    | 4 => 5
    | 3 => 7
    |=> 5
  ]`
  match(t, code)
});

test('Switch Expression with else', t => {
  const code = `| sas > 100 => "sas > 100" echo
| sas < 40  => "sas < 90" echo
|=> "sas between 40 and 100" echo`
  match(t, code)
});



// Methods declaration
test('Methods declaration, unary typed', t => {
  match(t, "-Person x -> int = [ x sas ]")
});
test('Methods declaration, unary untyped', t => {
  match(t, "-Person from: a to: b = [ x sas ]")
});

test('Methods declaration, binary typed', t => {
  match(t, "-int + x::int -> int = [ x sas ]")
});
test('Methods declaration, binary untyped', t => {
  match(t, "-int + x -> int = [ x sas ]")
});

test('Methods declaration, keyword with type with local name', t => {
  match(t, "int from: y::int, to: x::string = 4")
});
test('Methods declaration, keyword with type no local name', t => {
  match(t, "int from::int, to::string = 4")
});
test('Methods declaration, keyword no type with local name', t => {
  match(t, "int from: y = 5")
});
test('Methods declaration, keyword no type no local name', t => {
  match(t, "int :x :y :z = 8")
});

// Type declaration
test('Type declaration', t => {
  match(t, "type Person name: string job: string", "typeDeclaration")
});

// Nested type
test('Nested Type declaration', t => {
  match(t, "type P x: int y: (type O z: int)", "typeDeclaration")
});

// Union type
test('Union type declaration', t => {
  match(t, `union Fig = 
| a, r => x: string
| q => s: qwe`)
});



// Message call


test('Methods declaration, keyword typed', t => {
  match(t, "-Person from: a::int to: b::int  -> int = [ x sas ]")
});
test('Methods declaration, keyword untyped', t => {
  match(t, "-Person from: a to: b = [ x sas ]")
});

// Literals
test('List Literal', t => {
  match(t, "{1, 2, 3}", "listLiteral")
});

test('Block', t => {
  match(t, "[1 sas]", "blockConstructor")
});

// Others
test('Others typedProperties', t => {
  match(t, "age: int name: string", "typedProperties")
});

test('module statement', t => {
  match(t, "module sas")
});

test('use statement', t => {
  match(t, "use sas")
});

