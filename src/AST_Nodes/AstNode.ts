import { IntLiteralNode } from "./Literals/IntLiteralNode"
import { StringLiteralNode } from "./Literals/StringLiteralNode"
import { Assignment, ExpressionStatement, Statement  } from "./Statements/Statement"

export type ASTNode = 
| StatementList
| ExpressionStatement
| Assignment
| StringLiteralNode
| IntLiteralNode

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}

