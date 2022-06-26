import { Statement } from "../AST_Nodes/Statements/Statement"

export interface MessageCallInfo {
  callStack: string[]
}

export type EffectType = "mutatesFields"

export interface DefaultMessageInfo {
  code?: string
  returnType: string,
  effects: Set<EffectType>
  statements: Statement[]
  declaratedValueToType: Map<string, string>
}
function newDefaultMessageInfo(returnType: string): DefaultMessageInfo {
  return {
    returnType,
    effects: new Set(),
    statements: [],
    declaratedValueToType: new Map()
  }
}


export interface UnaryMessageInfo extends DefaultMessageInfo {
  // ast: UnaryMethodDeclaration,
}
export function newUnaryMethodInfo(returnType: string): UnaryMessageInfo {
  return newDefaultMessageInfo(returnType)
}

export interface BinaryMethodInfo extends DefaultMessageInfo {
  // ast: BinaryMethodDeclaration,
}

export function newBinaryMethodInfo(returnType: string): BinaryMethodInfo {
  return newDefaultMessageInfo(returnType)
}

export interface KeywordMethodInfo extends DefaultMessageInfo {
  // ast: KeywordMethodDeclaration,
}

export function newKeywordMethodInfo(returnType: string): KeywordMethodInfo {
  return newDefaultMessageInfo(returnType)
}

