import { BracketExpression, Constructor, MessageCallExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { Statement } from "../../AST_Nodes/Statements/Statement";

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

      inferMessageCall(s)
      break;
    case "Constructor":
      inferConstructor(s)
      break;

    default:
      break;
  }
}

function inferMessageCall(sas: MessageCallExpression | BracketExpression) {

  // sas.receiver
}

function inferConstructor(s: Constructor) {

}
