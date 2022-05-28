import { StatementList } from './AST_Nodes/AstNode';
import { IntLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/IntLiteralNode';
import { StringLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/StringLiteralNode';
import { Expression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Mutability, Statement } from './AST_Nodes/Statements/Statement';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';
import { BinaryArgument, KeywordArgument } from './AST_Nodes/Statements/Expressions/Messages/Message';
import { TypeDeclaration } from './AST_Nodes/Statements/TypeDeclaration/TypeDeclaration';
import { KeywordMethodArgument, MethodDeclaration } from './AST_Nodes/Statements/MethodDeclaration/MethodDeclaration';

function generateAssigment(assignmentTarget: string, to: Expression, type?: string): string {
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
	return '.' + unaryMessageName + '()';
}

function generateBinaryCall(binaryMessageName: string, argument: BinaryArgument): string {
	const argValue = argument.value.atomReceiver.value

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

export function generateNimFromAst(x: StatementList, identation = 0, discardable = false): string {
	let lines: string[] = [];

	if (discardable) {
		lines.push('{. push discardable .}');
	}

	for (const s of x.statements) {
		switch (s.kindStatement) {
			case 'Expression':
				// превратить x echo в x.echo
				// сначала добавит вызывающего x
				// потом будем добавлять по вызову за цикл
				// например для унарных будет добавлятся .sas().sus().sos()
				// для бинарных .add(1).mul(3)
				// для кейвордных .fromto(1, 2)
				const primaryName = getAtomPrimary(s.receiver);
				const messageLine = [ primaryName ];
				if (typeof primaryName === 'object') {
					throw new Error('typeof primaryName === object');
				}

				for (const messageCall of s.messageCalls) {
					switch (messageCall.selectorKind) {
						case 'unary':
							switch (s.receiver.kindReceiver) {
								case 'Primary':
									const unaryNimCall = generateUnaryCall(messageCall.unarySelector);
									messageLine.push(unaryNimCall);
									break;

								default:
									throw new Error('expression as receiver not supported yet');
							}
							break;


						case 'binary':
							switch (s.receiver.kindReceiver) {
								case 'Primary':
									const { binarySelector: messageIdent, argument: binaryArguments } = messageCall;
									const binaryMessageCall = generateBinaryCall(messageIdent, binaryArguments);
									messageLine.push(binaryMessageCall);
									break;

								default:
									throw new Error('expression as receiver not supported yet');
							}
							break;


						case 'keyword':
								switch (s.receiver.kindReceiver) {
								case 'Primary':
									const { arguments: keywordArguments } = messageCall;
									const keywordMessageCall = generateKeywordCall(keywordArguments);
									messageLine.push(keywordMessageCall);
									break;

								default:
									throw new Error('expression as receiver not supported yet');
							}
							break;
						default:
							const _never: never = messageCall;
							console.log("NEVER = ", _never);
							
							throw new Error('SoundError');
					}
				}
				// generate ident
				const identStr = " ".repeat(identation) 
				// пушим строку вызовов сообщений
				if (identation === 0) {
					lines.push(messageLine.join(''));
				} else {
					lines.push(identStr + messageLine.join(''));
				}


				
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
	const {method: x} = methodDec

	const returnType = x.returnType? x.returnType: "auto"
	const methodBody = generateMethodBody(bodyStatements, 1)
	console.log("methodBody = ", methodBody);
	

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
	console.log("generateMethodBody", statements);
	
  return generateNimFromAst(statementList, level * 2)
}



function generateKeywordMethodArg(x: KeywordMethodArgument): string {
	if (x.identifier.type) {
		return `${x.identifier.value}: ${x.identifier.type}`
	} else {
		return `${x.identifier.value}: auto`

	}
}