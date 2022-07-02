export type EffectType = "mutatesFields"

export interface ArgInfo {
  // variable name
  usedUnaryMessagesOnArg: Map<string, Set<string>>
  usedBinaryMessagesOnArg: Map<string, Set<string>>
  usedKeywordMessagesOnArg: Map<string, Set<string>>
}
export function newArgInfo(): ArgInfo {
  return {
    usedBinaryMessagesOnArg: new Map(),
    usedKeywordMessagesOnArg: new Map(),
    usedUnaryMessagesOnArg: new Map()
  }
}

function setMessageForValue(sas1: Set<string> | undefined, messageSelector: string, selfMap: Map<string, Set<string>>, valueName: string) {
  if (sas1) {
    sas1.add(messageSelector)
  } else {
    const setUnary = new Set<string>()
    setUnary.add(messageSelector)
    selfMap.set(valueName, setUnary)
  }
}

function getMessageForValue(sas1: Set<string> | undefined, messageSelector: string, selfMap: Map<string, Set<string>>, valueName: string): boolean {
  if (sas1) {
    return sas1.has(messageSelector)
  } else {
    return false
  }
}

export function addToArgInfo(self: ArgInfo, valueName: string, messageSelector: string, kind: "unary" | "binary" | "keyword") {
  switch (kind) {
    case "unary":
      const sas1 = self.usedUnaryMessagesOnArg.get(valueName)
      setMessageForValue(sas1, messageSelector, self.usedUnaryMessagesOnArg, valueName);
      break;
    case "binary":
      const sas2 = self.usedUnaryMessagesOnArg.get(valueName)
      setMessageForValue(sas2, messageSelector, self.usedBinaryMessagesOnArg, valueName);

      break;
    case "keyword":
      const sas3 = self.usedUnaryMessagesOnArg.get(valueName)
      setMessageForValue(sas3, messageSelector, self.usedKeywordMessagesOnArg, valueName);
      break;
  }
}

export function hasSelectorArgInfo(self: ArgInfo, valueName: string, messageSelector: string, kind: "unary" | "binary" | "keyword"): boolean {
  switch (kind) {
    case "unary":
      const sas1 = self.usedUnaryMessagesOnArg.get(valueName)
      return getMessageForValue(sas1, messageSelector, self.usedUnaryMessagesOnArg, valueName);

    case "binary":
      const sas2 = self.usedUnaryMessagesOnArg.get(valueName)
      return getMessageForValue(sas2, messageSelector, self.usedBinaryMessagesOnArg, valueName);

    case "keyword":
      const sas3 = self.usedUnaryMessagesOnArg.get(valueName)
      return getMessageForValue(sas3, messageSelector, self.usedKeywordMessagesOnArg, valueName);
  }
}


export interface DefaultMessageInfo {
  // code?: string
  returnType: string,
  effects: Set<EffectType>
  // statements: Statement[]
  declaratedValueToType: Map<string, string>
  argsNamesToInfo: Map<string, ArgInfo>
  needInferArgs: boolean
}
function newDefaultMessageInfo(returnType: string, needInferArgs: boolean = false): DefaultMessageInfo {
  return {
    returnType,
    effects: new Set(),
    // statements: [],
    declaratedValueToType: new Map(),
    needInferArgs: needInferArgs,
    argsNamesToInfo: new Map()
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

