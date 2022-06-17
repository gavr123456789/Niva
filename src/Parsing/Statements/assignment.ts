import { NonterminalNode } from "ohm-js";
import { Expression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Assignment, Mutability } from "../../AST_Nodes/Statements/Statement";
import { codeDB, ContextInformation, state } from "../../niva";

export function assignment(
  assignmentTargetNode: NonterminalNode,
  _arg1: NonterminalNode,
  _assignmentOp: NonterminalNode,
  _arg3: NonterminalNode,
  expression: NonterminalNode
) {
  const message = assignmentTargetNode.source.getLineAndColumnMessage();
  const assignRightValue: Expression = expression.toAst();
  const assignmentTargetIdentifier = assignmentTargetNode.sourceString
  if (assignRightValue.kindStatement === "SwitchExpression" && !assignRightValue.elseBranch) {

    throw new Error(`${message} switch assignment must have else branch`);
  }
  const astAssign: Assignment = {
    kindStatement: 'Assignment',
    assignmentTarget: assignmentTargetIdentifier,
    mutability: Mutability.IMUTABLE,
    to: assignRightValue,
    messagelineAndColumnMessage: message,
    sourceCode: expression.sourceString,
  };

  
  // if reciever is literal, then assign type
  if (assignRightValue.kindStatement === "MessageCallExpression" &&
    assignRightValue.receiver.kindStatement === "Primary") {
    if (assignRightValue.receiver.atomReceiver.kindPrimary !== "Identifer"){
    const currentMessageInfo = state.insideMessage
      codeDB.addTypedValueToMethodScope(currentMessageInfo, assignmentTargetIdentifier, assignRightValue.receiver.atomReceiver.kindPrimary )
    } else {
      // check, if there already defined identifier
      
    }
    // 
  }

  // addGlobalVariableDeclaratuon(variables.get('global'), astAssign, errors);
  return astAssign;
}