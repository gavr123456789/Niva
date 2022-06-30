import {
  BinaryArgument,
  BinaryMessage,
  KeywordMessage,
  MessageCall, UnaryMessage
} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {codeDB} from "../../niva";
import {getReceiverType} from "./getReceiverType";
import {getTypeOfExpression, getTypeOfLastMessage} from "./getTypeOfExpression";



function getTypeOfBinaryMsgArgument(msg: BinaryMessage) {
  if (!msg.argument.unaryMessages || msg.argument.unaryMessages.length === 0) {
    return getReceiverType(msg.argument.value)
  } else {
    const typeOfLastMsg = getTypeOfLastMessage(msg.argument.unaryMessages)
    console.log("typeOfLastMsg =", typeOfLastMsg)
    if (!typeOfLastMsg){
      throw new Error("cant get typeOfLastMsg of binary argument")
    }
    return  typeOfLastMsg
  }
}

// for binaryMsg returns +::int
// for keyword msg returns from::int:to::int:do::code
export function getTypedSelector(msg: MessageCall): string {
  switch (msg.selectorKind) {

    case "unary":
      throw new Error("we dont need typed selector for unary msg")

    case "binary":

      // а еще вместо того чтобы хранить типы внутри сигнатуры, держи внутри
      // информации о типах хешмапу от типов ключей к типу возвращаемого значения
      // например не ++::int а ++, Map(int -> int, string(тип аргумента) -> string(тип значения))
      // А вот для кейвордов пока шо придется складывать Map("int,string,bool" -> bool, "int,int,int"->int)

      // const sss = getTypeOfExpression(msg)

      // Если сообщений нема берем тип ресивера, если есть берем тип сообщений
      const receiverType = getTypeOfBinaryMsgArgument(msg)
      if (!receiverType){
        throw new Error("Cant get receiver type")
      }
      return msg.name + "::" + receiverType

    case "keyword":

      throw new Error("I dont know how to get typed selector")
  }
}

function getMsgCallReturnType(msg: MessageCall, previousType: string) {
  switch (msg.selectorKind) {
    case "unary":
      return codeDB.getMethodReturnType(previousType, msg.name, msg.selectorKind)
    case "binary":

      const typedSelector = getTypedSelector(msg)
      return codeDB.getMethodReturnType(previousType, typedSelector, msg.selectorKind)
    case "keyword":
      throw new Error("I dont know how to get typed selector")
      // return codeDB.getMethodReturnType(previousType, ????, msg.selectorKind)
  }
}

function getReturnType(msg: UnaryMessage | BinaryMessage | KeywordMessage, previousType: string): string {
  switch (msg.selectorKind) {

    case "unary":
      const returnType = getMsgCallReturnType(msg, previousType)//codeDB.getMethodReturnType(previousType, msg.name + "::" + msg.type.name, msg.selectorKind)
      if (!returnType) {
        console.log("message: ", msg)
        throw new Error("I dont know return type of unary message")
      }
      return returnType

    case "binary":
      if (msg.argument.unaryMessages) {
        const argReceiverType = getReceiverType(msg.argument.value)
        if (!argReceiverType) {
          throw new Error("cant get receiverType of binary argument to get its type")
        }
        fillMessageCallsWithTypes(argReceiverType, msg.argument.unaryMessages)
      }

      switch (msg.argument.value.kindStatement) {
        case "Primary":
          const returnType = getMsgCallReturnType(msg, previousType)
          if (!returnType) {
            console.log("message: ", msg)
            throw new Error("I dont know return type of message")
          }
          return returnType
        case "BlockConstructor":
          throw new Error("TODO")
        case "BracketExpression":
          const binaryArgExpression = msg.argument.value
          const typeOfExpressionArg = getTypeOfExpression(binaryArgExpression)
          const typedSelector = msg.name + "::" + typeOfExpressionArg
          return codeDB.getMethodReturnType(previousType, typedSelector, msg.selectorKind)
        default:
          const _never: never = msg.argument.value
          throw new Error("Sound error")
      }
    case "keyword":
      throw new Error("TODO")
    default:
      const _never: never = msg
      throw new Error("Sound error")

  }
}

export function fillMessageCallsWithTypes(receiverType: string, astMessages: MessageCall[]) {

  astMessages.forEach((msg, i, array) => {


    const previousMsg = array[i - 1]
    const previousType = previousMsg ? previousMsg.returnType.name : receiverType

    msg.returnType.name = getReturnType(msg, previousType);
    // const returnType =  getReturnType(msg, previousType)//codeDB.getMethodReturnType(previousType, msg.name + "::" + msg.type.name, msg.selectorKind)
    // if (!returnType) {
    //   console.log("message: ", msg)
    //   throw new Error("I dont know return type of message")
    // }
    // msg.returnType.name = returnType
  })
}