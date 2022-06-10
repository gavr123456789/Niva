import { IntLiteral } from "./Expressions/Receiver/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Expressions/Receiver/Primary/Literals/StringLiteralNode"
import { BracketExpression, Expression, MessageCallExpression } from "./Expressions/Expressions"
import { TypeDeclaration } from "./TypeDeclaration/TypeDeclaration"
import { MethodDeclaration } from "./MethodDeclaration/MethodDeclaration"
import { Receiver } from "./Expressions/Receiver/Receiver"

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
  | MethodDeclaration


export interface Assignment {
  kindStatement: "Assignment"
  assignmentTarget: string
  type?: string
  to: Expression // | BoolLiteralNode
  mutability: Mutability 

  messagelineAndColumnMessage: string,
  sourceCode: string
  file: "" // TODO
}

export interface ReturnStatement {
  kindStatement: "ReturnStatement"
  value: Expression
}


export enum Mutability {
  MUTABLE,
  IMUTABLE
}



