import { StatementList } from './AST_Nodes/AstNode';
import { IntLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/IntLiteralNode';
import { StringLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/StringLiteralNode';
import { Expression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Mutability } from './AST_Nodes/Statements/Statement';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';
import { BinaryArgument, KeywordArgument } from './AST_Nodes/Statements/Expressions/Messages/Message';

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

	const unaryCalls = argValue + argument.unaryMessages.map(x => generateUnaryCall(x.unarySelector)).join(".")
	return '.' + '`' + binaryMessageName + '`' + '(' + unaryCalls + ')'
}

function generateKeywordCall(keyWordArgs: KeywordArgument[]): string {
	const functionName = keyWordArgs.map(x => x.ident).join("_")
	const argumentsSeparatedByComma = keyWordArgs.map(x => x.value).join(", ")
	return '.' + functionName + '(' + argumentsSeparatedByComma + ')';
}

export function generateNimFromAst(x: StatementList, discardable = true): string {
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
				// пушим строку вызовов сообщений
				lines.push(messageLine.join(''));

				break;

			case 'Assignment':
				// codeGenerateExpression(s.value, lines)
				const assignment = s;
				if (assignment.mutability === Mutability.IMUTABLE) {
					lines.push(generateAssigment(assignment.assignmentTarget, assignment.to, s.type));
				}
				break;

			case 'MethodDeclarationStatement':
				throw new Error('MethodDeclarationStatement not done');
			case 'ReturnStatement':
				throw new Error('ReturnStatement not done');
			case 'TypeDeclarationStatement':
				throw new Error('TypeDeclarationStatement not done');

			default:
				const _never: never = s;
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
			throw new Error('SoundError');
	}
}

// function codeGenerateExpression(x: Expression, lines: string[]) {
// 	switch (x.kindExpression) {
// 		case 'Assignment':
// 			if (x.mutability === Mutability.IMUTABLE) {
// 				lines.push(generateAssigment(x.assignmentTarget, x.to.value, x.type));
// 			}
// 			break;
// 		case 'Parentheses':
// 			break;

// 		default:
// 			const _never: never = x;
// 			break;
// 	}
// }
