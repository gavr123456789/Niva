import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {codeDB, state} from "../../niva";
import {getTypeOfExpression} from "./getTypeOfExpression";
import {getStatementType} from "./getStatementType";

export function getReceiverType(receiver: Receiver): string | undefined {
  switch (receiver.kindStatement) {
    case "Primary":
      switch (receiver.atomReceiver.kindPrimary) {
        case "string":
        case "int":
        case "bool":
          return receiver.atomReceiver.kindPrimary
        case "Identifier":
          const typeOfValInsideMethdod = codeDB.getValueType(state.insideMessage, receiver.atomReceiver.value)
          return typeOfValInsideMethdod
      }
      break;
    case "BlockConstructor":
      const lastStatement = receiver.statements.at(-1)
      if (lastStatement){
        return getStatementType(lastStatement)
      }
      throw new Error("blockConstructor has no statements")
    case "BracketExpression":
      return getTypeOfExpression(receiver)
  }
}