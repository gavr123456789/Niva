import {
  BracketExpression,
  Constructor, CustomConstructor,
  MessageCallExpression,
  Setter
} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {generateConstructor, generateCustomConstructor} from "./constructor";
import {processExpression} from "./expression";
import {generateSetter} from "./setter";
import {codeDB} from "../../niva";
import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";

export type CallLikeExpression = MessageCallExpression | BracketExpression | Constructor | CustomConstructor | Setter //  Getter

function checkForConstructor(s: MessageCallExpression) {

  return false;
}


// get previousMessage type
export function checkForGetter(receiver: Receiver, unaryMessage: MessageCall, previousMessage: MessageCall | undefined) {
  const isPrimaryReceiver = receiver.kindStatement === "Primary"
  const isMessageUnary = unaryMessage?.selectorKind === "unary"
  if (!unaryMessage || receiver.kindStatement !== "Primary" || !(unaryMessage?.selectorKind === "unary")) {
    console.log("false  receiver = ", receiver, " unaryMessage = ", unaryMessage)
    return false
  }

  const valueName = receiver.atomReceiver.value
  const fieldName = unaryMessage.name

  // console.log("checkForGetter: ", fieldName)
  // console.log("checkForGetter: unaryMessage.type.name= ", unaryMessage.type.name)

  // TODO если есть предыдущее сообщение то receiverType должен стать его типом
  const receiverType = previousMessage
    ? previousMessage.type.name
    : codeDB.getValueType(unaryMessage.insideMethod, valueName)

  if (!receiverType) {
    console.log("false1 state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)
    console.log("receiverType = ", receiverType)
    return false
  }
  const typeOfField = codeDB.getFieldType(receiverType, fieldName)
  if (!typeOfField){
    // console.log("false1 state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)
    // console.log("receiverType = ", receiverType)
    return false
  }
  // console.log("true state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)
  console.log(fieldName, " is getter")

  return true
}

export function generateCallLikeExpression(s: CallLikeExpression, indentation: number) {
  // преобразовать все сообщения в конструкторы, геттеры и сеттеры
  // transform all messages into constructors getters and setters

  // switch (s.kindStatement) {
  //
  //   case "MessageCallExpression":
  //   case "Constructor":
  //   case "BracketExpression":
  //   case "Setter":
  //     break;
  //   default:
  //     const _never: never = s
  //     throw new Error("Sound error")
  // }

  // generate code
  switch (s.kindStatement) {
    case "Constructor":
      return generateConstructor(s, indentation)
    case "CustomConstructor":
      return generateCustomConstructor(s, indentation)
    case "MessageCallExpression":
    case "BracketExpression":
      return processExpression(s, indentation)
    case "Setter":
      return generateSetter(s, indentation)
    default:
      const _never: never = s
      throw new Error("Sound error")
  }
}