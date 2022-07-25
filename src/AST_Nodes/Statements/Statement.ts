import {Expression, SwitchStatement} from "./Expressions/Expressions"
import {TypeDeclaration, UnionDeclaration} from "./TypeDeclaration/TypeDeclaration"
import {ConstructorDeclaration, MethodDeclaration} from "./MethodDeclaration/MethodDeclaration"

//TODO move
export interface BodyStatements {
  statements: Statement[]
}

export type Statement =
  | Expression
  // | MessageCallExpression 
  // | BracketExpression
  | ReturnStatement
  | Assignment
  | TypeDeclaration
  | UnionDeclaration
  | MethodDeclaration
  | ConstructorDeclaration
  | SwitchStatement


export interface Assignment {
  kindStatement: "Assignment"
  assignmentTarget: string
  type?: string
  to: Expression // | BoolLiteralNode
  mutability: Mutability

  messagelineAndColumnMessage: string,
  sourceCode: string
}

export interface ReturnStatement {
  kindStatement: "ReturnStatement"
  value: Expression
}


export enum Mutability {
  MUTABLE,
  IMUTABLE
}



