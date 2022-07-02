import {BaseMessageCallExpression} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";

export function getTypeOfLastMessage(messageCalls: MessageCall[]){
  return messageCalls.at(-1)?.returnType.name
}

export function getTypeOfExpression(x: BaseMessageCallExpression): string | undefined {
  // console.log("x = ", prettyPrint(x))
  // fillMessageCallsWithTypes()
  // x.messageCalls
  const lastMessageType = getTypeOfLastMessage(x.messageCalls)
  // console.log("BracketExpression lastMessageType = ", lastMessageType)
  return lastMessageType
}