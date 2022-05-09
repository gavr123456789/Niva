import test from 'ava';
import { RedefinitionOfVariableError } from './Errors/Error';
import { generateNimCode } from './niva';
import { grammarMatch } from './utils';


test('Grammar binary message send', t => {
  t.is(grammarMatch("5 + 5", "basicExpression").succeeded(), true)
  t.is(grammarMatch("5 ++ 5", "basicExpression").succeeded(), true)
  t.is(grammarMatch("5 |> 5", "basicExpression").succeeded(), true)
});

test('Grammar unary message send', t => {
  t.is(grammarMatch("\"reverce this!\" reverce", "basicExpression").succeeded(), true)
  t.is(grammarMatch("5 factorial", "basicExpression").succeeded(), true)
});


test('Grammar code block', t => {
  t.is(grammarMatch("[ 5 factorial ]",).succeeded(), true)
});
test('Grammar multy-line code block', t => {
  t.is(grammarMatch("[\n 5 factorial \n]",).succeeded(), true)
});
test('Grammar empty code block', t => {
  t.is(grammarMatch("[\n\n]").succeeded(), true)
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
  const [_sas,_nimCode, errors] = generateNimCode(code)
  
  const varError = errors[0]
  t.truthy(varError)
  if (!varError) {
    throw new Error("there no redefinition error");
  }

  t.is("RedefinitionOfVariableError", varError.errorKind)
  console.log("variable ", varError.variableName, " from " );
  console.log(varError.lineAndColMessage);
  console.log("was already defined in ");
  console.log(varError.previousLineAndColMessage);
  // console.log("var error = ", varError);
  
  // t.is(expectedError.line, varError.line)
  // t.is(expectedError.previousLine2, varError.previousLine2)
});
