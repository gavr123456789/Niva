import { NonterminalNode } from "ohm-js";
import { Expression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Assignment, Mutability } from "../../AST_Nodes/Statements/Statement";
import { codeDB, state } from "../../niva";
import { prettyPrint } from '@base2/pretty-print-object';
import {getReceiverType} from "../../CodeDB/InferTypes/getReceiverType";

export function assignment(
  assignmentTargetNode: NonterminalNode,
  _arg1: NonterminalNode,
  _assignmentOp: NonterminalNode,
  _arg3: NonterminalNode,
  expression: NonterminalNode
) {
  const message = assignmentTargetNode.source.getLineAndColumnMessage();
  const assignRightValue: Expression = expression.toAst();
  // console.log(prettyPrint(assignRightValue));


  const leftName = assignmentTargetNode.sourceString

  // else branch in switch
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


  // add type of last message return to variable

  switch (assignRightValue.kindStatement) {
    case "MessageCallExpression":
      if (assignRightValue.messageCalls.length > 0) {
        // x = 7 + 4
        const typeOfLastMessageCall = assignRightValue.messageCalls.at(-1)?.type.name
        if (typeOfLastMessageCall) {
          codeDB.setTypedValueToMethodScope(state.insideMessage, leftName, typeOfLastMessageCall)
          console.log("assignment set type for ", leftName, ": ", typeOfLastMessageCall)
          astAssign.type = typeOfLastMessageCall
        }
      } else {
        // x = 7
        const receiverType = getReceiverType(assignRightValue.receiver)
        if (receiverType) {
          codeDB.setTypedValueToMethodScope(state.insideMessage, leftName, receiverType)
          astAssign.type = receiverType
        }
      }
      break;
    case "Constructor":
    case "CustomConstructor":
      // x = Person name: "Bob" age: 42
      codeDB.setTypedValueToMethodScope(currentMessageInfo, leftName, assignRightValue.type)
      astAssign.type = assignRightValue.type
      break;
    case "Setter":
      throw new Error(`You cant assign setter: ${assignRightValue.valueName} to value ${leftName}`)
    case "SwitchExpression":
    case "BracketExpression":
      throw new Error("TODO")
    default:
      const _never: never = assignRightValue
  }


  return astAssign;
}