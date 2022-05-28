import test, { ExecutionContext } from 'ava';
import { RedefinitionOfVariableError } from './Errors/Error';
import { generateNimCode } from './niva';
import { echo, grammarMatch, isDebug } from './utils';

isDebug.isDebug = false


function match(t: ExecutionContext<unknown>, code: string, startRule: string = "statement") {
  const matchedGrammar = grammarMatch(code, startRule)
  if (matchedGrammar.failed()) {
    console.error(matchedGrammar.message);
  } 
  t.is(matchedGrammar.succeeded(), true)
 
}

// GRAMMAR

// Messages
test('Grammar binary message send', t => {
  match(t, "5 + 5", "expression")
  match(t, "5 ++ 5", "expression")
  match(t, "5 |> 5", "expression")
});

test('Grammar unary message send', t => {
  match(t, "\"reverce this!\" reverce", "expression")
  match(t, "5 factorial", "expression")
});

// Code blocks
test('Grammar code block', t => {
  match(t, "[ 5 factorial ]", "expression")
});
test('Grammar multy-line code block', t => {
  match(t, "[\n 5 factorial \n]", "expression")
});
test('Grammar empty code block', t => {
  match(t, "[\n\n]")
});

// Methods declaration
test('Grammar Methods declaration, unary', t => {
  match(t, "Person die -> int = [ x sas ]")
});
test('Grammar Methods declaration, binary', t => {
  match(t, "Person + x::int -> int = [ x sas ]")
});
test('Grammar Methods declaration, keyword', t => {
  match(t, "Person from: a::int to: b::int  -> int = [ x sas ]")
});

// Type declaration
test('Grammar Type declaration', t => {
  match(t, "type Person name: string job: string", "typeDeclaration")
});

// Nested type
test('Grammar Nested Type declaration', t => {
  match(t, "type P x: int y: (type O z: int)", "typeDeclaration")
});


// Message call

test('Grammar Methods call, unary typed', t => {
  match(t, "Person x -> int = [ x sas ]")
});
test('Grammar Methods call, unary untyped', t => {
  match(t, "Person from: a to: b = [ x sas ]")
});

test('Grammar Methods call, binary typed', t => {
  match(t, "int + x::int -> int = [ x sas ]")
});
test('Grammar Methods call, binary untyped', t => {
  match(t, "int + x -> int = [ x sas ]")
});

test('Grammar Methods call, keyword typed', t => {
  match(t, "Person from: a::int to: b::int  -> int = [ x sas ]")
});
test('Grammar Methods call, keyword untyped', t => {
  match(t, "Person from: a to: b = [ x sas ]")
});

// Literals
test('Grammar List Literal', t => {
  match(t, "[1,2, 3]", "listLiteral")
});

test('Grammar Block', t => {
  match(t, "[1 sas]", "blockConstructor")
});

// Others
test('Grammar Others typedProperties', t => {
  match(t, "age: int name: string", "typedProperties")
});



// CODEGEN

// Assigment
test('Codegen assignment statement', t => {
  const code = 'x = 5. y = 6. z =  7'
  const [_,nimCode] = generateNimCode(code)

  t.is("let x = 5\nlet y = 6\nlet z = 7", nimCode)
});

// Asigment same variables names
test('Codegen Asigment same variables names', t => {
  const code = 'x = 5.\nx = 6.'
  const [_statementList,_nimCode, errors] = generateNimCode(code)
  
  const varError = errors[0]
  t.truthy(varError)
  if (!varError) {
    throw new Error("there no redefinition error");
  }

  t.is("RedefinitionOfVariableError", varError.errorKind)
  // console.log("variable ", varError.variableName, " from " );
  // console.log(varError.lineAndColMessage);
  // console.log("was already defined in ");
  // console.log(varError.previousLineAndColMessage);

});

// Expressions



test('Codegen Expressions Unary messages', t => {
  const code = '42 echo'
  const [_,nimCode] = generateNimCode(code)

  t.is("42.echo()", nimCode)
});

test('Codegen Expressions Unary messages two times', t => {
  const code = '42 sas sas'
  const [_,nimCode] = generateNimCode(code)

  t.is("42.sas().sas()", nimCode)
});

// Binary

test('Codegen Expressions Binary messages', t => {
  const code = '1 + 2'
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`+`(2)", nimCode)
});
test('Codegen Expressions Binary messages many', t => {
  const code = '1 + 2 + 3' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.`+`(2).`+`(3)", nimCode)
});

// Keyword

test('Codegen Expressions Keyword messages', t => {
  const code = '1 from: 2 to: 3' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.from_to(2, 3)", nimCode)
});

// Combined 
test('Codegen Expressions Unary with Binary', t => {
  const code = '1 sas + 2 sas' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.sas().`+`(2.sas())", nimCode)
});

test('Codegen Expressions two Unary with Binary', t => {
  const code = '1 sas + 2 sas.\n1 sas + 2 sas.' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.sas().`+`(2.sas())\n1.sas().`+`(2.sas())", nimCode)
});

test('Codegen Expressions Unary with Binary many', t => {
  const code = '1 sas + 2 sas sus ses' 
  const [_, nimCode] = generateNimCode(code)

  t.is("1.sas().`+`(2.sas().sus().ses())", nimCode)
});

// TYPE DECLARATION
test('Codegen Type Declaration', t => {
  const code = 'type Person name: string age: int' 
  const [_, nimCode] = generateNimCode(code)

  t.is("type Person = object\n  name: string\n  age: int\n", nimCode)
});


test('Codegen two Type Declaration', t => {
  const code = 'type Person name: string age: int.\ntype AnotherPerson name: string age: int' 
  const [_, nimCode] = generateNimCode(code)

  t.is("type Person = object\n  name: string\n  age: int\n\ntype AnotherPerson = object\n  name: string\n  age: int\n", nimCode)
});


// MESSAGE DECLARATION
test('Codegen Method Declaration', t => {
  const code = 'Person + x::int = [ x echo ]' 
  const [_, nimCode] = generateNimCode(code)

  t.is("proc from(self: Person, x: int) =\n  x.echo()", nimCode)
});


