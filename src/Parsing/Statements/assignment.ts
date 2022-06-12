import { NonterminalNode } from "ohm-js";
import { Expression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Assignment, Mutability } from "../../AST_Nodes/Statements/Statement";

export function assignment(
  assignmentTarget: NonterminalNode,
  _arg1: NonterminalNode,
  _assignmentOp: NonterminalNode,
  _arg3: NonterminalNode,
  expression: NonterminalNode
) {
  const message = assignmentTarget.source.getLineAndColumnMessage();
  const assignRightValue: Expression = expression.toAst();
  if (assignRightValue.kindStatement === "SwitchExpression" && !assignRightValue.elseBranch) {

    throw new Error(`${message} switch assignment must have else branch`);
  }
  const astAssign: Assignment = {
    kindStatement: 'Assignment',
    assignmentTarget: assignmentTarget.sourceString,
    mutability: Mutability.IMUTABLE,
    to: assignRightValue,
    messagelineAndColumnMessage: message,
    sourceCode: expression.sourceString,
    file: ''
  };

  // addGlobalVariableDeclaratuon(variables.get('global'), astAssign, errors);
  return astAssign;
}