import { MessageCallExpression } from "../Expressions/Expressions"
import { Identifer } from "../Expressions/Primary/Identifier"
import { Receiver } from "../Expressions/Receiver/Receiver"
import { BodyStatements, Statement } from "../Statement"

export interface UnaryMethodDeclarationArgs {
  methodArgKind: "Unary"
  identifier: string
}

export interface BinaryMethodDeclarationArgs {
  methodArgKind: "Binary"
  binarySelector: string
  identifier: Identifer
}

export interface KeywordMethodArgument{
  keyName: string
  identifier: Identifer
}

export interface KeywordMethodDeclarationArg {
  keyValueNames: KeywordMethodArgument[]
}


interface MethodDeclarationBase {
  expandableType: string
  returnType?: string
  bodyStatements: BodyStatements
}

export interface UnaryMethodDeclaration extends MethodDeclarationBase {
  methodKind: "UnaryMethodDeclaration"
  name: string
}

export interface BinaryMethodDeclaration extends MethodDeclarationBase {
  methodKind: "BinaryMethodDeclaration"
  binarySelector: string // +
  identifier: Identifer // x::int
}
export interface BinaryMethodDeclarationArg{
  binarySelector: string,
  identifier: Identifer
}

export interface KeywordMethodDeclaration extends MethodDeclarationBase {
  methodKind: "KeywordMethodDeclaration"
  keyValueNames: KeywordMethodArgument[]
}

export interface MethodDeclaration {
  kindStatement: "MethodDeclaration"
  method: MethodDeclarationNode
}

type MethodDeclarationNode = 
  | UnaryMethodDeclaration
  | BinaryMethodDeclaration
  | KeywordMethodDeclaration

