import {TypeField} from "../niva";
import {BinaryMethodInfo, KeywordMethodInfo, newKeywordMethodInfo, newUnaryMethodInfo, UnaryMessageInfo} from "./types";

export class TypeInfo {
  // field name to info
  constructor(public fields: Map<string, TypeField>) {
    // fill getters and setters
    fields.forEach((value, key) => {
      // getter
      const unaryInfo = newUnaryMethodInfo(value.type)
      const unaryMessageInfo: UnaryMessageInfo = {
        returnType: value.type,
        effects: new Set(),
        statements: [],
        declaratedValueToType: new Map()
      }
      this.unaryMessages.set(key, unaryMessageInfo)

      //setter
      const setterInfo = newKeywordMethodInfo(value.type)
      setterInfo.effects.add("mutatesFields")
      this.keywordMessages.set(key, setterInfo)

    })

  }

  structural: boolean = false
  distinct: boolean = false

  unaryMessages: Map<string, UnaryMessageInfo> = new Map()
  binaryMessages: Map<string, BinaryMethodInfo> = new Map()
  keywordMessages: Map<string, KeywordMethodInfo> = new Map()

}