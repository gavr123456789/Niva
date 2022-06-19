import { NonterminalNode, IterationNode} from "ohm-js";
import { Constructor, MessageCallExpression } from "../../../AST_Nodes/Statements/Expressions/Expressions";
import { MessageCall} from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import { codeDB, state } from "../../../niva";

export function messageCall(receiver: NonterminalNode, maymeMessages: IterationNode, cascadedMessages: IterationNode): MessageCallExpression | Constructor {
  const astOfReceiver: Receiver = receiver.toAst();
  
  const result: MessageCallExpression | Constructor = {
    kindStatement: 'MessageCallExpression',
    receiver: astOfReceiver,
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
  // result.selectorName = state.insudeMessage.withName

  // if check for constructor
  const firstMessage = astMessages.at(0)
  const isThisIsConstructorCall =
    astOfReceiver.kindStatement === "Primary" &&
    astOfReceiver.atomReceiver.kindPrimary === "Identifier" &&
    firstMessage?.selectorKind === "keyword" &&
    codeDB.hasType(astOfReceiver.atomReceiver.value)

  if (isThisIsConstructorCall) {
    const constructor: Constructor = {
      kindStatement: "Constructor",
      call: firstMessage,
      selfTypeName: "state.insideMessage.forType",
      type: astOfReceiver.atomReceiver.value
    }
    
    return constructor
  }

  return result;
}

