import {TypeField} from "../niva";
import {TypeInfo} from "./TypeInfo";

export function addDefaultType(typeNameToInfo: Map<string, TypeInfo>, name: string) {
  if (typeNameToInfo.has(name)){
    throw new Error(`Type ${name} already added`)
  }
  const fields = new Map<string, TypeField>()
  fields.set("value", {type: name})
  typeNameToInfo.set(name, new TypeInfo(fields))
}


// function addUnaryMessageForType(typeNameToInfo: Map<string, TypeInfo>, unaryMessages: Map<string, UnaryMessageInfo>, typeName: string, selectorName: string, info: UnaryMessageInfo) {
//   const type = typeNameToInfo.get(typeName)
//   if (!type) {
//     throw new Error("trying to add method for non existing type");
//   }
//   unaryMessages.set(selectorName, info)
// }