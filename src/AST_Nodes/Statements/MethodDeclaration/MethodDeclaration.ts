import { Identifer } from "../Expressions/Primary/Identifier"
import { Statement } from "../Statement"

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
  valueName: Identifer
}

export interface KeywordMethodDeclarationArg {
  methodArgKind: "Keyword"
  keyValueNames: KeywordMethodArgument[]
}


interface MethodDeclarationBase {
  expandableType: string
  returnType?: string
  bodyStatements: Statement[]
}

export interface UnaryMethodDeclaration extends MethodDeclarationBase {
  // Person sas = []

  methodKind: "UnaryMethodDeclaration"
  name: string
}

export interface BinaryMethodDeclaration extends MethodDeclarationBase {
  methodKind: "BinaryMethodDeclaration"
  binarySelector: string // +
  identifier: Identifer // x::int
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


// export type MethodArguments = UnaryMethodDeclarationArgs | BinaryMethodDeclarationArgs | KeywordMethodDeclarationArg 

// export interface MethodDeclaration {
//   kindStatement: "MethodDeclaration"
//   methodName: string,
//   returnType?: string
//   expandableType: string
//   arguments: MethodArguments
//   bodyStatements: Statement[]
// }