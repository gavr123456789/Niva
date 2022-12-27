import { NonterminalNode, IterationNode, TerminalNode } from "ohm-js"
import { SwitchExpression, SwitchBranch, ElseBranch, Expression } from "../../../AST_Nodes/Statements/Expressions/Expressions"
import {state} from "../../../niva";

export function switchExpression(_s0: NonterminalNode,
  switchBranch: NonterminalNode,
  _s: NonterminalNode,
  otherSwitchBranch: IterationNode,
  _s2: IterationNode,
  switchBranchElseStatement: IterationNode): SwitchExpression {


  const switchReturn1: SwitchBranch = switchBranch.toAst()
  const switchReturn2: SwitchBranch[] = otherSwitchBranch.children.map(x => x.toAst())
  console.log("switchExpression")
  const maybeElseBranch = switchBranchElseStatement.children.at(0)

  // if (maybeElseBranch) {
  //
  //   const elseBranch = maybeElseBranch.toAst()
  //   const result: SwitchExpression = {
  //     kindStatement: "SwitchExpression",
  //     branches: [switchReturn1, ...switchReturn2],
  //     elseBranch: elseBranch
  //   }
  //   state.leavePM_Scope()
  //   return result
  // }
  const result: SwitchExpression = {
    kindStatement: "SwitchExpression",
    branches: [switchReturn1, ...switchReturn2],
    elseBranch: maybeElseBranch? maybeElseBranch.toAst(): undefined
  }
  state.leavePM_Scope()

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
  // Add to state inside which branch are we


  // if (state.inPM_Branch){
  //   state.enterBranchScope()
  //
  // }
  const thenDoExpressionNode: Expression = thenDoExpression.toAst()
  if (thenDoExpressionNode.kindStatement === "SwitchExpression") {
    throw new Error(`${thenDoExpression.sourceString} nested switch expression are not supported`);
  }
  // remove to state inside which branch are we

  const result: SwitchBranch = {
    caseExpressions: expressionList,
    thenDoExpression: thenDoExpressionNode
  }

  return result
}
