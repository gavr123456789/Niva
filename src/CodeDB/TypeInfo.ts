import {TypeField} from "../niva";
import {BinaryMethodInfo, KeywordMethodInfo, newKeywordMethodInfo, newUnaryMethodInfo, UnaryMessageInfo} from "./types";
import {TypedProperty} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration";

export class TypeInfo {

  fields = new Map<string, TypeField>();

  // in the future isUnion can be nullable case field name
  // for now it always "kind"
  constructor(typedProperties: TypedProperty[], public isUnion: boolean, public unionParentName?: string) {

    typedProperties.forEach(x => {
      this.fields.set(x.identifier, {type: x.type ?? "auto"})
    })


    // fill getters and setters
    this.fields.forEach((value, key) => {
      // getter
      const unaryInfo = newUnaryMethodInfo(value.type)
      this.unaryMessages.set(key, unaryInfo)

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
