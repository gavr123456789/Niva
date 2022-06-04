import { StatementList } from './AST_Nodes/AstNode';
import { BracketExpression, MessageCallExpression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Mutability, Statement } from './AST_Nodes/Statements/Statement';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';
import { BinaryArgument, KeywordArgument } from './AST_Nodes/Statements/Expressions/Messages/Message';
import { TypeDeclaration } from './AST_Nodes/Statements/TypeDeclaration/TypeDeclaration';
import { KeywordMethodArgument, MethodDeclaration } from './AST_Nodes/Statements/MethodDeclaration/MethodDeclaration';

function generateAssigment(assignmentTarget: string, to: MessageCallExpression, type?: string): string {
	if (to.receiver.kindStatement === "BracketExpression") {
		throw new Error("BracketExpression not supported as left part of assigment");
	}
	if (to.messageCalls.length === 0) {
		const assignRightValue = to.receiver.atomReceiver.value;
		if (type) {
			return 'let ' + assignmentTarget + ': ' + type + ' = ' + assignRightValue;
		} else {
			return 'let ' + assignmentTarget + ' = ' + assignRightValue;
		}
	} else {
		// тут преобразовать expression, который по сути message call в ним код, и добавить
		throw new Error('x = exporessiong not supported yet');
	}
}

// function call generators
function generateUnaryCall(unaryMessageName: string): string {
	// TODO check all strange symbols?
	if (unaryMessageName !== "$"){
		return '.' + unaryMessageName + '()';
	} else {
		return '.' + '`' + unaryMessageName + '`' + '()';
	}
}


function generateBinaryCall(binaryMessageName: string, argument: BinaryArgument): string {
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
// Without nested expressions in brackets
function generateSimpleBinaryCall(binaryMessageName: string, argument: BinaryArgument, argValue: string) {

	if (!argument.unaryMessages) {
		return '.' + '`' + binaryMessageName + '`' + '(' + argValue + ')';
	}
	const listOfUnaryCalls = argument.unaryMessages.map(x => generateUnaryCall(x.unarySelector)).join("")
	const unaryCallsWithReceiver = argValue + listOfUnaryCalls
	return '.' + '`' + binaryMessageName + '`' + '(' + unaryCallsWithReceiver + ')'
}

function generateKeywordCall(keyWordArgs: KeywordArgument[]): string {
	const functionName = keyWordArgs.map(x => x.ident).join("_")
	const argumentsSeparatedByComma = keyWordArgs.map(x => x.value).join(", ")
	return '.' + functionName + '(' + argumentsSeparatedByComma + ')';
}

// То что может быть на первом уровне вложенности
export function generateNimFromAst(x: StatementList, identation = 0, discardable = false): string {
	let lines: string[] = [];

	if (discardable) {
		lines.push('{. push discardable .}');
	}

	for (const s of x.statements) {
		switch (s.kindStatement) {
			case 'MessageCallExpression':
			case "BracketExpression":
				processExpression(s, identation, lines)
			break;

			case 'Assignment':
				// codeGenerateExpression(s.value, lines)
				const assignment = s;
				if (assignment.mutability === Mutability.IMUTABLE) {
					lines.push(generateAssigment(assignment.assignmentTarget, assignment.to, s.type));
				}
				break;

			case 'ReturnStatement':
				throw new Error('ReturnStatement not done');
			case 'TypeDeclaration':
				const typeDeclarationAst = s
				const typeDeclarationCode: string = generateTypeDeclaration(typeDeclarationAst)
				lines.push(typeDeclarationCode)
				break;
			case 'MethodDeclaration':
				const methodDeclarationAst = s
				const methodDeclarationCode = generateMethodDeclaration(methodDeclarationAst);
				lines.push(methodDeclarationCode)
			break;

			case 'SwitchExpression':
				/*
				if x > 4: 
					"sas".echo()
				elif x < 4:
					"sus".echo()
				*/
				const switchExp = s
				const branchesCode: string[] = []

				// case -> thenDo branches


				// first must be if, others must be elif
				const firstBranch = switchExp.branches.at(0)
				if (firstBranch) {
					if (firstBranch.caseExpression.kindStatement === "SwitchExpression"){
						throw new Error("nested SwitchExpression doesnt support yet");
					}
					if (firstBranch.thenDoExpression.kindStatement === "SwitchExpression"){
						throw new Error("nested SwitchExpression doesnt support yet");
					}

					const caseExpressionLines: string[] = []
					const thenDoExpressionLines: string[] = []
					
					processExpression(firstBranch.caseExpression, 0, caseExpressionLines)
					processExpression(firstBranch.thenDoExpression, 2, thenDoExpressionLines)

					const caseExpressionCode = caseExpressionLines.at(0) ?? ""
					const thenDoExpressionCode = thenDoExpressionLines.at(0) ?? ""
					const switchExpLine = `if ${caseExpressionCode}:\n${thenDoExpressionCode}`
					
					branchesCode.push(switchExpLine)

					// remove first 
					switchExp.branches = switchExp.branches.slice(1)
				}

				// process others elif branches
				switchExp.branches.forEach(x => {
					if (x.caseExpression.kindStatement === "SwitchExpression"){
						//| | x > 5. => "sas" echo
						throw new Error("case expression cant be another switch expression");
					}
					if (x.thenDoExpression.kindStatement === "SwitchExpression"){
						throw new Error("nested SwitchExpression doesnt support yet");
					}

					const caseExpressionLines: string[] = []
					const thenDoExpressionLines: string[] = []
					
					processExpression(x.caseExpression, 0, caseExpressionLines)
					processExpression(x.thenDoExpression, 2, thenDoExpressionLines)

					const caseExpressionCode = caseExpressionLines.at(0) ?? ""
					const thenDoExpressionCode = thenDoExpressionLines.at(0) ?? ""
					const elIfSwitchExpLine = `elif ${caseExpressionCode}:\n${thenDoExpressionCode}`
					
					branchesCode.push(elIfSwitchExpLine)
					// if caseExpressionCode: thenDoExpressionCode(ident 1)
				})
				// else branch
				if (switchExp.elseBranch) {
					if (switchExp.elseBranch.thenDoExpression.kindStatement === "SwitchExpression") {
						throw new Error("nested SwitchExpression doesnt support yet");
					}
					const elseExpressionLines: string[] = []

					processExpression(switchExp.elseBranch.thenDoExpression, 2, elseExpressionLines)
					const elseExpressionCode = elseExpressionLines.at(0) ?? ""

					branchesCode.push(`else:\n${elseExpressionCode}`)
				}
				const switchExpResult = branchesCode.join("\n")
				lines.push(switchExpResult)
				break;
			default:
				const _never: never = s;
				console.log("!!! s = ", s);
				
				throw new Error('SoundError');
		}
	}

	return lines.join('\n');
}

// If primary is not an expression
function getAtomPrimary(primary: Primary): string {
	const atom = primary.atomReceiver;
	switch (atom.kindPrimary) {
		case 'Identifer':
			return atom.value;
		case 'IntLiteral':
			return atom.value;
		case 'StringLiteral':
			return atom.value;
		default:
			const _never: never = atom;
			console.log("!!! atom = ", atom);
			
			throw new Error('SoundError');
	}
}

function generateTypeDeclaration(typeDeclarationAst: TypeDeclaration): string {
	const {typeName, typedProperties, ref} = typeDeclarationAst
	const objectType = ref? "ref": "object"
	const typedArgPart = typedProperties.map(x => "  " + x.identifier + ": " + x.type)

	// const result = "type " + typeName + " = " + objectType + "\n" + typedArgPart.join("\n") + "\n"
	const result2 = `type ${typeName} = ${objectType}\n${typedArgPart.join("\n")}\n`

	return result2
}


function generateMethodDeclaration(methodDec: MethodDeclaration): string {
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

	// int from: x-int to: y-int -> int = [ x echo. 5 ]
	// proc from_to(self: int, x: int, y: int): int =
  //   x.echo()

	// int from: x-int to: y-int = [ x echo. 5 ]
	// proc from_to(self: int, x: int, y: int): auto =
  //   x.echo()
	//   5

	// то какой тип расширяем это первый аргумент self

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

function processExpression(s: MessageCallExpression | BracketExpression, identation: number, lines: string[]) {
	// превратить x echo в x.echo
	// сначала добавит вызывающего x
	// потом будем добавлять по вызову за цикл
	// например для унарных будет добавлятся .sas().sus().sos()
	// для бинарных .add(1).mul(3)
	// для кейвордных .fromto(1, 2)
	
	const receiver = s.receiver
	
	const primaryName = receiver.kindStatement === "BracketExpression"? getExpressionCode(receiver, identation) :  getAtomPrimary(receiver);
	const messageLine = [ primaryName ];
	if (typeof primaryName === 'object') {
		throw new Error('typeof primaryName === object');
	}
	
	for (const messageCall of s.messageCalls) {
		switch (messageCall.selectorKind) {
			case 'unary':
				switch (receiver.kindStatement) {
					case 'Primary':
					case "BracketExpression":
						const unaryNimCall = generateUnaryCall(messageCall.unarySelector);
						messageLine.push(unaryNimCall);
						break;

					default:
						const _never: never = receiver
						throw new Error('Sound error');
				}
				break;


			case 'binary':
				switch (receiver.kindStatement) {
					case 'Primary':
					case "BracketExpression":
						const { binarySelector: messageIdent, argument: binaryArguments } = messageCall;
						const binaryMessageCall = generateBinaryCall(messageIdent, binaryArguments);
						messageLine.push(binaryMessageCall);
						break;

						default:
							const _never: never = receiver
							throw new Error('Sound error');
				}
				break;


			case 'keyword':
					switch (receiver.kindStatement) {
					case 'Primary':
					case "BracketExpression":

						const { arguments: keywordArguments } = messageCall;
						const keywordMessageCall = generateKeywordCall(keywordArguments);
						messageLine.push(keywordMessageCall);
						break;
						default:
							const _never: never = receiver
							throw new Error('Sound error');
				}
				break;
			default:
				const _never: never = messageCall;
				console.log("!!! = ", _never);
				
				throw new Error('SoundError');
		}
	}
	// generate ident
	const identStr = " ".repeat(identation) 
	// пушим строку вызовов сообщений
	const needBrackets = s.kindStatement === "BracketExpression"
	const messageCall = !needBrackets? messageLine.join(''): "(" + messageLine.join('') + ")"
	
	if (identation === 0) {
		lines.push(messageCall);
	} else {
		lines.push(identStr + messageCall);
	}


}
function getExpressionCode(receiver: BracketExpression, identation: number): string {
	const lines: string[] = []
	processExpression(receiver, identation, lines)
	return lines.join("")
}

