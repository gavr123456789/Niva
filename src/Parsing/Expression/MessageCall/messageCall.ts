import {IterationNode, NonterminalNode} from "ohm-js";
import {Constructor, MessageCallExpression, Setter} from "../../../AST_Nodes/Statements/Expressions/Expressions";
import {MessageCall} from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Receiver} from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {codeDB, state} from "../../../niva";
import {CallLikeExpression} from "../../../CodeGenerator/expression/callLikeExpression";
import {fillMessageCallsWithTypes} from "../../../CodeDB/InferTypes/fillMessageCallsWithTypes";
import {getReceiverType} from "../../../CodeDB/InferTypes/getReceiverType";

export function messageCall(receiverNode: NonterminalNode, maybeMessages: IterationNode, cascadedMessages: IterationNode): CallLikeExpression {
  const receiver: Receiver = receiverNode.toAst();
  const receiverType = getReceiverType(receiver)

  const result: MessageCallExpression = {
    kindStatement: 'MessageCallExpression',
    receiver: receiver,
    selfTypeName: "",
    messageCalls: [],
  };

  // if no messages than its just a value
  const messages = maybeMessages.children.at(0);
  if (!messages) {
    if (receiverType){
      result.type = receiverType
    }
    return result;
  }


  const astMessages: MessageCall[] = messages.toAst();
  result.messageCalls = astMessages;
  result.selfTypeName = state.insideMessage.forType

  if (receiverType){
    fillMessageCallsWithTypes(receiverType, astMessages)
    // TODO нужно также запускать это на все подсообщения

  } else {
    console.log("I dont know the receiver type receiver = ", receiver)
    // Вот мы встретили функцию у которой нужно вывети тип
    // Проверяем что нашы аргументы(в бинарном токо один) удовлетворяют условиям(имеют все вызываемые в этом темплейте методы)

    //TODO
    // Запускаем вывод возвращаемого значения с учетом наших аргументов
    // Добавляем полученный тип возвращаемого значения в хешмапу конкатенации типов аргументов к типу возвращаемого значения,
    // чтобы в следующий раз ничего не вычислять
    // codeDB.
    // throw new Error("I dont know the receiver type")
  }
  const resultType = astMessages.at(-1)?.returnType.name
  if (!resultType){
    console.log("I dont know the type of last astMessage, lastAstMessage is ", astMessages.at(-1))
    // throw new Error("I dont know the type of last astMessage")
  }
  result.type = astMessages.at(-1)?.returnType.name
  // console.log("ast with types = ", astMessages)

  const selfTypeName = state.insideMessage.forType
  const firstMessage = astMessages.at(0)
  /// Check for Setter
  // if this is keyword message with one arg, and its arg is one of the type fields
  if (firstMessage?.selectorKind === "keyword"
    && firstMessage.arguments.length === 1
    && receiver.kindStatement === "Primary" &&
    receiver.atomReceiver.kindPrimary === "Identifier") {
    const firstArg = firstMessage.arguments[0]
    const valueName = receiver.atomReceiver.value

    const fieldType = codeDB.getFieldType(selfTypeName, firstArg.keyName)
    if (fieldType) {
      console.log("mutable effect added to ", state.insideMessage.withName);

      codeDB.addEffectForMethod(selfTypeName, state.insideMessage.kind, state.insideMessage.withName, {kind: "mutatesFields"})
      const setter: Setter = {
        // messageCalls: astMessages,
        kindStatement: "Setter",
        argument: firstArg,
        receiver,
        selfTypeName,
        type: fieldType,
        valueName
      }
      console.log("setter detected type: ", setter.type, " signature: ", setter.argument.keyName);

      return setter
    }
  }
  /// check for constructor
  const isThisIsConstructorCall =
    receiver.kindStatement === "Primary" &&
    receiver.atomReceiver.kindPrimary === "Identifier" &&
    firstMessage?.selectorKind === "keyword" &&
    codeDB.hasType(receiver.atomReceiver.value)

  if (isThisIsConstructorCall) {
    const constructor: Constructor = {
      kindStatement: "Constructor",
      call: firstMessage,
      selfTypeName,
      type: receiver.atomReceiver.value
    }
    console.log("constructor detected type: ", constructor.type, " signature: ", constructor.call.name);

    return constructor
  }

  return result;
}

