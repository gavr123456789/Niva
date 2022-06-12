import { NonterminalNode } from "ohm-js";
import { ReturnStatement } from "../../AST_Nodes/Statements/Statement";
import { state } from "../../niva";

export function returnStatement(_op: NonterminalNode, _s: NonterminalNode, expression: NonterminalNode): ReturnStatement {
  if (!state.isInMethodBody) {
    throw new Error("Retrun statement must be inside method body");
  }
  const expr = expression.toAst()
  const result: ReturnStatement = {
    kindStatement: "ReturnStatement",
    value: expr
  }
  return result
}