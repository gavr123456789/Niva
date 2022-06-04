import { BinaryArgument, KeywordArgument } from "../AST_Nodes/Statements/Expressions/Messages/Message";
import { processExpression } from "./expression/expression";

export function generateBinaryCall(binaryMessageName: string, argument: BinaryArgument): string {
	// example: 1 + (1 - 1)
	if (argument.value.kindStatement === "BracketExpression") {
		const lines: string[] = []
		processExpression(argument.value, 0, lines)
		const expressionInBracketsCode = lines.at(0)

		if (!expressionInBracketsCode) throw new Error("expressionInBracketsCode cant be null");
		const codeWithArgumentInBrackets = generateSimpleBinaryCall(binaryMessageName, argument, expressionInBracketsCode)
		return codeWithArgumentInBrackets
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




export function generateKeywordCall(keyWordArgs: KeywordArgument[]): string {
	const functionName = keyWordArgs.map(x => x.ident).join("_")
	const argumentsSeparatedByComma = keyWordArgs.map(x => x.value).join(", ")
	return '.' + functionName + '(' + argumentsSeparatedByComma + ')';
}
