import { IntLiteral } from "./Statements/Expressions/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Statements/Expressions/Primary/Literals/StringLiteralNode"
import { Expression, MessageCall } from "./Statements/Expressions/Expressions"
import { Assignment, Statement  } from "./Statements/Statement"
import { Primary } from "./Statements/Expressions/Primary/Primary"

export type ASTNode = 
| StatementList
| Expression
| Assignment
| StringLiteral
| IntLiteral
| MessageCall
| MessageCall[]
| Primary

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}

