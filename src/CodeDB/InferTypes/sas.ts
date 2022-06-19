import { BracketExpression, Constructor, MessageCallExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Statement } from "../../AST_Nodes/Statements/Statement";
import {codeDB} from "../../niva";

export function inferStatementsType(statements: Statement[]) {
  if (statements.length === 0) return "void"
  let firstStatement = statements[0]
  // начинаем со второго до предпоследнего
  for (let i = 1; i < statements.length - 1; i++) {
    const statement = statements[i];
    inferStatementType(statement)
  }
}



function inferStatementType(s: Statement) {
  switch (s.kindStatement) {
    case "MessageCallExpression":
    case "BracketExpression":
      s.type = inferMessageCall(s)
      break;
    case "Constructor":
      // конструктор уже типизирован
      // inferConstructor(s)
      break;

    default:
      break;
  }
}

function inferMessageCall(sas: MessageCallExpression | BracketExpression): string {
  // TODO: пройтись в цикле по всем сообщениям, убедится что они есть у этих типов
  // TODO: проверить что они вызываются от нужных

  // Смотрим возвращаемый тип последнего сообщения, присваиваем его стейтменту
  const lastMessagecall = sas.messageCalls[sas.messageCalls.length-1]

  const methodReturnType = codeDB.getMethodReturnType(sas.selfTypeName, lastMessagecall.name, lastMessagecall.selectorKind)

  return methodReturnType

  // sas.receiver
}

function inferConstructor(s: Constructor): string {
  return s.type
}
