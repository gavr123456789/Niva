import { IntLiteral } from "./Expressions/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Expressions/Primary/Literals/StringLiteralNode"
import { Expression } from "./Expressions/Expressions"

export type Statement = 
  | Expression 
  | ReturnStatement 
  | MethodDeclarationStatement 
  | TypeDeclarationStatement
  | Assignment


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



