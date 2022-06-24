import { NonterminalNode, IterationNode, TerminalNode } from "ohm-js"
import { SwitchExpression, SwitchBranch, ElseBranch, Expression } from "../../../AST_Nodes/Statements/Expressions/Expressions"

export function switchExpression(_s0: NonterminalNode,
  switchBranch: NonterminalNode,
  _s: NonterminalNode,
  otherSwitchBranch: IterationNode,
  _s2: IterationNode,
  switchBranchElseStatement: IterationNode): SwitchExpression {
  const switchReturn1: SwitchBranch = switchBranch.toAst()
  const switchReturn2: SwitchBranch[] = otherSwitchBranch.children.map(x => x.toAst())

  const maybeElseBranch = switchBranchElseStatement.children.at(0)
  if (maybeElseBranch) {
    const elseBranch = maybeElseBranch.toAst()
    const result: SwitchExpression = {
      kindStatement: "SwitchExpression",
      branches: [switchReturn1, ...switchReturn2],
      elseBranch: elseBranch
    }
    return result
  }
  const result: SwitchExpression = {
    kindStatement: "SwitchExpression",
    branches: [switchReturn1, ...switchReturn2],
  }
  return result
}

export function switchBranchElseStatement(_arrow: TerminalNode, _s: NonterminalNode, expression: NonterminalNode): ElseBranch {
  const elseBranch: ElseBranch = {
    thenDoExpression: expression.toAst()
  }
  return elseBranch
}

export function switchBranch(_pipe: TerminalNode,
  _s: NonterminalNode,
  expressionListNode: NonterminalNode,
  _s2: NonterminalNode,
  _arrow: TerminalNode,
  _s3: NonterminalNode,
  thenDoExpression: NonterminalNode,
  _s4: NonterminalNode): SwitchBranch {

  const expressionList: Expression[] = expressionListNode.toAst()
  if (expressionList.find(x => x.kindStatement === "SwitchExpression")) {
    throw new Error(`${expressionListNode.sourceString} case cant be another switch expression`);
  }
  const thenDoExpressionNode: Expression = thenDoExpression.toAst()
  if (thenDoExpressionNode.kindStatement === "SwitchExpression") {
    throw new Error(`${thenDoExpression.sourceString} nested switch expression are not supported`);
  }

  const result: SwitchBranch = {
    caseExpressions: expressionList,
    thenDoExpression: thenDoExpressionNode
  }

  return result
}
