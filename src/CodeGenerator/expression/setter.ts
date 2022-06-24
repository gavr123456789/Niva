import { MessageCallExpression, Setter} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {fillKeywordArgsAndReturnStatements} from "./messageCalls";
import {processExpression} from "./expression";




export function generateSetter(s: Setter, indentation: number): string {
  const indent = " ".repeat(indentation)
  const {valueName, argument, } = s

  const argsValuesCode: string[] = []
  const keyWordArgs = [argument]
  fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, indentation)
  const argumentCode = argsValuesCode[0]
  console.log("argsValuesCode = ", argsValuesCode)
  const fieldName = argument.keyName
  // fillKeywordArgsAndReturnStatements(messageCall.keyWordArgs, argsValuesCode, 0)
  // input  person name: "sas"
  // output person.name = "sas"
  // we here after dot
  // keywords messages cant be continued with other type of messages, so here no problems
  return `${indent}${valueName}.${fieldName} = ${argumentCode}`

}
