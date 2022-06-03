import { IntLiteral } from "./Expressions/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "./Expressions/Primary/Literals/StringLiteralNode"
import { BracketExpression, Expression } from "./Expressions/Expressions"
import { TypeDeclaration } from "./TypeDeclaration/TypeDeclaration"
import { MethodDeclaration } from "./MethodDeclaration/MethodDeclaration"
import { Receiver } from "./Expressions/Receiver/Receiver"

//TODO move
export interface BodyStatements {
  statements: Statement[]
  switchReturns: SwitchReturn[]


}
export interface SwitchReturn {
  expression: Expression,
  receiver: Receiver
}
//

export type Statement = 
  | Expression 
  | BracketExpression
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
  value: never
}


export enum Mutability {
  MUTABLE,
  IMUTABLE
}



