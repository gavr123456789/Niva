import {
  BracketExpression,
  Constructor,
  MessageCallExpression,
  Setter
} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {generateConstructor} from "./constructor";
import {processExpression} from "./expression";
import {generateSetter} from "./setter";
import {codeDB} from "../../niva";
import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";

export type CallLikeExpression = MessageCallExpression | BracketExpression | Constructor | Setter //  Getter

function checkForConstructor(s: MessageCallExpression) {

  return false;
}


// get previousMessage type
export function checkForGetter(receiver: Receiver, unaryMessage: MessageCall) {
  console.log("checkForGetter")
  const isPrimaryReceiver = receiver.kindStatement === "Primary"
  const isMessageUnary = unaryMessage?.selectorKind === "unary"
  if ( !unaryMessage || receiver.kindStatement !== "Primary" || !(unaryMessage?.selectorKind === "unary")) {
    console.log("false  receiver = ", receiver, " unaryMessage = ", unaryMessage)

    return false
  }

  const valueName = receiver.atomReceiver.value
  const fieldName = unaryMessage.name


  const receiverType = codeDB.getValueType(unaryMessage.insideMethod, valueName)

  if (!receiverType) {
    console.log("false1 state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)
    console.log("receiverType = ", receiverType)
    return false
  }
  const typeOfField = codeDB.getFieldType(receiverType, fieldName)
  if (!typeOfField){
    console.log("false1 state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)
    console.log("receiverType = ", receiverType)

    return false
  }
  console.log("true state.insideMessage = ", unaryMessage.insideMethod, " valueName = ", valueName, " fieldName = ", fieldName)

  // тип receiver меняется на тип возвращаемый геттером
  console.log("type of receiver ", receiver, "will be changed")
  // TODO check for previous message type
  // receiver.type = typeOfField
  // codeDB.setTypedValueToMethodScope(unaryMessage.insideMethod, valueName, typeOfField)
  console.log("type of receiver ", receiver, "has changed")
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
      return generateConstructor(s)
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