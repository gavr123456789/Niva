import {ContextInformation, TypeField} from "../niva"
import {
  BinaryMethodInfo,
  EffectType,
  KeywordMethodInfo, newBinaryMethodInfo,
  newKeywordMethodInfo,
  newUnaryMethodInfo,
  UnaryMessageInfo
} from "./types"

export type MethodKinds = "unary" | "binary" | "keyword" | "__global__"


class TypeInfo {
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

export class CodeDB {
  private globalDeclaratedValueToType: Map<string, string> = new Map();

  setTypedValueToMethodScope(insideMethod: ContextInformation, valueName: string, valueType: string) {
    const {forType: typeName, kind, withName: messageName} = insideMethod

    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      console.error("typeName = ", typeName);
      throw new Error("trying to add method for non existing type");
    }

    switch (kind) {
      case "unary":
        const unaryMessage = type.unaryMessages.get(messageName)
        if (!unaryMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: ${type.unaryMessages}`);
          throw new Error("unary not found")
          return
        }
        const alreadyDefinedUnary = unaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedUnary) {
          // console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        unaryMessage.declaratedValueToType.set(valueName, valueType)
        // console.log(`added val with name: ${valueName}, type: ${valueType} inside unary method ${messageName} for type ${typeName}`);

        break;
      case "binary":
        const binaryMessage = type.binaryMessages.get(messageName)
        if (!binaryMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: ${type.binaryMessages}`);
          return
        }
        const alreadyDefinedBinary = binaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedBinary) {
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        binaryMessage.declaratedValueToType.set(valueName, valueType)
        // console.log(`added val with name: ${valueName}, type: ${valueType} inside binary method ${messageName} for type ${typeName}`);

        break;
      case "keyword":
        const keywordMessage = type.keywordMessages.get(messageName)
        if (!keywordMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: ${type.keywordMessages}`);
          return
        }
        const alreadyDefinedKeyword = keywordMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedKeyword) {
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        keywordMessage.declaratedValueToType.set(valueName, valueType)

        // console.log(`added val with name: ${valueName}, type: ${valueType} inside keyword method ${messageName} for type ${typeName}`);

        break;

      case "__global__":
        this.globalDeclaratedValueToType.set(valueName, valueType)

        // console.log(`added val with name: ${valueName}, type: ${valueType} inside global scope ${messageName} for type ${typeName}`);

        break;

      default:
        const never: never = kind
        break;
    }
  }

  getValueType(insideMethod: ContextInformation, valueName: string): string | undefined {
    const {forType: typeName, kind, withName: messageName} = insideMethod

    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      console.error("typeName = ",);
      throw new Error(`trying to add method for non existing type: ${typeName}`);
    }

    switch (kind) {
      case "unary":
        const unaryMessage = type.unaryMessages.get(messageName)
        if (!unaryMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: ${type.unaryMessages}`);
          return
        }
        return unaryMessage.declaratedValueToType.get(valueName)
      case "binary":
        const binaryMessage = type.binaryMessages.get(messageName)
        if (!binaryMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: `, type.binaryMessages);
          throw Error("Message not found")

        }

        return binaryMessage.declaratedValueToType.get(valueName)
      // console.log(`added val with name: ${valueName}, type: ${valueType} inside binary method ${messageName} for type ${typeName}`);

      case "keyword":
        const keywordMessage = type.keywordMessages.get(messageName)
        if (!keywordMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}`);
          console.log(`All known messages are: ${type.keywordMessages}`);
          return
        }

        return keywordMessage.declaratedValueToType.get(valueName)

      case "__global__":
        return this.globalDeclaratedValueToType.get(valueName)

      default:
        const never: never = kind
        break;
    }


  }


  private typeNameToInfo: Map<string, TypeInfo> = new Map()


  private addDefaultType(name: string) {
    const intFields = new Map<string, TypeField>()
    intFields.set("value", {type: name})
    this.typeNameToInfo.set(name, new TypeInfo(intFields))
  }

  constructor() {
    // add default types and functions
    this.addDefaultType("__global__")
    this.addDefaultType("int")
    // int unary
    this.addUnaryMessageForType("int", "toString", newUnaryMethodInfo("string"))
    this.addUnaryMessageForType("int", "echo", newUnaryMethodInfo("void"))
    // int binary
    this.addBinaryMessageForType("int", "+", newBinaryMethodInfo("int"))
    this.addBinaryMessageForType("int", "-", newBinaryMethodInfo("int"))
    this.addBinaryMessageForType("int", "*", newBinaryMethodInfo("int"))
    this.addBinaryMessageForType("int", "/", newBinaryMethodInfo("int"))
    this.addBinaryMessageForType("int", ">", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("int", "<", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("int", "==", newBinaryMethodInfo("bool"))
    // int keyword
    this.addKeywordMessageForType("int", "to_do", newKeywordMethodInfo("void"))
    this.addKeywordMessageForType("int", "add", newKeywordMethodInfo("void"))
    this.addKeywordMessageForType("int", "mod", newKeywordMethodInfo("int"))


    this.addDefaultType("uint")

    this.addDefaultType("string")
    // string unary
    this.addUnaryMessageForType("string", "echo", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("string", "print", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("string", "toString", newUnaryMethodInfo("string"))
    // string binary
    this.addBinaryMessageForType("string", "==", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("string", "!=", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("string", "&", newBinaryMethodInfo("string"))


    this.addDefaultType("bool")
    // bool unary
    this.addUnaryMessageForType("bool", "toString", newUnaryMethodInfo("string"))
    this.addUnaryMessageForType("bool", "echo", newUnaryMethodInfo("void"))
    // bool binary
    this.addBinaryMessageForType("bool", "==", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "!=", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "||", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "&&", newBinaryMethodInfo("bool"))

    this.addDefaultType("float")
  }

  addNewType(typeName: string, fields: Map<string, TypeField>) {
    const typeInfo = new TypeInfo(fields)
    this.typeNameToInfo.set(typeName, typeInfo)
    // add getters and setters
    fields.forEach((v, fieldName) => {
      this.addUnaryMessageForType(typeName, fieldName, newUnaryMethodInfo(v.type))
      this.addKeywordMessageForType(typeName, fieldName, newUnaryMethodInfo("void"))
      // console.log("added getter, setter for type ", typeName, " fieldName: ", fieldName, " with type ", v.type)
    })

  }

  addKeywordMessageForType(typeName: string, selectorName: string, info: KeywordMethodInfo) {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error("trying to add method for non existing type");
    }
    type.keywordMessages.set(selectorName, info)
  }

  addUnaryMessageForType(typeName: string, selectorName: string, info: UnaryMessageInfo) {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error("trying to add method for non existing type");
    }
    type.unaryMessages.set(selectorName, info)
  }

  addBinaryMessageForType(typeName: string, selectorName: string, info: BinaryMethodInfo) {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error("trying to add method for non existing type");
    }
    type.binaryMessages.set(selectorName, info)
  }

  hasMutateEffect(typeName: string, methodSelector: string): boolean {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error("trying to check effect of non existing type");
    }

    const unary = !!type.unaryMessages.get(methodSelector)?.effects.has("mutatesFields")
    const binary = !!type.binaryMessages.get(methodSelector)?.effects.has("mutatesFields")
    const keyword = !!type.keywordMessages.get(methodSelector)?.effects.has("mutatesFields")
    // console.log("type.keywordMessages = ", type.keywordMessages)
    return unary || binary || keyword
  }

  addEffectForMethod(selfType: string, methodKind: MethodKinds, methodName: string, effect: { kind: EffectType; }) {
    const type = this.typeNameToInfo.get(selfType)
    if (!type) {
      throw new Error("trying to add effect to non existing type");
    }

    if (methodName.length === 0) {
      throw new Error("MethodName is Empty");
    }

    switch (methodKind) {
      case "keyword":
        const keywordMethod = type.keywordMessages.get(methodName)
        if (!keywordMethod) {
          console.error(`All known keywordMethods of type ${selfType}:`)

          throw new Error(`trying to add effecto for non existing method: ${methodName}`);
        }
        keywordMethod.effects.add(effect.kind)
        break;
      case "binary":
        const binaryMethod = type.binaryMessages.get(methodName)
        if (!binaryMethod) {
          throw new Error("trying to add effect for non existing method");
        }
        binaryMethod.effects.add(effect.kind)
        break;
      case "unary":
        const unaryMethod = type.unaryMessages.get(methodName)
        if (!unaryMethod) {
          throw new Error("trying to add effecto for non existing method");
        }
        unaryMethod.effects.add(effect.kind)
        break;
        case "__global__":
          throw new Error("TODO global");
          

      default:
        const _never: never = methodKind
        break;
    }
  }

  hasType(name: string) {
    return this.typeNameToInfo.has(name)
  }

  getFieldType(typeName: string, keyName: string): string | undefined {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      return undefined
    }
    return type.fields.get(keyName)?.type
  }

  getMethodReturnType(typeName: string, methodName: string, kind: MethodKinds): string {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error(`trying to get return type of non existing type: ${typeName} for methodName: ${methodName}`);
    }
    switch (kind) {
      case "unary":
        const unaryMethod = type.unaryMessages.get(methodName)
        if (!unaryMethod) {
          console.log(`all known unaryMessages of type:  `, typeName," = ", type.unaryMessages)
          throw new Error(`no such unary method: ${methodName} `)
        }
        if (unaryMethod.returnType === "auto") {
          throw new Error(`Return type of: ${methodName}, is auto`)
        }
        return unaryMethod.returnType
      case "binary":
        const binaryMethod = type.binaryMessages.get(methodName)
        if (!binaryMethod) {
          console.log("all known types = ", type.binaryMessages)
          throw new Error(`no such binary method: ${methodName}`)
        }
        if (binaryMethod.returnType === "auto") {
          throw new Error(`Return type of: ${methodName}, is auto`)
        }
        return binaryMethod.returnType
      case "keyword":
        const keywordMethod = type.keywordMessages.get(methodName)
        if (!keywordMethod) {
          throw new Error(`no such unary method: ${methodName}, all known methods: ${type.keywordMessages}`)
        }
        if (keywordMethod.returnType === "auto") {
          throw new Error(`Return type of: ${methodName}, is auto`)
        }
        return keywordMethod.returnType

        case "__global__":
          throw new Error("TODO global");
          
      default:
        const _never:never = kind
        throw new Error("Sound error");

    }
  }
}
