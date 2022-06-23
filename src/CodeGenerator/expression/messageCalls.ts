import {StatementList} from "../../AST_Nodes/AstNode";
import {BracketExpression} from "../../AST_Nodes/Statements/Expressions/Expressions";
import {BinaryArgument, KeywordArgument} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {Primary} from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";
import {generateNimFromAst} from "../codeGenerator";
import {processExpression} from "./expression";

export function generateUnaryCall( unaryMessageName: string): string {

	return '.' + '`' + unaryMessageName + '`' + '()';
}



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
	const listOfUnaryCalls = argument.unaryMessages.map(x => generateUnaryCall(x.name)).join("")
	const unaryCallsWithReceiver = argValue + listOfUnaryCalls
	return '.' + '`' + binaryMessageName + '`' + '(' + unaryCallsWithReceiver + ')'
}



export function generateKeywordCall(
	currentMethodName: string,
	keyWordArgs: KeywordArgument[],
	identation: number): string {


	const functionName = keyWordArgs.map(x => x.keyName).join("_")
	const argsValuesCode: string[] = []


	// generateArgs должна возвращать массив аргументов с вызовами у них унарных и бинарных функций
	const lastKeyWordArgBody = fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, identation);

	const argumentsSeparatedByComma = argsValuesCode.join(", ")

	if (!lastKeyWordArgBody) {
		const keywordMessageCall = '.' + functionName + '(' + argumentsSeparatedByComma + ')'
		return keywordMessageCall
	} else {
		// .sas:
		//   blockOfCode
		if (argsValuesCode.length === 1) {
			const keywordMessageCall = `.${functionName}(${argsValuesCode[0]}):\n${lastKeyWordArgBody}`
			return keywordMessageCall
		}
		// .sas(1, 2):
		//   blockOfCode
		if (argsValuesCode.length > 1) {
			const allArgsExceptLast = argsValuesCode.slice(0, -1).join(", ")
			const keywordMessageCall = `.${functionName}(${allArgsExceptLast}):\n${lastKeyWordArgBody}`

			return keywordMessageCall
		}

		throw new Error(`Args count must be 1 or more, args: ${argsValuesCode}`);
	}

}

export function fillKeywordArgsAndReturnStatements(keyWordArgs: KeywordArgument[], args: string[], indentation: number): string | undefined {
	const keyWordArgsCount = keyWordArgs.length
	let lastKeyWordArgBody: undefined | string = undefined
	keyWordArgs.forEach((kwArg, i) => {
		switch (kwArg.receiver.kindStatement) {
			// Arg is just a simple thing(identifier or literal)
			case "Primary":
				const receiverVal = kwArg.receiver.atomReceiver.value + generateKeywordArgCalls(kwArg);

				args.push(receiverVal);
				break;

			// Arg is CodeBlock
			// do: [...]
			case "BlockConstructor":
				if (i !== keyWordArgsCount - 1) {
					throw new Error(`BlockConstructor as not last argument(arg# ${i + 1}, and the last is ${keyWordArgsCount})`);
				}

				const statementList: StatementList = {
					kind: "StatementList",
					statements: kwArg.receiver.statements
				};
				lastKeyWordArgBody = generateNimFromAst(statementList, indentation + 2);
				break;
			//key: (...)
			case "BracketExpression":
				lastKeyWordArgBody = processExpression(kwArg.receiver, indentation)
				break;

			default:
				const _never: never = kwArg.receiver;
				throw new Error("Sound error!");
		}

	});
	return lastKeyWordArgBody;
}

function generateKeywordArgCalls(kwArg: KeywordArgument) {
	let result = ""
	// if there any unary calls, call them first
	for (const unaryMsg of kwArg.unaryMessages) {
		const unaryCallCode = generateUnaryCall(unaryMsg.name);
		result = result + unaryCallCode;
	}
	// if there any binary calls, call them after
	for (const binaryMsg of kwArg.binaryMessages) {
		const binaryCallCode = generateBinaryCall(binaryMsg.name, binaryMsg.argument);
		result = result + binaryCallCode;
	}
	return result;
}
