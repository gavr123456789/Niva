import { TypeDeclaration } from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration"

export function generateTypeDeclaration(typeDeclarationAst: TypeDeclaration): string {
	const {typeName, typedProperties, ref} = typeDeclarationAst
	const objectType = ref? "ref": "object"
	const typedArgPart = typedProperties.map(x => "  " + x.identifier + ": " + x.type)

	const result = `type ${typeName} = ${objectType}\n${typedArgPart.join("\n")}\n`

	return result
}
