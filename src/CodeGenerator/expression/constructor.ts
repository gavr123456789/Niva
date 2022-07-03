// (name: "sas", age: 33)
import {Constructor, CustomConstructor} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {fillKeywordArgsAndReturnStatements} from "./messageCalls";

export function generateConstructor(c: Constructor, indentation: number): string {
  const indent = " ".repeat(indentation)
  const keyWordArgs = c.call.arguments
  const argsValuesCode: string[] = []

  fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, 0)
  // const typeName = receiver.atomReceiver.value;


  // create "key: val, key2: val2" pairs
  const argNameColonArgVal = keyWordArgs.map((x, i) => {
    return x.keyName + ": " + argsValuesCode[i];
  }).join(", ");

  const code = `${indent}${c.type}(${argNameColonArgVal})`;
  return code;
}

export function generateCustomConstructor(c: CustomConstructor, indentation: number): string {
  const indent = " ".repeat(indentation)
  const constructorName = "construct_" + c.type + "_" + c.call.name
  switch (c.call.selectorKind) {
    case "unary":

      const unaryCode = `${indent}${constructorName}()`;
      return unaryCode;
    case "binary":
      throw new Error("binary constructor? are u crazy?")
    case "keyword":
      const keyWordArgs = c.call.arguments
      const argsValuesCode: string[] = []

      fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, 0)
      // const typeName = receiver.atomReceiver.value;


      // create "key: val, key2: val2" pairs
      const argNameColonArgVal = keyWordArgs.map((x, i) => {
        return x.keyName + ": " + argsValuesCode[i];
      }).join(", ");
      const args = argsValuesCode.join(", ")
      const code = `${indent}${constructorName}(${args})`;
      return code;
  }

}