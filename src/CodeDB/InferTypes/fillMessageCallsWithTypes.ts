import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {codeDB} from "../../niva";

export function fillMessageCallsWithTypes(receiverType: string, astMessages: MessageCall[]) {

  astMessages.forEach((msg, i, array) => {
    const previousMsg = array[i - 1]
    const previousType = previousMsg ? previousMsg.type.name : receiverType
    const returnType = codeDB.getMethodReturnType(previousType, msg.name, msg.selectorKind)
    if (!returnType)
      return null
    msg.type.name = returnType
  })
}