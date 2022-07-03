import { Identifier } from "../Expressions/Receiver/Primary/Identifier"
import { BodyStatements } from "../Statement"

export interface UnaryMethodDeclarationArgs {
  methodArgKind: "Unary"
  identifier: string
}

export interface BinaryMethodDeclarationArgs {
  methodArgKind: "Binary"
  binarySelector: string
  identifier: Identifier
}

export interface KeywordMethodArgument{
  keyName: string
  identifier: Identifier
}

export interface KeywordMethodDeclarationArg {
  keyValueNames: KeywordMethodArgument[]
}


interface MethodDeclarationBase {
  kind: "proc" | "template"
  expandableType: string
  returnType?: string
  bodyStatements: BodyStatements
  name: string
}

export interface UnaryMethodDeclaration extends MethodDeclarationBase {
  methodKind: "UnaryMethodDeclaration"
}

export interface BinaryMethodDeclaration extends MethodDeclarationBase {
  methodKind: "BinaryMethodDeclaration"
  argument: Identifier // x::int
}
export interface BinaryMethodDeclarationArg{
  binarySelector: string,
  identifier: Identifier
}

export interface KeywordMethodDeclaration extends MethodDeclarationBase {
  methodKind: "KeywordMethodDeclaration"
  keyValueNames: KeywordMethodArgument[]
}

export interface MethodDeclaration {
  kindStatement: "MethodDeclaration"
  method: MethodDeclarationNode
}

export interface ConstructorDeclaration {
  kindStatement: "ConstructorDeclaration"
  method: MethodDeclarationNode
}

type MethodDeclarationNode = 
  | UnaryMethodDeclaration
  | BinaryMethodDeclaration
  | KeywordMethodDeclaration

