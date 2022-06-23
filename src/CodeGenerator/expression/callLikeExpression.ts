import {
  BracketExpression,
  Constructor,
  Getter,
  MessageCallExpression,
  Setter
} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {generateConstructor} from "./constructor";
import {processExpression} from "./expression";
import {generateGetter} from "./getter";
import {generateSetter} from "./setter";
import {codeDB, state} from "../../niva";

export type CallLikeExpression = MessageCallExpression | BracketExpression | Constructor | Getter | Setter

function checkForConstructor(s: MessageCallExpression) {

  return false;
}

function checkForGetter(s: MessageCallExpression): Getter | null {
  //only one message
  // receiver type is in the codeDb, message is unary, its selector is one of the type fields
  const onlyOneMessageCall = s.messageCalls.length === 1
  const firstMessage = s.messageCalls[0]
  const isPrimaryReceiver = s.receiver.kindStatement === "Primary"
  const isMessageUnary = firstMessage?.selectorKind === "unary"
  if (!onlyOneMessageCall || !firstMessage || s.receiver.kindStatement !== "Primary" || !isMessageUnary) {
    return null
  }

  const valueName = s.receiver.atomReceiver.value
  const fieldName = firstMessage.name

  const receiverType = codeDB.getValueType(state.insideMessage, valueName)
  if (!receiverType || !codeDB.typeHasField(receiverType, fieldName)) {
    return null
  }

  const result: Getter = {
    kindStatement: "Getter",
    valueName,
    fieldName,
    selfTypeName: s.selfTypeName,
    messageCalls: s.messageCalls.slice(1)
  }
  return result

}

export function generateCallLikeExpression(s: CallLikeExpression, indentation: number) {
  // преобразовать все сообщения в конструкторы, геттеры и сеттеры
  // transform all messages into constructors getters and setters

  switch (s.kindStatement) {

    case "MessageCallExpression":
      // const isConstructor: boolean = checkForConstructor(s)
      const getter: Getter | null = checkForGetter(s)
      if (getter) {
        console.log("found MessageCallExpression after parsing")
        return generateGetter(getter, indentation)
      }
      // const isSetter: boolean = checkForSetter(s)
      // const x = 5
      // x.
      break;
    case "Constructor":
    case "BracketExpression":
    case "Getter":
    case "Setter":
      break;
    default:
      const _never: never = s
      throw new Error("Sound error")
  }

  // generate code
  switch (s.kindStatement) {
    case "Constructor":
      return generateConstructor(s)
    case "MessageCallExpression":
    case "BracketExpression":
      return processExpression(s, indentation)
    case "Getter":
      return generateGetter(s, indentation)
    case "Setter":
      return generateSetter(s, indentation)
    default:
      const _never: never = s
      throw new Error("Sound error")
  }
}