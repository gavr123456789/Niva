import { StatementList } from "../AST_Nodes/AstNode";
import { BinaryArgument, KeywordArgument } from "../AST_Nodes/Statements/Expressions/Messages/Message";
import { generateNimFromAst } from "./codeGenerator";
import { processExpression } from "./expression/expression";

export function generateBinaryCall(binaryMessageName: string, argument: BinaryArgument): string {
	// example: 1 + (1 - 1)
	if (argument.value.kindStatement === "BracketExpression") {
		const expressionInBracketsCode = processExpression(argument.value, 0)
		const codeWithArgumentInBrackets = generateSimpleBinaryCall(binaryMessageName, argument, expressionInBracketsCode)
		return codeWithArgumentInBrackets
	}
	if (argument.value.kindStatement === "BlockConstructor") {
		throw new Error("BlockConstructor");
	}
	// example 1 + 1
	const argValue = argument.value.atomReceiver.value
	const code = generateSimpleBinaryCall(binaryMessageName, argument, argValue)
	return code 
}



function generateSimpleBinaryCall(binaryMessageName: string, argument: BinaryArgument, argValue: string) {

	if (!argument.unaryMessages) {
		return '.' + '`' + binaryMessageName + '`' + '(' + argValue + ')';
	}
	const listOfUnaryCalls = argument.unaryMessages.map(x => generateUnaryCall(x.unarySelector)).join("")
	const unaryCallsWithReceiver = argValue + listOfUnaryCalls
	return '.' + '`' + binaryMessageName + '`' + '(' + unaryCallsWithReceiver + ')'
}

// function call generators
export function generateUnaryCall(unaryMessageName: string): string {
	// TODO check all strange symbols?
	if (unaryMessageName !== "$"){
		return '.' + unaryMessageName + '()';
	} else {
		return '.' + '`' + unaryMessageName + '`' + '()';
	}
}



export function generateKeywordCall(keyWordArgs: KeywordArgument[], identation: number): string {
	

	const functionName = keyWordArgs.map(x => x.ident).join("_")
	const args: string[] = []
	const keyWordArgsCount = keyWordArgs.length

	let lastKeyWordArgBody: undefined | string = undefined

	
	keyWordArgs.forEach((x, i) => {

		switch (x.receiver.kindStatement) {
			case "Primary":
				const s = x.receiver.atomReceiver.value
				args.push(s) 
				break;


			case "BlockConstructor":
				// TODO тут нужна информация о том это вызов темплейта или обычной функции
				// если темплейта то код должен быть ...:\n expressions.join("\n")
				// если функция то proc(x: int): int = code
				if (i !== keyWordArgsCount - 1) {
					throw new Error(`BlockConstructor as not last argument(arg# ${i+1}, and the last is ${keyWordArgsCount})`);
				}

				const statementList: StatementList = {
					kind: "StatementList",
					statements: x.receiver.statements
				} 

				lastKeyWordArgBody = generateNimFromAst(statementList, identation + 2 )
				
				break
			case "BracketExpression":
				throw new Error("BracketExpression as key args");

		
			default:
				const _never: never = x.receiver
				throw new Error("Sound error!");
				
		}

		
		
	})
	
	const argumentsSeparatedByComma = args.join(", ")

	
	if (!lastKeyWordArgBody){
		const keywordMessageCall = '.' + functionName + '(' + argumentsSeparatedByComma + ')'
		return keywordMessageCall
	} else {
		// .sas:
		//   blockOfCode
		if (args.length === 1){
			const keywordMessageCall = `.${functionName}(${args[0]}):\n${lastKeyWordArgBody}`
			return keywordMessageCall
		}
		// .sas(1, 2):
		//   blockOfCode
		if (args.length > 1) {
			const allArgsExceptLast = args.slice(0, -1).join(", ")
			const keywordMessageCall = `.${functionName}(${allArgsExceptLast}):\n${lastKeyWordArgBody}`
			
			return keywordMessageCall
		}  

		throw new Error(`Args count must be 1 or more, args: ${args}`);
		
		
	}

}
