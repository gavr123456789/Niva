import {BaseMessageCallExpression} from "../../AST_Nodes/Statements/Expressions/Expressions";

export function getTypeOfExpression(x: BaseMessageCallExpression): string | undefined {
  // console.log("x = ", prettyPrint(x))
  // fillMessageCallsWithTypes()
  // x.messageCalls
  const lastMessageType = x.messageCalls.at(-1)?.type.name
  // console.log("BracketExpression lastMessageType = ", lastMessageType)
  return lastMessageType
}