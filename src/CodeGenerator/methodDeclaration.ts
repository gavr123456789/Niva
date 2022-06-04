import { StatementList } from "../AST_Nodes/AstNode"
import { KeywordMethodArgument, MethodDeclaration } from "../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration"
import { Statement } from "../AST_Nodes/Statements/Statement"
import { generateNimFromAst } from "./codeGenerator"




export function generateMethodDeclaration(methodDec: MethodDeclaration): string {
	const {bodyStatements, expandableType} = methodDec.method
	const {statements} = bodyStatements
	const {method: x} = methodDec

	const returnType = x.returnType? x.returnType: "auto"
	const methodBody = generateMethodBody(statements, 2)
	

	switch (x.methodKind) {
		case "UnaryMethodDeclaration":
		return `proc ${x.name}(self: ${expandableType}): ${returnType} =\n${methodBody}`

		case "BinaryMethodDeclaration":
			const argumentType = x.identifier.type ?? "auto"
		return `proc \`${x.binarySelector}\`(self: ${expandableType}, ${x.identifier.value}: ${argumentType}): ${returnType} =\n${methodBody}`

		case "KeywordMethodDeclaration":
		const keyArgs = x.keyValueNames.map(y => generateKeywordMethodArg(y)).join(", ")
		// from_to
		const keywordProcName = x.keyValueNames.map(y => y.keyName).join("_")
		return `proc ${keywordProcName}(self: ${expandableType}, ${keyArgs}): ${returnType} =\n${methodBody}`

		
		default:
			const _never: never = x
			throw new Error("SoundError");
	}
}


// level - degree of nesting of the code
function generateMethodBody(statements: Statement[], level: number): string {
	const statementList: StatementList = {
		kind: "StatementList",
		statements
	} 
	
  return generateNimFromAst(statementList, level)
}



function generateKeywordMethodArg(x: KeywordMethodArgument): string {
	if (x.identifier.type) {
		return `${x.identifier.value}: ${x.identifier.type}`
	} else {
		return `${x.identifier.value}: auto`
	}
}