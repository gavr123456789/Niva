// (name: "sas", age: 33)
import {Constructor} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {fillKeywordArgsAndReturnStatements} from "./messageCalls";

export function generateConstructor(c: Constructor): string {
  const keyWordArgs = c.call.arguments
  const argsValuesCode: string[] = []

  fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, 0)
  // const typeName = receiver.atomReceiver.value;


  // create "key: val, key2: val2" pairs
  const argNameColonArgVal = keyWordArgs.map((x, i) => {
    return x.keyName + ": " + argsValuesCode[i];
  }).join(", ");

  const code = `${c.type}(${argNameColonArgVal})`;
  return code;
}