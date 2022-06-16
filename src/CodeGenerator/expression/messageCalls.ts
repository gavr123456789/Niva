import { StatementList } from "../../AST_Nodes/AstNode";
import { BracketExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { BinaryArgument, KeywordArgument } from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import { Primary } from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";
import { codeDB } from "../../niva";
import { generateNimFromAst } from "../codeGenerator";
import { processExpression } from "./expression";

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
export function generateUnaryCall( unaryMessageName: string): string {
	// Check if this a getter
	// need to know receiver type
	// if receiver type has field with the same name as the 
	
	return '.' + '`' + unaryMessageName + '`' + '()';
}


// Keyword can be just a method call
// or a type constructor: Person name: "Bob" age: 42.
// or a field setter 
export function generateKeywordCall(
	selfType: string,
	currentMethodName: string,
	keyWordArgs: KeywordArgument[],
	receiver: BracketExpression | Primary,
	identation: number): {keywordMessageCall: string, isContructor: boolean} {

	// Check if this a object constructor
	const isThisIsConstructorCall =
		receiver.kindStatement === "Primary" &&
		receiver.atomReceiver.kindPrimary === "Identifer" &&
		codeDB.hasType(receiver.atomReceiver.value)

	// if this call a constructor => set type of receiver
	


	// Check if this a setter
	const ifThisIsSetterCode: string | null = ifFieldSetterGenerateCode(selfType, keyWordArgs)
	if (ifThisIsSetterCode) {
		codeDB.addEffectForMethod(selfType, "keyword", currentMethodName, { kind: "mutatesFields" })
		return {keywordMessageCall: ifThisIsSetterCode, isContructor: false}
	}


	const functionName = keyWordArgs.map(x => x.keyName).join("_")
	const args: string[] = []
	const keyWordArgsCount = keyWordArgs.length

	let lastKeyWordArgBody: undefined | string = undefined

	// generateArgs должна возвращать массив аргументов с вызовами у них унарных и бинарных функций
	lastKeyWordArgBody = generateArgs(keyWordArgs, args, keyWordArgsCount, lastKeyWordArgBody, identation);

	const argumentsSeparatedByComma = args.join(", ")

	if (isThisIsConstructorCall){
		// TODO type check, check that all arguments was setted
		// Person(name: "sas", age: 42)
		const typeName = receiver.atomReceiver.value 
		// create "key: val, key2: val2" pairs
		const argNameColonArgVal = keyWordArgs.map((x, i) => {
		 return	x.keyName + ": " + args[i] 
		}).join(", ")

		const code =  `(${argNameColonArgVal})`
		return {keywordMessageCall: code, isContructor: true}
	}
	if (!lastKeyWordArgBody) {
		const keywordMessageCall = '.' + functionName + '(' + argumentsSeparatedByComma + ')'
		return {keywordMessageCall: keywordMessageCall, isContructor: false}
	} else {
		// .sas:
		//   blockOfCode
		if (args.length === 1) {
			const keywordMessageCall = `.${functionName}(${args[0]}):\n${lastKeyWordArgBody}`
			return {keywordMessageCall: keywordMessageCall, isContructor: false}
		}
		// .sas(1, 2):
		//   blockOfCode
		if (args.length > 1) {
			const allArgsExceptLast = args.slice(0, -1).join(", ")
			const keywordMessageCall = `.${functionName}(${allArgsExceptLast}):\n${lastKeyWordArgBody}`

			return {keywordMessageCall: keywordMessageCall, isContructor: false}
		}

		// console.log("lastKeyWordArgBody = ", lastKeyWordArgBody);

		throw new Error(`Args count must be 1 or more, args: ${args}`);
	}

}


function generateArgs(keyWordArgs: KeywordArgument[], args: string[], keyWordArgsCount: number, lastKeyWordArgBody: string | undefined, identation: number): string | undefined {
	
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
				// TODO тут нужна информация о том это вызов темплейта или обычной функции
				// если темплейта то код должен быть ...:\n expressions.join("\n")
				// если функция то proc(x: int): int = code
				if (i !== keyWordArgsCount - 1) {
					throw new Error(`BlockConstructor as not last argument(arg# ${i + 1}, and the last is ${keyWordArgsCount})`);
				}

				const statementList: StatementList = {
					kind: "StatementList",
					statements: kwArg.receiver.statements
				};

				lastKeyWordArgBody = generateNimFromAst(statementList, identation + 2);

				break;
			//key: (...)
			case "BracketExpression":
				lastKeyWordArgBody = processExpression(kwArg.receiver, identation)

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
		const unaryCallCode = generateUnaryCall(unaryMsg.unarySelector);
		result = result + unaryCallCode;
	}
	// if there any binary calls, call them after
	for (const binaryMsg of kwArg.binaryMessages) {
		const binaryCallCode = generateBinaryCall(binaryMsg.binarySelector, binaryMsg.argument);
		result = result + binaryCallCode;
	}
	return result;
}

function ifFieldSetterGenerateCode(extendedType: string, keyWordArgs: KeywordArgument[]): string | null {
	// code DB
	// Check if this call is setter

	// must have one argument, and that argument has key name same as one of the fields
	const firstArg = keyWordArgs.at(0)
	if (!firstArg || keyWordArgs.length !== 1) {

		return null
	}


	const isField = codeDB.typeHasField(extendedType, firstArg.keyName)

	// TODO: type check that field is of the same type as arg
	if (true) {

	}

	// Check that arg is simple primary
	if (isField && firstArg.receiver.kindStatement === "Primary") {
		const code = generateSetter(firstArg.keyName, firstArg.receiver.atomReceiver.value)
		return code
	} else {
		return null
	}

	// if (firstArg.receiver.kindStatement === "BlockConstructor"){
	// 	throw new Error("BlockConstructor not supported yet as ");

	// }

}

function generateSetter(fieldName: string, value: string): string {

	// input  person name: "sas"
	// output person.name = "sas"
	// we here after dot
	// keywords messages cant be continued with other type of messages, so here no problems like
	return `.${fieldName} = ${value}`

}
