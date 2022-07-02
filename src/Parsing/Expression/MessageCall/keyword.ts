
// Keyword

import { NonterminalNode, IterationNode, TerminalNode } from "ohm-js";
import { KeywordMessage, KeywordArgument, BinaryMessage, UnaryMessage } from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import { state } from "../../../niva";

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
  const keywordMessageName = resultArguments.map(x => x.keyName).join("_")
  
  
  const result: KeywordMessage = {
    name: keywordMessageName,
    selectorKind: 'keyword',
    arguments: resultArguments,
    returnType: {name: ""}
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