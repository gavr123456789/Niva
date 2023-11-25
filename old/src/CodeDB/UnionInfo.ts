import {TypeField} from "../niva";
import {BinaryMethodInfo, KeywordMethodInfo, newKeywordMethodInfo, newUnaryMethodInfo, UnaryMessageInfo} from "./types";
import {TypeInfo} from "./TypeInfo";
import {UnionBranch, UnionDeclaration} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration";



export class UnionInfo {
  // public defaultFields: Map<string, TypeField>,
  private branches: UnionBranch[] = []
  public defaultFields: Map<string, TypeField> = new Map()
  constructor(union: UnionDeclaration) {

    /// Properties
    union.defaultProperties.forEach(x => {
      this.defaultFields.set(x.identifier, {type: x.type ?? "auto"})
    })

    /// Branches
    this.branches = union.branches


    // generate getters and setters
    this.defaultFields.forEach((value, key) => {
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






  unaryMessages: Map<string, UnaryMessageInfo> = new Map()
  binaryMessages: Map<string, BinaryMethodInfo> = new Map()
  keywordMessages: Map<string, KeywordMethodInfo> = new Map()

}
