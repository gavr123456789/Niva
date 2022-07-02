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

// так как унарные и бинарные сообщения могут идти за другими сообщениями,
// нам нужно узнавать типы предыдущих сообщений чтобы узнать тип получателя текущего сообщения
function getMsgCallReturnType(msg: MessageCall, previousType: string) {
  switch (msg.selectorKind) {
    case "unary":
      return codeDB.getMethodReturnType(previousType, msg.name, msg.selectorKind)
    case "binary":

      const typedSelector = getTypedSelector(msg)

      // тут в previous type должен попадать не предыдущий аргумент, а результат предыдущего бинарного сообдения
      // 5 \/\/ 3 & "sas"
      // \/\/ переводит оба в стринги и складывает
      // & должен принять не 3 в качестве аргумента а результат \/\/ то есть стринг
      return codeDB.getMethodReturnType(previousType, typedSelector, msg.selectorKind)
    case "keyword":
      throw new Error("keyword message cant follow another message, its always first")
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
      // если у аргумента есть унарные сообщения то
      if (msg.argument.unaryMessages) {
        const argReceiverType = getReceiverType(msg.argument.value)
        if (!argReceiverType) {
          throw new Error("cant get receiverType of binary argument to get its type")
        }
        fillMessageCallsWithTypes(argReceiverType, msg.argument.unaryMessages)
      }

      // 2 + x
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

      // Нужно вычислить тип каждого аргумента, чтобы знать
      // какой именно метод мы будем искать исходя из этих типов
      // на случай если он перегружен
    case "keyword":
      // если есть бинарные сообщения - вычислить их типы
      // если есть унарные сообщения - вычислить их типы

      // array of arg types,
      // example: 23 between "sas" length and: 348 + 23
      // arrayOfArgTypes = [int, int]
      const arrayOfArgTypes: string[] = []
      msg.arguments.forEach(arg => {
        const argReceiverType = getReceiverType(arg.receiver)
        if (!argReceiverType){
          throw new Error("cant get receiverType of keyword argument")
        }

        fillMessageCallsWithTypes(argReceiverType, arg.unaryMessages)
        // заполняя типы бинарных мы должны знать типы унарных сообщений
        fillMessageCallsWithTypes(argReceiverType, arg.binaryMessages)
        // 34 between: 12 invert + 15 invert and: 45 sas - 72 sus
        // first all unary, then all binary
        // so to know to what binary calls we need to check unary first
        // const allUnaryThenBinaryMessages = [...arg.unaryMessages, arg.binaryMessages]


        // если там были унарные сообщения и бинарные то возвращаем тип последнего бинарного
        // если там были только бинарные или только унарные то возращаем тип последнего
        const isThereUnaryMessages = arg.unaryMessages.length > 0
        const isThereBinaryMessages = arg.binaryMessages.length > 0

        if(!isThereBinaryMessages && !isThereUnaryMessages){
          arrayOfArgTypes.push(argReceiverType)
        } else if (isThereBinaryMessages) {
          const returnType = getTypeOfLastMessage(arg.binaryMessages)
          if (!returnType){
            throw new Error("cant get return type")
          }
          arrayOfArgTypes.push(returnType)
        } else if (isThereUnaryMessages && !isThereBinaryMessages){
          const returnType = getTypeOfLastMessage(arg.unaryMessages)
          if (!returnType){
            throw new Error("cant get return type")
          }
          arrayOfArgTypes.push(returnType)
        }
      })

      const arrayOfArgsNames = msg.arguments.map(x => x.keyName)
      if (arrayOfArgsNames.length !== arrayOfArgTypes.length) {
        console.log("arrayOfArgsNames = ", arrayOfArgsNames)
        console.log("arrayOfArgTypes = ", arrayOfArgTypes)
        throw new Error("every argument of keyword message must have type")
      }

      const namesWithTypes = arrayOfArgsNames.map((x, i) => {
        const sas = x + "::" + arrayOfArgTypes[i]
        return sas
      })

      // "between::int and::int"

      const result = namesWithTypes.join(" ")
      console.log("keyword result = ", result)
      return result
    default:
      const _never: never = msg
      throw new Error("Sound error")
  }
}

function getPreviousType(previousMsg: MessageCall | undefined, receiverType: string): string {
  if(!previousMsg){
    return receiverType
  } else {
    if (previousMsg.selectorKind == "binary") {
      // 5 /\/\ 5 & "sas"

      const binaryMethodName = previousMsg.name + "::" + previousMsg.argument.value.type
      console.log(`returnTypeOfPreviousMsg args: type: ${receiverType}, methodName: ${binaryMethodName}`)
      const returnTypeOfPreviousMsg = codeDB.getMethodReturnType(
        receiverType, // receiverType of previous message
        binaryMethodName,
        "binary"
      )
      console.log("returnTypeOfPreviousMsg = ", returnTypeOfPreviousMsg)
      return returnTypeOfPreviousMsg
    }
    return previousMsg.returnType.name
  }


}

export function fillMessageCallsWithTypes(receiverType: string, astMessages: MessageCall[]) {
  astMessages.forEach((msg, i, array) => {
    const previousMsg: MessageCall | undefined = array[i - 1]
    const previousType = getPreviousType(previousMsg, receiverType)//previousMsg ? previousMsg.returnType.name : receiverType

    msg.returnType.name = getReturnType(msg, previousType);
  })
}