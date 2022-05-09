import { IntLiteralNode } from "../Literals/IntLiteralNode"
import { StringLiteralNode } from "../Literals/StringLiteralNode"

export type Statement = 
  | ExpressionStatement 
  | ReturnStatement 
  | MethodDeclarationStatement 
  | TypeDeclarationStatement
  | Assignment


export interface Assignment {
  kindStatement: "Assignment"
  assignmentTarget: string
  type?: string
  to: StringLiteralNode | IntLiteralNode // | BoolLiteralNode
  mutability: Mutability 

  messagelineAndColumnMessage: string,
  sourceCode: string
  file: "" // TODO
}

export interface ExpressionStatement {
  kindStatement: "ExpressionStatement"
  value: Assignment // | BasicExpression
}
export interface ReturnStatement {
  kindStatement: "ReturnStatement"
  value: never
}
export interface TypeDeclarationStatement {
  kindStatement: "TypeDeclarationStatement"
  value: never
}
export interface MethodDeclarationStatement {
  kindStatement: "MethodDeclarationStatement"
  value: never
}

export enum Mutability {
  MUTABLE,
  IMUTABLE
}



