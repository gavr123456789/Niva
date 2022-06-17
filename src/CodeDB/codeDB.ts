import { ContextInformation, TypeField } from "../niva"
import { UnaryMessageInfo, BinaryMethodInfo, KeywordMethodInfo, EffectType } from "./types"

export type MethodKinds = "unary" | "binary" | "keyword"


class TypeInfo {
	// field name to info
	constructor(public fields: Map<string, TypeField>) {}

	structural: boolean = false
	distinct: boolean = false

	unaryMessages: Map<string, UnaryMessageInfo> = new Map()
	binaryMessages: Map<string, BinaryMethodInfo> = new Map()
	keywordMessages: Map<string, KeywordMethodInfo> = new Map()
}

export class CodeDB {

  addTypedValueToMethodScope(insideMethod: ContextInformation, valueName: string, valueType: string) {
    const {forType: typeName, kind, withName: messageName} = insideMethod
    if (typeName === "__global__"){
      console.log("addTypedValueToMethodScope, typeName === __global__");
      return
    }
    
    const type = this.typeNameToInfo.get(typeName)
		if(!type){
      console.error("typeName = ", typeName);
			throw new Error("trying to add method for non existing type");
		}
    
    switch (kind) {
      case "unary":
        const unaryMessage = type.unaryMessages.get(messageName)
        if (!unaryMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.unaryMessages}`);
          return
        }
        const alreadyDefinedUnary = unaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedUnary){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        unaryMessage.declaratedValueToType.set(valueName, valueType)
        // console.log("added val unary current:",  unaryMessage.declaratedValueToType);
        console.log(`added val with name: ${valueName}, type: ${valueType} inside unary method ${messageName} for type ${typeName}`);
        

        break;
      case "binary":
        const binaryMessage = type.binaryMessages.get(messageName)
        if (!binaryMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.binaryMessages}`);
          return
        }
        const alreadyDefinedBinary = binaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedBinary){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        binaryMessage.declaratedValueToType.set(valueName, valueType)
        console.log(`added val with name: ${valueName}, type: ${valueType} inside binary method ${messageName} for type ${typeName}`);

        break;
      case "keyword":
        const keywordMessage = type.keywordMessages.get(messageName)
        if (!keywordMessage) {
          console.log(`addTypedValueToMethodScope, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.keywordMessages}`);
          return
        }
        const alreadyDefinedKeyword = keywordMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedKeyword){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        keywordMessage.declaratedValueToType.set(valueName, valueType)
        console.log(`added val with name: ${valueName}, type: ${valueType} inside keyword method ${messageName} for type ${typeName}`);

        break;
			
			case "__global__":
				throw new Error("TODO");
        break;
    
      default:
				const never:never = kind
        break;
    }
  }

	checkMethodHasValue(insideMethod: ContextInformation, valueName: string): string | undefined{
    const {forType: typeName, kind, withName: messageName} = insideMethod

		const type = this.typeNameToInfo.get(typeName)
		if(!type){
      console.error("typeName = ", typeName);
			throw new Error("trying to add method for non existing type");
		}

		switch (kind) {
      case "unary":
        const unaryMessage = type.unaryMessages.get(messageName)
        if (!unaryMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.unaryMessages}`);
          return
        }
        const alreadyDefinedUnary = unaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedUnary){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        unaryMessage.declaratedValueToType.get(valueName)
        // console.log("added val unary current:",  unaryMessage.declaratedValueToType);
        

        break;
      case "binary":
        const binaryMessage = type.binaryMessages.get(messageName)
        if (!binaryMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.binaryMessages}`);
          return
        }
        const alreadyDefinedBinary = binaryMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedBinary){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        return binaryMessage.declaratedValueToType.get(valueName)
        // console.log(`added val with name: ${valueName}, type: ${valueType} inside binary method ${messageName} for type ${typeName}`);

      case "keyword":
        const keywordMessage = type.keywordMessages.get(messageName)
        if (!keywordMessage) {
          console.log(`checkMethodHasValue, message with name: ${messageName} not found for type, ${typeName}` );
          console.log(`All known messages are: ${type.keywordMessages}`);
          return
        }
        const alreadyDefinedKeyword = keywordMessage.declaratedValueToType.get(valueName)
        if (alreadyDefinedKeyword){
          console.log(`${valueName} value already defined in ${messageName}`);
          return
        }
        return keywordMessage.declaratedValueToType.get(valueName)

			case "__global__":
				throw new Error("TODO");
        break;
    
      default:
				const never:never = kind
        break;
    }


	}

	// TODO methods must have all variables and their types table

	private typeNameToInfo: Map<string, TypeInfo> = new Map()

	private addDefaultType(name: string){
		const intFields = new Map<string, TypeField>()
		intFields.set("value", {type: name})
		this.typeNameToInfo.set(name, new TypeInfo(intFields))
	}

	constructor(){
		// add default types and functions
		this.addDefaultType("int")
		this.addDefaultType("string")
		this.addDefaultType("bool")
		this.addDefaultType("float")
	}

	addNewType(name: string, fields: Map<string, TypeField>){
		const typeInfo = new TypeInfo(fields)
		this.typeNameToInfo.set(name, typeInfo)
	}

	addKeywordMessageForType(typeName: string, selectorName: string, info: KeywordMethodInfo){
		const type = this.typeNameToInfo.get(typeName)
		if(!type){
			throw new Error("trying to add method for non existing type");
		}
		type.keywordMessages.set(selectorName, info)
	}
	addUnaryMessageForType(typeName: string, selectorName: string, info: UnaryMessageInfo){
		const type = this.typeNameToInfo.get(typeName)
		if(!type){
			throw new Error("trying to add method for non existing type");
		}
		type.unaryMessages.set(selectorName, info)
	}
	addBinaryMessageForType(typeName: string, selectorName: string, info: BinaryMethodInfo){
		const type = this.typeNameToInfo.get(typeName)
		if(!type){
			throw new Error("trying to add method for non existing type");
		}
		type.binaryMessages.set(selectorName, info)
	}

	hasMutateEffect(typeName: string, methodSelector: string): boolean {
		const type = this.typeNameToInfo.get(typeName)
		if (!type){
			throw new Error("trying to check effect of non existing type");
		}

		const unary =   !!type.unaryMessages.get(methodSelector)?.effects.has("mutatesFields")
		const binary =  !!type.binaryMessages.get(methodSelector)?.effects.has("mutatesFields")
		const keyword = !!type.keywordMessages.get(methodSelector)?.effects.has("mutatesFields")
		return unary || binary || keyword
	}

	addEffectForMethod(selfType: string, methodKind: MethodKinds, methodName: string, effect: { kind: EffectType; }) {
		const type = this.typeNameToInfo.get(selfType)
		if (!type){
			throw new Error("trying to add effect to non existing type");
		}

		if (methodName.length === 0){
			throw new Error("MethodName is Empty");
			
		}

		switch (methodKind) {
			case "keyword":
			  const keywordMethod = type.keywordMessages.get(methodName)
				if (!keywordMethod){
					console.error(`All known keywordMethods of type ${selfType}:`)
					
					throw new Error(`trying to add effecto for non existing method: ${methodName}` );
				}
				keywordMethod.effects.add(effect.kind)
				break;
			case "binary":
				const binaryMethod = type.binaryMessages.get(methodName)
				if (!binaryMethod){
					throw new Error("trying to add effect for non existing method");
				}
				binaryMethod.effects.add(effect.kind)
				break;
			case "unary":
				const unaryMethod = type.unaryMessages.get(methodName)
				if (!unaryMethod){
					throw new Error("trying to add effecto for non existing method");
				}
				unaryMethod.effects.add(effect.kind)
				break;
		
			default:
				const _never: never = methodKind
				break;
		}

		// TODO
		// throw new Error("Method not implemented.");
	}

	hasType(name: string){
		return this.typeNameToInfo.has(name)
	}

	autoCompleteMessage(type: string, start: string): string{
		const x = this.typeNameToInfo.get(type)
		if (!x) return ""

		if (start === ""){
			const allFieldsAndMethodsName = ""
			return allFieldsAndMethodsName
		} else {
			const allFieldsAndMethodsStartWith = ""
			return allFieldsAndMethodsStartWith

		}

	}

	typeHasField(typeName: string, keyName: string): boolean {
		const type = this.typeNameToInfo.get(typeName)
		if (!type){
			return false
		}
		return 	type.fields.has(keyName)
  }
}

export const codeDB2 = new CodeDB()