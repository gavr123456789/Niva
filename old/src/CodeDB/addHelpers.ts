import {TypeField} from "../niva";
import {TypeInfo} from "./TypeInfo";
import {TypedProperty} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration";

export function addDefaultType(typeNameToInfo: Map<string, TypeInfo>, name: string) {
  if (typeNameToInfo.has(name)){
    throw new Error(`Type ${name} already added`)
  }

  const defaultSimpleTypeProperty: TypedProperty = {
    type: name,
    identifier: "value"
  }
  typeNameToInfo.set(name, new TypeInfo([defaultSimpleTypeProperty], false))
}


// function addUnaryMessageForType(typeNameToInfo: Map<string, TypeInfo>, unaryMessages: Map<string, UnaryMessageInfo>, typeName: string, selectorName: string, info: UnaryMessageInfo) {
//   const type = typeNameToInfo.get(typeName)
//   if (!type) {
//     throw new Error("trying to add method for non existing type");
//   }
//   unaryMessages.set(selectorName, info)
// }
