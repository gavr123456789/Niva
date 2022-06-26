import { IterationNode, NonterminalNode } from "ohm-js";
import { MessageCall, BinaryMessage, BinaryArgument } from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";


// Binary
export function messages_binaryFirst(binaryMessages: IterationNode, keywordMessages: IterationNode) {
  const astOfBinaryMessages: MessageCall[] = binaryMessages.children.map((x) => x.toAst()); // call binaryMessage
  const astOfKeywordMessages: MessageCall[] = keywordMessages.children.map((x) => x.toAst()); // call binaryMessage
  return [...astOfBinaryMessages, ...astOfKeywordMessages];
}

export function binaryMessage(_spaces1: NonterminalNode, binarySelector: NonterminalNode, _spaces2: NonterminalNode, binaryArgument: NonterminalNode): BinaryMessage {
  const result: BinaryMessage = {
    selectorKind: 'binary',
    argument: binaryArgument.toAst(), // returns its name
    name: binarySelector.toAst(), // returns its name
    type: {name: ""}
  };
  return result;
}

export function binarySelector(x: IterationNode) {
  return x.sourceString;
}

export function binaryArgument(receiver: NonterminalNode, unaryMessageNode: IterationNode): BinaryArgument {
  // get receiver
  const value: Receiver = receiver.toAst();
  const result: BinaryArgument = {
    value
  };

  // check if there unary Messages
  if (unaryMessageNode.children.length > 0) {
    const unaryMessages = unaryMessageNode.children.map((x) => x.toAst());
    result.unaryMessages = unaryMessages;
  }

  return result;
}

