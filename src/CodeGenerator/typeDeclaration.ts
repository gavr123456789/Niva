import {
	getTypedPropertiesNames,
	TypeDeclaration,
	TypedProperty,
	UnionDeclaration
} from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration"

function processTypedProperties(typedProperties:  TypedProperty[], indentation: number): string {
	const indent = " ".repeat(indentation)

	return typedProperties.map(x => indent + x.identifier + ": " + x.type).join("\n")
}

export function generateTypeDeclaration(typeDeclarationAst: TypeDeclaration, identation: number): string {
	const ident = " ".repeat(identation) 

	const {name, typedProperties, ref} = typeDeclarationAst
	const objectType = ref? "ref": "object"
	const typedArgPart = processTypedProperties(typedProperties, identation + 2)

	

	const result = `${ident}type ${name} = ${objectType}\n${typedArgPart}\n`
	return  result
}


function generateBranchConstructors(u: UnionDeclaration): string[] {
	/*
	proc construct_Rectangle_defaultProp_width_height(defaultProp: int, width: int, height: int): Shape =
    Shape(kind: Rectangle, defaultProp: defaultProp, width: width, height: height)
	* */

	const hasDefaultProps = u.defaultProperties.length > 0
	const result: string[] = []
	for (let branch of u.branches) {
		if (branch.unionKind === "ManyNames"){
			throw new Error("TODO")
			// все шо снизу разбить на функции, и если неймов много запускать эту одну функцию в цикле
		}

		const branchHasProps = branch.propertyTypes.length > 0


		// generate function name
		// defaultProp
		const defaultNames = getTypedPropertiesNames(u.defaultProperties)
		// width_height
		const branchNames = getTypedPropertiesNames(branch.propertyTypes)
		const allPropertiesNames: string[] = [...defaultNames, ...branchNames ]
		// defaultProp_width_height
		const constructorNameArgs = allPropertiesNames.join("_")
		const constructorName = `construct_${branch.name}_${constructorNameArgs}`



		// generate function args
		// defaultProp: int, width: int, height: int
		const constructorArgsArray: string[] = []

		u.defaultProperties.forEach(x => {
			if (!x.type)
				throw new Error(`Type of ${x.identifier} default property of union: ${u.name} not declarated`)
			// defaultProp: int
			constructorArgsArray.push(x.identifier + ": " + x.type)
		})

		branch.propertyTypes.forEach(x => {
			const branchName = branch.unionKind === "OneNames"? branch.name: branch.names.join(", ")
			if (!x.type) throw new Error(`Type of ${x.identifier} of ${branchName} branch of union: ${u.name} not declarated`)

			constructorArgsArray.push(x.identifier + ": " + x.type)
		})

		const constructorArgs = constructorArgsArray.join(", ")


		// generate body
		//     Shape(kind: Rectangle, defaultProp: defaultProp, width: width, height: height)

		let body: string | undefined = undefined

		if (branch.unionKind === "OneNames"){

			// body args kind: Rectangle, defaultProp: defaultProp, width: width, height: height
			const kindArg = `kind: ${branch.name}`
			const bodyArgsArray: string[] = []
			// console.log("constructorArgsArray = ", constructorArgsArray)
			allPropertiesNames.forEach(x => {
				// defaultProp: defaultProp
				bodyArgsArray.push(x + ": " + x)
			})

			const bodyArgs = bodyArgsArray.join(", ")


			body = `  ${u.name}(${kindArg}, ${bodyArgs})`

		} else {
			throw new Error("TODO many constructors for one branch")
		}


		const procDeclaration = `\nproc ${constructorName}*(${constructorArgs}): ${u.name} = \n${body}`


		result.push(procDeclaration)
	}


	return result


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
	s.branches.forEach(x => {

		const {propertyTypes} = x
		const hasProperties = propertyTypes.length > 0
		const propertiesStr = processTypedProperties(propertyTypes, indentation + 6)
		const propResult = hasProperties? `${propertiesStr}`: `${indentThreeMore}discard`

		// generate branches
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

	// generate default fields
	const defaultFields: string[] = []
	defaultProperties.forEach(x => {
		if (!x.type){
			throw new Error(`Default type of union: ${s.name} of field: ${x.identifier} not declarated`)
		}
		defaultFields.push(indentTwoMore + x.identifier + ": " + x.type)
	})

	const defaultFieldsLines = defaultFields.join("\n")

	// main part
	const kindEnumTypeName = name + "Kind"

	const enumLine = `${indentOneMore}${kindEnumTypeName}* = enum ${enumList}`
	const unionDeclarationLine = `${indentOneMore}${name}* = object`

	const caseKindLine = `${indentTwoMore}case kind*: ${kindEnumTypeName}`


	const typeLine = `${indent}type \n`
	const result = `${typeLine}\n${enumLine}\n${unionDeclarationLine}\n\n${defaultFieldsLines}\n\n${caseKindLine}\n${branchesLines}`

	const constructors: string[] = generateBranchConstructors(s)



	return result + "\n" + constructors.join("\n") + "\n\n"
}
