import { TypeDeclaration } from "../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration"

export function generateTypeDeclaration(typeDeclarationAst: TypeDeclaration, identation: number): string {
	const {typeName, typedProperties, ref} = typeDeclarationAst
	const objectType = ref? "ref": "object"
	const typedArgPart = typedProperties.map(x => "  " + x.identifier + ": " + x.type)
	const ident = " ".repeat(identation) 

	const result = `${ident}type ${typeName} = ${objectType}\n${typedArgPart.join("\n")}\n`
	return  result
}
