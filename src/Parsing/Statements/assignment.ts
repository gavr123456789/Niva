import { NonterminalNode } from "ohm-js";
import { Expression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Assignment, Mutability } from "../../AST_Nodes/Statements/Statement";
import { codeDB, state } from "../../niva";

export function assignment(
  assignmentTargetNode: NonterminalNode,
  _arg1: NonterminalNode,
  _assignmentOp: NonterminalNode,
  _arg3: NonterminalNode,
  expression: NonterminalNode
) {
  const message = assignmentTargetNode.source.getLineAndColumnMessage();
  const assignRightValue: Expression = expression.toAst();
  const leftName = assignmentTargetNode.sourceString
  if (assignRightValue.kindStatement === "SwitchExpression" && !assignRightValue.elseBranch) {
    throw new Error(`${message} switch assignment must have else branch`);
  }
  const astAssign: Assignment = {
    kindStatement: 'Assignment',
    assignmentTarget: leftName,
    mutability: Mutability.IMUTABLE,
    to: assignRightValue,
    messagelineAndColumnMessage: message,
    sourceCode: expression.sourceString,
  };

  const currentMessageInfo = state.insideMessage
  // if reciever is literal, then assign type
  //TODO будут еще конструкторы остальных типов тоже
  if (assignRightValue.kindStatement === "MessageCallExpression" &&
    assignRightValue.receiver.kindStatement === "Primary") {

    // "x = y", not "x= Person from: 3"
    if (assignRightValue.messageCalls.length === 0 ){
    const rightLiteralKind = assignRightValue.receiver.atomReceiver.kindPrimary
      // x = y
      if (rightLiteralKind === "Identifier") {
        // check, if there already defined identifier
        const rightIdentifier = assignRightValue.receiver.atomReceiver.value
        const alreadyDefinedTypeOfOtherVal = codeDB.checkMethodHasValue(currentMessageInfo, rightIdentifier)
        if (alreadyDefinedTypeOfOtherVal) {
          codeDB.addTypedValueToMethodScope(currentMessageInfo, leftName, alreadyDefinedTypeOfOtherVal)
        }
      } else {
        // x = 3
        codeDB.addTypedValueToMethodScope(currentMessageInfo, leftName, rightLiteralKind)
      }
    }
  }
  // x = Person name: "Bob" age: 42
  if (assignRightValue.kindStatement === "Constructor"){
    // get type if right is Constructor
    codeDB.addTypedValueToMethodScope(currentMessageInfo, leftName, assignRightValue.type)
  }

  // addGlobalVariableDeclaratuon(variables.get('global'), astAssign, errors);
  return astAssign;
}