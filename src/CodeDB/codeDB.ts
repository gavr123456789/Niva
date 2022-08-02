import {codeDB, ContextInformation, TypeField} from "../niva"
import {
  BinaryMethodInfo,
  EffectType,
  KeywordMethodInfo,
  newBinaryMethodInfo,
  newKeywordMethodInfo,
  newUnaryMethodInfo,
  UnaryMessageInfo
} from "./types"
import {TypeInfo} from "./TypeInfo";
import {addDefaultType} from "./addHelpers";
import {UnionInfo} from "./UnionInfo";
import {TypedProperty, UnionDeclaration} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration";

export type MethodKinds = "unary" | "binary" | "keyword" | "__global__"


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
  private unionNameToInfo: Map<string, UnionInfo> = new Map()

  hasUnion(unionName: string): boolean{
    return this.unionNameToInfo.has(unionName)
  }

  // по дефолту все добавляется в typeNameToInfo
  // если при добавлении там уже такой есть то считываем название модуля,
  // если его нет у идентификатора то ошибка, если в данном модуле нет такого идентификатора то ошибка

  // по дефолту название модуля это название файла
  private modules: Map<string, CodeDB> = new Map()

  constructor() {
    // add default types and functions
    addDefaultType(this.typeNameToInfo, "__global__")
    addDefaultType(this.typeNameToInfo, "int")
    addDefaultType(this.typeNameToInfo, "auto")
    addDefaultType(this.typeNameToInfo, "void") // temp
    this.addUnaryMessageForType("auto", "echo", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("void", "echo", newUnaryMethodInfo("void")) // temp
    this.addUnaryMessageForType("auto", "toStr", newUnaryMethodInfo("void"))

    // int unary
    this.addUnaryMessageForType("int", "toStr", newUnaryMethodInfo("string"))
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

    // float
    addDefaultType(this.typeNameToInfo, "float")
    // float unary
    this.addUnaryMessageForType("float", "toStr", newUnaryMethodInfo("string"))
    this.addUnaryMessageForType("float", "echo", newUnaryMethodInfo("void"))
    // float binary
    this.addBinaryMessageForType("float", "+", newBinaryMethodInfo("float"))
    this.addBinaryMessageForType("float", "-", newBinaryMethodInfo("float"))
    this.addBinaryMessageForType("float", "*", newBinaryMethodInfo("float"))
    this.addBinaryMessageForType("float", "/", newBinaryMethodInfo("float"))
    this.addBinaryMessageForType("float", ">", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("float", "<", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("float", "==", newBinaryMethodInfo("bool"))
    //uint

    addDefaultType(this.typeNameToInfo,"uint")

    addDefaultType(this.typeNameToInfo,"string")
    // string unary
    this.addUnaryMessageForType("string", "echo", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("string", "print", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("string", "printnln", newUnaryMethodInfo("void"))
    this.addUnaryMessageForType("string", "toStr", newUnaryMethodInfo("string"))
    // string binary
    this.addBinaryMessageForType("string", "==", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("string", "!=", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("string", "&", newBinaryMethodInfo("string"))


    addDefaultType(this.typeNameToInfo, "bool")
    // bool unary
    this.addUnaryMessageForType("bool", "toStr", newUnaryMethodInfo("string"))
    this.addUnaryMessageForType("bool", "echo", newUnaryMethodInfo("void"))
    // bool binary
    this.addBinaryMessageForType("bool", "==", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "!=", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "||", newBinaryMethodInfo("bool"))
    this.addBinaryMessageForType("bool", "&&", newBinaryMethodInfo("bool"))

  }

  addNewType(typeName: string, typedProperties: TypedProperty[], isUnion: boolean, unionName?: string) {


    const typeInfo = new TypeInfo(typedProperties, isUnion, unionName)
    this.typeNameToInfo.set(typeName, typeInfo)
    // TODO не нужно, внутри конструктора TypeInfo это уже происходит
    // add getters and setters
    // typeInfo.fields.forEach((v, fieldName) => {
    //   this.addUnaryMessageForType(typeName, fieldName, newUnaryMethodInfo(v.type))
    //   this.addKeywordMessageForType(typeName, fieldName, newUnaryMethodInfo("void"))
    // })
  }

  addNewUnionType(union: UnionDeclaration) {
    // register usual type for each branch
    union.branches.forEach(branch => {

      // combine with defaultTypes
      const kindProp: TypedProperty = {
        type: "string",
        identifier: "kind"
      }

      const branchWithDefaultProps: TypedProperty[] = [...branch.propertyTypes, ...union.defaultProperties, kindProp]


      switch (branch.unionKind) {
        case "ManyNames":
          // for each name own type created with same set of fields
          branch.names.forEach(name => {
            // TODO !!! Add not a only type, but a custom constructor for this branch

            this.addNewType(name, branchWithDefaultProps, true, union.name)
          })
          break;
        case "OneNames":
          this.addNewType(branch.name, branchWithDefaultProps, true, union.name)
          break;
      }
    })

    // register union itself with types
    const unionInfo = new UnionInfo(union)
    this.unionNameToInfo.set(union.name, unionInfo)

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

  hasType(typeName: string) {
    return this.typeNameToInfo.has(typeName)
  }
  getType(typeName: string) {
    return this.typeNameToInfo.get(typeName)
  }

  getTypeFields(typeName: string): Map<string, TypeField> {
    const type = this.typeNameToInfo.get(typeName)
    if (!type) {
      throw new Error(`No such type as ${typeName}`)
    }
    return type.fields
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
          console.log(`all known unaryMessages of type: `, typeName," = ", type.unaryMessages)
          throw new Error(`no such unary method: ${methodName} for type ${typeName}`)
        }
        if (unaryMethod.returnType === "auto") {
          throw new Error(`Return type of: ${methodName}, is auto`)
        }
        return unaryMethod.returnType
      case "binary":
        const binaryMethod = type.binaryMessages.get(methodName)
        if (!binaryMethod) {
          // console.log("all known methods of type", typeName, " = ", type.binaryMessages)
          if (typeName !== "auto") {
            throw new Error(`no such binary method: ${methodName}`)
          } else {
            return "auto"
          }
        }
        if (binaryMethod.returnType === "auto") {
          throw new Error(`Return type of: ${methodName}, is auto`)
        }
        return binaryMethod.returnType
      case "keyword":
        const keywordMethod = type.keywordMessages.get(methodName)
        // if (!keywordMethod) {
        //   throw new Error(`no such unary method: ${methodName}, all known methods: ${type.keywordMessages}`)
        // }
        // if (keywordMethod.returnType === "auto") {
        //   throw new Error(`Return type of: ${methodName}, is auto`)
        // }
        return keywordMethod?.returnType ?? "auto"

        case "__global__":
          throw new Error("global cant have return type");
          
      default:
        const _never:never = kind
        throw new Error("Sound error");

    }
  }
}

