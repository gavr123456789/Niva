import {IterationNode, NonterminalNode} from "ohm-js";
import {
  Constructor,
  Getter,
  MessageCallExpression,
  Setter
} from "../../../AST_Nodes/Statements/Expressions/Expressions";
import {MessageCall} from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Receiver} from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {codeDB, state} from "../../../niva";
import {CallLikeExpression} from "../../../CodeGenerator/expression/callLikeExpression";

export function messageCall(receiverNode: NonterminalNode, maymeMessages: IterationNode, cascadedMessages: IterationNode): CallLikeExpression {
  const receiver: Receiver = receiverNode.toAst();

  const result: MessageCallExpression = {
    kindStatement: 'MessageCallExpression',
    receiver: receiver,
    selfTypeName: "",
    messageCalls: []
  };

  // check if receiver has messages
  const messages = maymeMessages.children.at(0);
  if (!messages) {
    return result;
  }

  const astMessages: MessageCall[] = messages.toAst();
  result.messageCalls = astMessages;
  result.selfTypeName = state.insideMessage.forType

  const selfTypeName = state.insideMessage.forType
  const firstMessage = astMessages.at(0)
  /// Check for Setter
  // if this is keyword message with one arg, and its arg is one of the type fields
  if (firstMessage?.selectorKind === "keyword"
    && firstMessage.arguments.length === 1
    && receiver.kindStatement === "Primary" &&
    receiver.atomReceiver.kindPrimary === "Identifier")
  {
    const firstArg = firstMessage.arguments[0]
    const valueName = receiver.atomReceiver.value

    const fieldType = codeDB.getFieldType(selfTypeName, firstArg.keyName)
    if (fieldType) {
      console.log("mutable effect added to ", state.insideMessage.withName);
      
      codeDB.addEffectForMethod(selfTypeName, state.insideMessage.kind, state.insideMessage.withName, { kind: "mutatesFields" })
      const setter: Setter = {
        messageCalls: astMessages,
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

  // check for getter
  // receiver type is in the codeDb, message is unary, its selector is one of the type fields
  if (firstMessage?.selectorKind === "unary" &&
    receiver.kindStatement === "Primary" &&
    receiver.atomReceiver.kindPrimary === "Identifier") {
    const valueName = receiver.atomReceiver.value
    const fieldName = firstMessage.name
    const receiverType = codeDB.getValueType(state.insideMessage, valueName)
    if (receiverType && codeDB.typeHasField(receiverType, fieldName)) {
      const getter: Getter = {
        kindStatement: "Getter",
        valueName,
        fieldName,
        selfTypeName,
        messageCalls: astMessages.slice(1)
      }
      console.log("getter detected:", getter.valueName, "receiverType =", receiverType, "fieldName =", fieldName);
      return getter
    }


  }
  ///


  return result;
}

