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

  result.selfTypeName = state.insudeMessage.forType
  // result.selectorName = state.insudeMessage.withName

  result.messageCalls = astMessages;
  return result;
}

export function messages_unaryFirst(unaryMessages: IterationNode, binaryMessages: IterationNode, keywordMessages: IterationNode) {
  const astOfUnaryMessages: MessageCall[] = unaryMessages.children.map((x) => x.toAst()); // call unaryMessage
  const astOfBinaryMessages: MessageCall[] = binaryMessages.children.map((x) => x.toAst()); // call binaryMessages
  const astOfKeywordMessages: MessageCall[] = keywordMessages.children.map((x) => x.toAst()); // call keywordMessages
  return [...astOfUnaryMessages, ...astOfBinaryMessages, ...astOfKeywordMessages];
}

export function unaryMessage(_s: NonterminalNode, unarySelector: NonterminalNode) {
  return unarySelector.toAst();
}
export function unarySelector(ident: NonterminalNode) {
  const result: UnaryMessage = {
    selectorKind: 'unary',
    unarySelector: ident.sourceString
  };
  return result;
}


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
    binarySelector: binarySelector.toAst() // returns its name
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


// Keyword

export function messages_keywordFirst(keywordMessage: NonterminalNode) {
  return [keywordMessage.toAst()];
}

export function keywordMessage(_s1: NonterminalNode,
  keywordM: IterationNode,
  _s2: IterationNode,
  keywordArgument: IterationNode,
  _s3: IterationNode,
  _s4: NonterminalNode): KeywordMessage {
  const resultArguments: KeywordArgument[] = [];

  // keywordM and keywordArgument always have same length
  keywordM.children.forEach((kwM, i) => {
    const keywordArgName: string = kwM.toAst();
    const argWithoutName: KeywordArgument = keywordArgument.children[i].toAst();
    argWithoutName.keyName = keywordArgName
    const arg = argWithoutName
    resultArguments.push(arg)
    
  });
  
  const result: KeywordMessage = {
    selectorName: state.insudeMessage.withName,
    selectorKind: 'keyword',
    arguments: resultArguments
  };
  return result;
}

export function keywordM(identifier: NonterminalNode, colon: TerminalNode) {
  return identifier.sourceString;
}

export function keywordArgument(receiverNode: NonterminalNode, unaryMessagesNode: IterationNode, binaryMessagesNode: IterationNode): KeywordArgument {
  const receiver: Receiver = receiverNode.toAst()
  
  const binaryMessages: BinaryMessage[] = binaryMessagesNode.children.map(x => x.toAst())
  const unaryMessages: UnaryMessage[] = unaryMessagesNode.children.map(x => x.toAst())
  
  const result: KeywordArgument = {
    receiver: receiver,
    binaryMessages,
    unaryMessages,
    keyName: "" 
  }
  return result;
}