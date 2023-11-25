import {TerminalNode, NonterminalNode, IterationNode} from "ohm-js"
import { BracketExpression, MessageCallExpression } from "../../../AST_Nodes/Statements/Expressions/Expressions"
import { BlockConstructor } from "../../../AST_Nodes/Statements/Expressions/Receiver/BlockConstructor"
import { Receiver } from "../../../AST_Nodes/Statements/Expressions/Receiver/Receiver"
import {Primary} from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";
import {SimpleLiteral} from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/SimpleLiteral";
import {Identifier} from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";

export function receiver_expressionInBrackets(_lb: TerminalNode, _s: IterationNode, expression: NonterminalNode, _s2: IterationNode, _rb: TerminalNode): BracketExpression {

  const result: MessageCallExpression = expression.toAst()
  const result2: BracketExpression = { ...result, kindStatement: "BracketExpression" }
  return result2
}

// TODO переделать
// export function receiver(x: NonterminalNode): Receiver {
//   const q = x.toAst()
//   console.log("parser receiver = ", q)
//   const result: Receiver = {
//     kindStatement: 'Primary',
//     atomReceiver: x.toAst()
//   };
//   return result;
// }



export function primary(arg0: NonterminalNode): Receiver {
  const sas: SimpleLiteral | Identifier = arg0.toAst()

  const result: Receiver = {
    kindStatement: "Primary",
    atomReceiver: sas
  }
  return result
}