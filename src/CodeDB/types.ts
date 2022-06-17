import { Statement } from "../AST_Nodes/Statements/Statement"

export interface MessageCallInfo {
  callStack: string[]
}

export type EffectType = "mutatesFields"

export interface DefaultMessageInfo {
  code?: string
  effects: Set<EffectType>
  statements: Statement[]
  declaratedValueToType: Map<string, string>
}
function newDefaultMessageInfo(): DefaultMessageInfo {
  return {
    effects: new Set(),
    statements: [],
    declaratedValueToType: new Map()
  }
}


export interface UnaryMessageInfo extends DefaultMessageInfo {
  // ast: UnaryMethodDeclaration,

}
export function newUnaryMessageInfo(): UnaryMessageInfo {
  return newDefaultMessageInfo()
}

export interface BinaryMethodInfo extends DefaultMessageInfo {
  // ast: BinaryMethodDeclaration,
}

export function newBinaryMethodInfo(): BinaryMethodInfo {
  return newDefaultMessageInfo()
}

export interface KeywordMethodInfo extends DefaultMessageInfo {
  // ast: KeywordMethodDeclaration,
}

export function newKeywordMethodInfo(): KeywordMethodInfo {
  return newDefaultMessageInfo()
}

