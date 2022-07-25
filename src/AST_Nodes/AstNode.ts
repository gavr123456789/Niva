import {ElseBranch, SwitchBranch, SwitchExpression, SwitchStatement} from "./Statements/Expressions/Expressions"
import {Assignment, BodyStatements, ReturnStatement, Statement} from "./Statements/Statement"
import {Primary} from "./Statements/Expressions/Receiver/Primary/Primary"
import {BinaryArgument, KeywordArgument, MessageCall} from "./Statements/Expressions/Messages/Message"
import {
  TypeDeclaration,
  TypedProperty,
  UnionBranch,
  UnionDeclaration
} from "./Statements/TypeDeclaration/TypeDeclaration"
import {
  BinaryMethodDeclarationArg,
  ConstructorDeclaration,
  KeywordMethodArgument,
  KeywordMethodDeclarationArg,
  MethodDeclaration
} from "./Statements/MethodDeclaration/MethodDeclaration"
import {Identifier} from "./Statements/Expressions/Receiver/Primary/Identifier"
import {SimpleLiteral} from "./Statements/Expressions/Receiver/Primary/Literals/SimpleLiteral"
import {CallLikeExpression} from "../CodeGenerator/expression/callLikeExpression";
import {CollectionLiteral, KeyValue} from "./Statements/Expressions/Receiver/Primary/Literals/CollectionLiteral";

export type ASTNode =
  | StatementList
  | Assignment
  | SimpleLiteral
  | CollectionLiteral
  | ReturnStatement
  | MessageCall
  | MessageCall[]
  | BinaryArgument

  | TypeDeclaration

  | UnionDeclaration
  | UnionBranch
  | UnionBranch[]

  | TypedProperty
  | TypedProperty[]

  | Primary
  | Identifier
  | MethodDeclaration
  | ConstructorDeclaration
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
  | Primary[]
  | KeyValue[]
  | KeyValue

export interface StatementList {
  kind: "StatementList"
  statements: Statement[] // typeDeclaration, methodDeclaration
}



