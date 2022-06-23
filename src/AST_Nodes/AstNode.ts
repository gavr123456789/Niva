import { BracketExpression, Constructor, ElseBranch, MessageCallExpression, SwitchBranch, SwitchExpression, SwitchStatement } from "./Statements/Expressions/Expressions"
import { Assignment, BodyStatements, ReturnStatement, Statement  } from "./Statements/Statement"
import { Primary } from "./Statements/Expressions/Receiver/Primary/Primary"
import { BinaryArgument, KeywordArgument, MessageCall } from "./Statements/Expressions/Messages/Message"
import { TypeDeclaration, TypedProperty } from "./Statements/TypeDeclaration/TypeDeclaration"
import { BinaryMethodDeclarationArg, KeywordMethodArgument, KeywordMethodDeclarationArg, MethodDeclaration } from "./Statements/MethodDeclaration/MethodDeclaration"
import { Identifier } from "./Statements/Expressions/Receiver/Primary/Identifier"
import { AnyLiteral } from "./Statements/Expressions/Receiver/Primary/Literals/AnyLiteral"
import {CallLikeExpression} from "../CodeGenerator/expression/callLikeExpression";

export type ASTNode = 
| StatementList
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
| Identifier
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
| CallLikeExpression

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}



