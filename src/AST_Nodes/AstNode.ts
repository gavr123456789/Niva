import { IntLiteral } from "./Statements/Expressions/Receiver/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Statements/Expressions/Receiver/Primary/Literals/StringLiteralNode"
import { BracketExpression, ElseBranch, MessageCallExpression, SwitchBranch, SwitchExpression, SwitchStatement } from "./Statements/Expressions/Expressions"
import { Assignment, BodyStatements, ReturnStatement, Statement  } from "./Statements/Statement"
import { Primary } from "./Statements/Expressions/Receiver/Primary/Primary"
import { BinaryArgument, KeywordArgument, MessageCall } from "./Statements/Expressions/Messages/Message"
import { TypeDeclaration, TypedProperty } from "./Statements/TypeDeclaration/TypeDeclaration"
import { BinaryMethodDeclarationArg, KeywordMethodArgument, KeywordMethodDeclarationArg, MethodDeclaration, UnaryMethodDeclaration } from "./Statements/MethodDeclaration/MethodDeclaration"
import { Identifer } from "./Statements/Expressions/Receiver/Primary/Identifier"
import { AnyLiteral } from "./Statements/Expressions/Receiver/Primary/Literals/AnyLiteral"

export type ASTNode = 
| StatementList
| MessageCallExpression
| BracketExpression
| Assignment
| AnyLiteral
| ReturnStatement
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
| BodyStatements
| SwitchBranch
| SwitchExpression
| SwitchStatement
| ElseBranch
| KeywordArgument

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}

