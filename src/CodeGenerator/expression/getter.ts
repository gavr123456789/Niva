import {Getter} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {generateMessageCalls, processExpression} from "./expression";

export function generateGetter(to: Getter, indentation: number) {
  const indent = " ".repeat(indentation)

  const otherMessages = generateMessageCalls(to.messageCalls, 0).join("")
  // person.name
  return indent + to.valueName + "." + to.fieldName + otherMessages
}