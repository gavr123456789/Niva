import {Statement} from "../../AST_Nodes/Statements/Statement";

export function getStatementType(lastBodyStatement: Statement): string | undefined {
  switch (lastBodyStatement.kindStatement) {
    case "ReturnStatement":

      break;
    case "MessageCallExpression":
      // console.log("lastBodyStatement MessageCallExpression type = ", lastBodyStatement.type)
      return lastBodyStatement.type
    case "BracketExpression":

      break;
    case "Constructor":
      return lastBodyStatement.type
    case "Setter":
      return "void"
    case "SwitchExpression":
      throw new Error("not done yer")
    // return lastBodyStatement.type
    case "Assignment":
      return "void"
    case "TypeDeclaration":
      return "void"
    case "MethodDeclaration":
      return "void"
    case "SwitchStatement":
      return "void"
  }
}