import { StatementList } from './AST_Nodes/AstNode';
import { IntLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/IntLiteralNode';
import { StringLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/StringLiteralNode';
import { Expression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Mutability } from './AST_Nodes/Statements/Statement';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';

function generateAssigment(assignmentTarget: string, to: Expression, type?: string): string {
	if (to.messageCalls.length === 0){
		const assignRightValue = to.receiver.atomReceiver.value
		if (type) {
			return 'let ' + assignmentTarget + ': ' + type + ' = ' + assignRightValue;
		} else {
			return 'let ' + assignmentTarget + ' = ' + assignRightValue;
		}
	} else {
		// тут преобразовать expression, который по сути message call в ним код, и добавить 
		throw new Error("x = exporessiong not supported yet");
		
	}

}

// function call generators
function generateUnaryCall(primaryName: string, unaryMessageName: string) {
	if (typeof primaryName === "object") {
		throw new Error("typeof primaryName === object");
		
	}
	return primaryName + '.' + unaryMessageName + '()';
}
function generateBinaryCall(primary: string, binaryMessageName: string, argument: string) {
	throw new Error("Not implemented");
	
	return primary + '.' + binaryMessageName + '()';
}
function generateKeywordCall(
	primary: string,
	keywordMessageName: string,
	keyWordArgs: { keyword: string; arg: string }[]
) {
	throw new Error("Not implemented");
	
	return primary + '.' + keywordMessageName + '()';
}

export function generateNimFromAst(x: StatementList): string {
	let lines: string[] = [];
	for (const s of x.statements) {
		switch (s.kindStatement) {
			case 'Expression':
				// превратить x echo в x.echo
				for (const messageCall of s.messageCalls) {
					switch (messageCall.selectorKind) {
						case 'unary':
							if (s.receiver.kindReceiver === 'Primary') {
								const primaryValue = getAtomPrimary(s.receiver);
								const unaryNimCall = generateUnaryCall(primaryValue, messageCall.messageIdent);
								lines.push(unaryNimCall);
							} else {
								console.log("s = ", s);
								
								throw new Error('expression as receiver not supported yet');
							}

							break;
						case 'binary':
							// const binaryNimCall = generateBinaryCall(s.primary.toString(), s.message.messageIdent)
							// lines.push(binaryNimCall);
							throw new Error('keyword calls not implemented');

							break;
						case 'keyword':
							throw new Error('keyword calls not implemented');

						default:
							const _never: never = messageCall;
							throw new Error('SoundError');
					}
				}

				break;

			case 'Assignment':
				// codeGenerateExpression(s.value, lines)
				const assignment = s
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
