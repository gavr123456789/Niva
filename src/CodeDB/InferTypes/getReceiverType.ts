import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {codeDB, state} from "../../niva";
import {getTypeOfExpression} from "./getTypeOfExpression";
import {inferStatementType} from "./inferStatementType";

export function getReceiverType(receiver: Receiver): string | undefined {
  switch (receiver.kindStatement) {
    case "Primary":
      switch (receiver.atomReceiver.kindPrimary) {
        case "string":
        case "int":
        case "bool":
        case "float":
          return receiver.atomReceiver.kindPrimary
        case "Identifier":
          const typeOfValInsideMethdod = codeDB.getValueType(state.insideMessage, receiver.atomReceiver.value)
          return typeOfValInsideMethdod
        default:
          const _never: never = receiver.atomReceiver
          throw new Error("Sound error")
      }
    case "BlockConstructor":
      const lastStatement = receiver.statements.at(-1)
      if (lastStatement){
        return inferStatementType(lastStatement)
      }
      throw new Error("blockConstructor has no statements")
    case "BracketExpression":
      return getTypeOfExpression(receiver)
  }
}