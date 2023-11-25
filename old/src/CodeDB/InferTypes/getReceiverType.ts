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
          // console.log("!!! state.insideMessage = ",state.insideMessage , " receiver.atomReceiver.value = ", receiver.atomReceiver.value ," typeOfValInsideMethdod = ", typeOfValInsideMethdod)
          return typeOfValInsideMethdod
        default:
          const _never: never = receiver.atomReceiver
          console.log(receiver)
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

    case "ListLiteral":
    case "MapLiteral":
    case "SetLiteral":
      // throw new Error("TODO")
      return "auto"
    default:
      const _never: never = receiver
  }
}