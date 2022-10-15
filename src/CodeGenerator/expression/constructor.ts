// (name: "sas", age: 33)
import {Constructor, CustomConstructor} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {fillKeywordArgsAndReturnStatements} from "./messageCalls";

export function generateConstructor(c: Constructor, indentation: number): string {

  const unionKindArg = c.unionParentName? `kind: ${c.unionParentName}`: ""


  const indent = " ".repeat(indentation)
  const keyWordArgs = c.call?.arguments ?? []
  const argsValuesCode: string[] = []

  if (keyWordArgs.length === 0) {
    return `${indent}${c.type}(${unionKindArg})`
  }

  fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, 0)
  console.log("12")
  // const typeName = receiver.atomReceiver.value;


  // create "key: val, key2: val2" pairs
  const argArray = keyWordArgs.map((x, i) => {
    return x.keyName + ": " + argsValuesCode[i];
  })

  if (unionKindArg.length > 0) {
    argArray.push(unionKindArg)
  }

  const argNameColonArgVal = argArray.join(", ")
  // console.log("argNameColonArgVal = ", argNameColonArgVal)
  const constructorType = c.type
  const code = `${indent}${constructorType}(${argNameColonArgVal})`;
  return code;
}

export function generateCustomConstructor(c: CustomConstructor, indentation: number): string {
  const indent = " ".repeat(indentation)
  const type = c.type
  const constructorName = "construct_" + type + "_" + c.call.name
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
      console.log("qweas")
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
