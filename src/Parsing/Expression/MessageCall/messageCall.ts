import { NonterminalNode, IterationNode, TerminalNode } from "ohm-js";
import { MessageCallExpression } from "../../../AST_Nodes/Statements/Expressions/Expressions";
import { BinaryArgument, BinaryMessage, KeywordArgument, KeywordMessage, MessageCall, UnaryMessage } from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import { state } from "../../../niva";

export function messageCall(receiver: NonterminalNode, maymeMessages: IterationNode, cascadedMessages: IterationNode): MessageCallExpression {
  const astOfReceiver: Receiver = receiver.toAst();
  
  const result: MessageCallExpression = {
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

  const astMessages = messages.toAst();

  result.selfTypeName = state.insideMessage.forType
  // result.selectorName = state.insudeMessage.withName

  result.messageCalls = astMessages;
  return result;
}

