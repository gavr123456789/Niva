import {TypeDeclaration, TypedProperty, UnionDeclaration} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration"

function processTypedProperties(typedProperties:  TypedProperty[], indentation: number): string {
	const indent = " ".repeat(indentation)

	return typedProperties.map(x => indent + x.identifier + ": " + x.type).join("\n")
}

export function generateTypeDeclaration(typeDeclarationAst: TypeDeclaration, identation: number): string {
	const ident = " ".repeat(identation) 

	const {typeName, typedProperties, ref} = typeDeclarationAst
	const objectType = ref? "ref": "object"
	const typedArgPart = processTypedProperties(typedProperties, identation + 2)

	

	const result = `${ident}type ${typeName} = ${objectType}\n${typedArgPart}\n`
	return  result
}


export function generateUnionDeclaration(s: UnionDeclaration, indentation: number) {
	const indent = " ".repeat(indentation)
	const indentOneMore = " ".repeat(indentation + 2)
	const indentTwoMore = " ".repeat(indentation + 4)
	const indentThreeMore = " ".repeat(indentation + 6)

	const {name, defaultProperties, ref} = s
	const objectType = ref? "ref object": "object"

	/*
	type
		MalTypeKind* = enum Nil, True, False, Number
		MalType* = ref object
			case kind*: MalTypeKind
			of Nil, True, False: nil
			of Number:           number*:   int
	* */

	// enum part
	const enumListArray: string[] = []
	s.branches.forEach(x => x.unionKind === "ManyNames"? enumListArray.push(...x.names): enumListArray.push(x.name) )
	const enumList = enumListArray.join(", ")

	// branches part
	const branches: string[] = []
	console.log("indentation = ", indentation)
	s.branches.forEach(x => {

		const {propertyTypes} = x
		const hasProperties = propertyTypes.length > 0
		const propertiesStr = processTypedProperties(propertyTypes, indentation + 6)
		const propResult = hasProperties? `${propertiesStr}`: `${indentThreeMore}discard`

		switch (x.unionKind) {
			case "ManyNames":
				const listOfNames = x.names.join(", ")
				branches.push(`${indentTwoMore}of ${listOfNames}:\n${propResult}`)
				break;
			case "OneNames":
				const oneName = x.name
				branches.push(`${indentTwoMore}of ${oneName}:\n${propResult}`)
				break;
			default:
				const _n: never = x
				throw new Error("Sound Error")
		}
	})
	const branchesLines = branches.join("\n")

	// main part
	const kindEnumTypeName = name + "Kind"

	const enumLine = `${indentOneMore}${kindEnumTypeName}* = enum ${enumList}`
	const unionDeclarationLine = `${indentOneMore}${name}* = object`

	const caseKindLine = `${indentTwoMore}case kind*: ${kindEnumTypeName}`


	const typeLine = `${indent}type \n`
	const result = `${typeLine}\n${enumLine}\n${unionDeclarationLine}\n${caseKindLine}\n${branchesLines}`


	return result
}