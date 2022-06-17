import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js"
import { AnyLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/AnyLiteral"
import { BoolLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/BoolLiteral"
import { IntLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/StringLiteralNode"

export function boolLiteral(boolLiteral: NonterminalNode): BoolLiteral {
  const result: BoolLiteral = {
    kindPrimary: "bool",
    value: boolLiteral.sourceString
  }
  return result
}

export function integerLiteral(intLiteral: NonterminalNode): IntLiteral {
  const result: IntLiteral = {
    kindPrimary: 'int',
    value: intLiteral.sourceString
  };
  return result;
}

export function stringLiteral(_lQuote: TerminalNode, text: IterationNode, _rQuote: TerminalNode): StringLiteral {
  const result: StringLiteral = {
    kindPrimary: 'string',
    value: '"' + text.sourceString + '"'
  };
  return result;
}

export function anyLiteral(arg0: NonterminalNode): AnyLiteral {
  return arg0.toAst();
}

