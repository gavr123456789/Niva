import { StatementList } from "../AST_Nodes/AstNode"
import { KeywordMethodArgument, MethodDeclaration } from "../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration"
import { Statement } from "../AST_Nodes/Statements/Statement"
import { codeDB } from "../niva"
import { generateNimFromAst } from "./codeGenerator"


export function generateMethodDeclaration(methodDec: MethodDeclaration, identation: number): string {
	const {bodyStatements, expandableType, kind} = methodDec.method
	const {statements} = bodyStatements
	const {method: x} = methodDec

	const returnType = x.returnType? x.returnType: "auto"
	const methodBody = generateMethodBody(statements, identation + 2)
	const ident = " ".repeat(identation) 

	const procOrTemplate = kind
	

	switch (x.methodKind) {
		case "UnaryMethodDeclaration":
		return `${ident}${procOrTemplate} ${x.name}(self: ${expandableType}): ${returnType} =\n${methodBody}`

		case "BinaryMethodDeclaration":
			const argumentType = x.argument.type ?? "auto"
		return `${ident}${procOrTemplate} \`${x.binarySelector}\`(self: ${expandableType}, ${x.argument.value}: ${argumentType}): ${returnType} =\n${methodBody}`

		case "KeywordMethodDeclaration":
		const keyArgs = x.keyValueNames.map(y => generateKeywordMethodArg(y)).join(", ")
		// from_to
		const keywordProcName = x.keyValueNames.map(y => y.keyName).join("_")
		
		const isSelfMutating: boolean = codeDB.hasMutateEffect(x.expandableType, keywordProcName)
		const args = `self: ${isSelfMutating? "var ": ""}${expandableType}, ${keyArgs}`
		return `${ident}${procOrTemplate} ${keywordProcName}(${args}): ${returnType} =\n${methodBody}`

		
		default:
			const _never: never = x
			throw new Error("SoundError");
	}
}


// level - degree of nesting of the code
function generateMethodBody(statements: Statement[], identation: number): string {
	const statementList: StatementList = {
		kind: "StatementList",
		statements
	} 
	
  return generateNimFromAst(statementList, identation)
}



function generateKeywordMethodArg(x: KeywordMethodArgument): string {
	if (x.identifier.type) {
		// const getter = generateGetter(x.identifier.value, x.identifier.type)
		return `${x.identifier.value}: ${x.identifier.type}`
	} else {
		return `${x.identifier.value}: auto`
	}
}

// function generateGetter(value: string, type: string): string {
	
// }
