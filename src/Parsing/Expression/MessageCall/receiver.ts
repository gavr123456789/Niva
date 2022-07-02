import {TerminalNode, NonterminalNode, IterationNode} from "ohm-js"
import { BracketExpression, MessageCallExpression } from "../../../AST_Nodes/Statements/Expressions/Expressions"
import { BlockConstructor } from "../../../AST_Nodes/Statements/Expressions/Receiver/BlockConstructor"
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver"

export function receiver_expressionInBrackets(_lb: TerminalNode, _s: IterationNode, expression: NonterminalNode, _s2: IterationNode, _rb: TerminalNode): BracketExpression {

  const result: MessageCallExpression = expression.toAst()
  const result2: BracketExpression = { ...result, kindStatement: "BracketExpression" }
  return result2
}

export function receiver(x: NonterminalNode): Receiver {
  if (x.sourceString[0] === "(") {
    const result: BracketExpression = x.toAst()
    return result
  }
  if (x.sourceString[0] === "[") {
    const result: BlockConstructor = x.toAst()
    return result
  }

  const result: Receiver = {
    kindStatement: 'Primary',
    atomReceiver: x.toAst()
  };
  return result;
}

export function primary(arg0: NonterminalNode) {
  return arg0.toAst();
}