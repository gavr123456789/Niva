import {IterationNode, NonterminalNode} from "ohm-js";
import {
  Constructor,
  CustomConstructor,
  MessageCallExpression,
  Setter
} from "../../../AST_Nodes/Statements/Expressions/Expressions";
import {KeywordMessage, MessageCall} from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Receiver} from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {codeDB, state} from "../../../niva";
import {CallLikeExpression} from "../../../CodeGenerator/expression/callLikeExpression";
import {fillMessageCallsWithTypes} from "../../../CodeDB/InferTypes/fillMessageCallsWithTypes";
import {getReceiverType} from "../../../CodeDB/InferTypes/getReceiverType";

function checkIfAllKeysAreTypeFields(kwMsg: KeywordMessage, typeName: string): boolean {
  const typeFields = codeDB.getTypeFields(typeName)

  if (typeFields.size !== kwMsg.arguments.length) {
    return false
  }

  for (let argument of kwMsg.arguments)
    if (!typeFields.has(argument.keyName))
      return false


  return true;
}

export function messageCall(receiverNode: NonterminalNode, maymeMessages: IterationNode, cascadedMessages: IterationNode): CallLikeExpression {
  const receiver: Receiver = receiverNode.toAst();
  const receiverType = getReceiverType(receiver)
  const result: MessageCallExpression = {
    kindStatement: 'MessageCallExpression',
    receiver: receiver,
    selfTypeName: "",
    messageCalls: [],
  };
  const insideMessageOfType = state.insideMessage.forType

  // if no messages than its just a value
  // or constructor without fields
  const messages = maymeMessages.children.at(0);
  if (!messages) {

    if(receiver.kindStatement === "Primary"
      && receiver.atomReceiver.kindPrimary === "Identifier"
      && codeDB.hasType(receiver.atomReceiver.value))
    {
      const type = codeDB.getType(receiver.atomReceiver.value)!


      const constructorWithNoFields: Constructor = {
        kindStatement: "Constructor",
        selfTypeName: insideMessageOfType,
        type: receiver.atomReceiver.value,
        isUnion: type.isUnion,
        unionParentName: type.unionParentName
      }
      return constructorWithNoFields
    }

    if (receiverType) {
      result.type = receiverType
    }
    return result;
  }


  const astMessages: MessageCall[] = messages.toAst();
  result.messageCalls = astMessages;
  result.selfTypeName = insideMessageOfType

  // TODO вот тут уже должен быть receiverType, как минимум если он и так указан
  // console.log("!!вот тут уже должен быть receiverType, как минимум если он и так указан =", receiverType)
  if (receiverType) {
    fillMessageCallsWithTypes(receiverType, astMessages)
  }
  result.type = astMessages.at(-1)?.type.name



  const firstMessage = astMessages.at(0)
  /// Check for Setter
  // if this is keyword message with one arg, and its arg is one of the type fields
  if (firstMessage?.selectorKind === "keyword"
    && firstMessage.arguments.length === 1
    && receiver.kindStatement === "Primary"
    && receiver.atomReceiver.kindPrimary === "Identifier"
    && !codeDB.hasType(receiver.atomReceiver.value) // if true, its Constructor, not setter
  ) {
    const firstArg = firstMessage.arguments[0]
    const valueName = receiver.atomReceiver.value
    const valueType = codeDB.getValueType(state.insideMessage, valueName)

    if (insideMessageOfType === "__global__"){
      const lineAndColumnMessage = messages.source.getLineAndColumnMessage()
      // console.error(lineAndColumnMessage)
      // console.error("You can't use setters in global scope, create a method to change the fields of an object.\nImagine that the objects are not boxes with fields, but virtual assistants.")


      // throw new Error("cant call non local setter")
    }
    if (insideMessageOfType !== valueType) {
      const lineAndColumnMessage = messages.source.getLineAndColumnMessage()
      // console.error(lineAndColumnMessage)
      // console.error(`You can't modify the fields of the ${valueType} inside method of type ${insideMessageOfType}.
      // Create a method for ${valueType} type that changes the fields and then call it here`)

      // throw new Error("cant call non local setter")
    }

    const fieldType = codeDB.getFieldType(insideMessageOfType, firstArg.keyName)
    if (fieldType) {
      console.log("mutable effect added to ", state.insideMessage.withName);

      codeDB.addEffectForMethod(insideMessageOfType, state.insideMessage.kind, state.insideMessage.withName, {kind: "mutatesFields"})
      const setter: Setter = {
        // messageCalls: astMessages,
        kindStatement: "Setter",
        argument: firstArg,
        receiver,
        selfTypeName: insideMessageOfType,
        type: fieldType,
        valueName
      }
      console.log("setter detected type: ", setter.type, " signature: ", setter.argument.keyName);

      return setter
    }
  }


  /// check for constructor
  const isThisIsTypeCall =
    receiver.kindStatement === "Primary" &&
    receiver.atomReceiver.kindPrimary === "Identifier" &&
    // firstMessage &&
    // firstMessage?.selectorKind === "keyword" &&
    codeDB.getType(receiver.atomReceiver.value)


  // если все кеи аргументов есть у поля типа то это обычный конструктор
  // иначе это кастомный конструктор

  /// Usual constructor
  if (firstMessage?.selectorKind === "keyword" || firstMessage === undefined) {
    if (isThisIsTypeCall) {
      const allKeysAreTypeFields: boolean = firstMessage
        ? checkIfAllKeysAreTypeFields(firstMessage, receiver.atomReceiver.value)
        : true

      const type = codeDB.getType(receiver.atomReceiver.value)!

      // if (type.isUnion){
      //   // TODO если это юнион, то добавляем аргумент kind типа значения енама
      //   // но для этого сначала нужно добавить в Primary Enum значения помимо
      //   firstMessage.arguments.push()
      // }

      // МОЖНО ПРОЩЕ, просто добавить конструктору поле is union

      if (allKeysAreTypeFields) {
        const constructor: Constructor = {
          kindStatement: "Constructor",
          call: firstMessage,
          selfTypeName: insideMessageOfType,
          type: receiver.atomReceiver.value,
          isUnion: type.isUnion
        }
        console.log("constructor detected type: ", constructor.type, " signature: ", constructor.call?.name ?? "---");
        return constructor
      }

    }
  }
  /// Custom Constructor
  if (isThisIsTypeCall && firstMessage) {

    const unaryCustomConstructor: CustomConstructor = {
      call: firstMessage,
      selfTypeName: insideMessageOfType,
      type: receiver.atomReceiver.value,
      kindStatement: "CustomConstructor",
    }

    // replace union branch type with union name, because of nim
    const sass = codeDB.getType(receiver.atomReceiver.value)
    if (sass && sass.unionParentName) {
      unaryCustomConstructor.unionParentName = sass.unionParentName
    }
    return unaryCustomConstructor

  }


  return result;
}

