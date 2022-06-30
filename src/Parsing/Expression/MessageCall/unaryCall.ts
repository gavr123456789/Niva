import {IterationNode, NonterminalNode} from "ohm-js";
import {MessageCall, UnaryMessage} from "../../../AST_Nodes/Statements/Expressions/Messages/Message";
import {state} from "../../../niva";

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
    insideMethod: {...state.insideMessage},
    selectorKind: 'unary',
    name: ident.sourceString,
    returnType: {name: ""}
  };
  // console.log("set ", state.insideMessage, " for ", ident.sourceString)
  return result;
}