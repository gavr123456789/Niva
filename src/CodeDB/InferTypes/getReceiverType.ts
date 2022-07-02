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
          const resultPrimary = receiver.atomReceiver.kindPrimary
          receiver.type = resultPrimary
          return resultPrimary
        case "Identifier":
          const typeOfValInsideMethod = codeDB.getValueType(state.insideMessage, receiver.atomReceiver.value)
          receiver.type = typeOfValInsideMethod
          return typeOfValInsideMethod
      }
      break;
    case "BlockConstructor":
      const lastStatement = receiver.statements.at(-1)
      if (lastStatement){
        const lastStatementType = getStatementType(lastStatement)
        receiver.type = lastStatementType
        return lastStatementType
      }
      throw new Error("blockConstructor has no statements")
    case "BracketExpression":
      const resultExpressionType = getTypeOfExpression(receiver)
      receiver.type = resultExpressionType
      return resultExpressionType
  }
}