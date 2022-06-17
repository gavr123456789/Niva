import { StatementList } from "../AST_Nodes/AstNode";
import { Statement } from "../AST_Nodes/Statements/Statement";
import { CodeDB } from "./codeDB";




export function makeBetterAst(codeDB: CodeDB, ast: StatementList) {
  for (const astStatementNode of ast.statements) {
    switch (astStatementNode.kindStatement) {
      case "Assignment":

        break;
      case "BracketExpression":
        break;
      case "MessageCallExpression":
        break;
      case "MethodDeclaration":
        break;
      case "ReturnStatement":
        break;
      case "SwitchExpression":
        break;
      case "SwitchStatement":
        break;
      case "TypeDeclaration":
        break;


      default:
        const never:never = astStatementNode
        break;
    }

  }
}

