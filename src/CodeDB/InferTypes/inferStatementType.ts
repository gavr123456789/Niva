import {Statement} from "../../AST_Nodes/Statements/Statement";

export function inferStatementType(lastBodyStatement: Statement): string | undefined {
  switch (lastBodyStatement.kindStatement) {
    case "ReturnStatement":
      throw new Error("TODO")

    case "MessageCallExpression":
      // console.log("lastBodyStatement MessageCallExpression type = ", lastBodyStatement.type)
      return lastBodyStatement.type
    case "BracketExpression":
      throw new Error("TODO")

    case "Constructor":
    case "CustomConstructor":
      return lastBodyStatement.type
    case "Setter":
      return "void"
    case "SwitchExpression":
      // throw new Error("not done yer")
      return "auto"
    // return lastBodyStatement.type
    case "Assignment":
      return "void"
    case "TypeDeclaration":
      return "void"
    case "MethodDeclaration":
      return "void"
    case "SwitchStatement":
      return "void"
    case "ConstructorDeclaration":
      return "void"
    default:
      const _never: never = lastBodyStatement
  }
}