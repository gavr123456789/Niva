import { NonterminalNode, IterationNode } from "ohm-js"
import { Expression } from "../../AST_Nodes/Statements/Expressions/Expressions"

export function expressionList(expression: NonterminalNode, _comma: IterationNode, _s: IterationNode, otherExpressionsNode: IterationNode): Expression[] {
  const firstExpression: Expression = expression.toAst()
  const otherExpressions: Expression[] = otherExpressionsNode.children.map(x => x.toAst())
  const result = [firstExpression, ...otherExpressions]
  return result
}