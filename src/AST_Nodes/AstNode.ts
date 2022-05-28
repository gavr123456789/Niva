import { IntLiteral } from "./Statements/Expressions/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Statements/Expressions/Primary/Literals/StringLiteralNode"
import { Expression } from "./Statements/Expressions/Expressions"
import { Assignment, Statement  } from "./Statements/Statement"
import { Primary } from "./Statements/Expressions/Primary/Primary"
import { BinaryArgument, MessageCall } from "./Statements/Expressions/Messages/Message"
import { TypeDeclaration, TypedProperty } from "./Statements/TypeDeclaration/TypeDeclaration"
import { BinaryMethodDeclarationArg, KeywordMethodArgument, KeywordMethodDeclarationArg, MethodDeclaration, UnaryMethodDeclaration } from "./Statements/MethodDeclaration/MethodDeclaration"
import { Identifer } from "./Statements/Expressions/Primary/Identifier"

export type ASTNode = 
| StatementList
| Expression
| Assignment
| StringLiteral
| IntLiteral
| MessageCall
| MessageCall[]
| BinaryArgument
| TypeDeclaration
| TypedProperty
| TypedProperty[]
| Primary
| Identifer
| MethodDeclaration
| BinaryMethodDeclarationArg
| KeywordMethodDeclarationArg
| KeywordMethodArgument
| Statement[]

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}

