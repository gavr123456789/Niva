import { StatementList } from '../../AST_Nodes/AstNode';
import { BracketExpression, Constructor, MessageCallExpression } from '../../AST_Nodes/Statements/Expressions/Expressions';
import { generateNimFromAst } from '../codeGenerator';
import { fillKeywordArgsAndReturnStatements, generateBinaryCall, generateKeywordCall, generateUnaryCall } from './messageCalls';
import { getAtomPrimary } from '../primary';
import {generateConstructor} from "./constructor";

export type MessageSendExpression = MessageCallExpression | BracketExpression | Constructor

export function processManyExpressions(s: MessageSendExpression[], identation: number): string[] {
	return s.map(x => processExpression(x, identation))
}

export function processExpression(s: MessageSendExpression, indentation: number): string {
	// ident stacks if its already idented
	const recurciveIdent = indentation >= 2 ? indentation - 2 : indentation
 
	if (s.kindStatement === "Constructor"){

    const constructorCode = generateConstructor(s)
		console.log("constructor code ", constructorCode);
		
		return constructorCode
	}

	const receiver = s.receiver;

	if (receiver.kindStatement === "BlockConstructor") {
		const statemetList: StatementList = {
			kind: "StatementList",
			statements: receiver.statements
		}
		if (receiver.blockArguments.length === 1) {
			// TODO  add it
		}
		const statementsCode = generateNimFromAst(statemetList, indentation)
		return statementsCode
	}

	const primaryName =
		receiver.kindStatement === 'BracketExpression'
			? processExpression(receiver, recurciveIdent)
			: getAtomPrimary(receiver);
	const messageLine = [primaryName];
	if (typeof primaryName === 'object') {
		throw new Error('typeof primaryName === object');
	}


	for (const messageCall of s.messageCalls) {
		switch (messageCall.selectorKind) {
			case 'unary':
				switch (receiver.kindStatement) {
					case 'Primary':
					case 'BracketExpression':
						const unaryNimCall = generateUnaryCall(messageCall.name);
						messageLine.push(unaryNimCall);
						break;

					default:
						const _never: never = receiver;
						throw new Error('Sound error');
				}
				break;

			case 'binary':
				switch (receiver.kindStatement) {
					case 'Primary':
					case 'BracketExpression':
						const { name: messageIdent, argument: binaryArguments } = messageCall;
						const binaryMessageCall = generateBinaryCall(messageIdent, binaryArguments);
						messageLine.push(binaryMessageCall);
						break;

					default:
						const _never: never = receiver;
						throw new Error('Sound error');
				}
				break;

			case 'keyword':
				switch (receiver.kindStatement) {
					case 'Primary':
					case 'BracketExpression':
						const { arguments: keywordArguments } = messageCall;
						const keywordMessageCall = generateKeywordCall(s.selfTypeName, messageCall.name, keywordArguments, receiver, indentation);
						messageLine.push(keywordMessageCall);
						break;
					default:
						const _never: never = receiver;
						throw new Error('Sound error');
				}
				break;
			default:
				const _never: never = messageCall;

				throw new Error('SoundError');
		}
	}
	// generate ident
	const identStr = ' '.repeat(indentation);
	// пушим строку вызовов сообщений
	const needBrackets = s.kindStatement === 'BracketExpression';

	const messageCall = !needBrackets ? messageLine.join('') : '(' + messageLine.join('') + ')';

	return `${identStr}${messageCall}`


}
