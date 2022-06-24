import { StatementList } from '../../AST_Nodes/AstNode';
import { BracketExpression, Constructor, MessageCallExpression } from '../../AST_Nodes/Statements/Expressions/Expressions';
import { generateNimFromAst } from '../codeGenerator';
import { fillKeywordArgsAndReturnStatements, generateBinaryCall, generateKeywordCall, generateUnaryCall } from './messageCalls';
import { getAtomPrimary } from '../primary';
import {generateConstructor} from "./constructor";
import {Primary} from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";
import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {checkForGetter} from "./callLikeExpression";
import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";

export type MessageSendExpression = MessageCallExpression | BracketExpression

export function processManyExpressions(s: MessageSendExpression[], identation: number): string[] {
	return s.map(x => processExpression(x, identation))
}

export function generateMessageCalls(messageCalls:  MessageCall[], indentation: number, receiver: Receiver): string[]  {
	const messageLine: string[] = []
	for (const messageCall of messageCalls) {
		switch (messageCall.selectorKind) {
			case 'unary':
				const isThisGetter = checkForGetter(receiver, messageCall)

				const unaryNimCall = generateUnaryCall(messageCall.name, isThisGetter);
				messageLine.push(unaryNimCall);
				break;

			case 'binary':
				const {name: messageIdent, argument: binaryArguments} = messageCall;
				const binaryMessageCall = generateBinaryCall(messageIdent, binaryArguments);
				messageLine.push(binaryMessageCall);
				break;

			case 'keyword':
				const {arguments: keywordArguments} = messageCall;
				const keywordMessageCall = generateKeywordCall(messageCall.name, keywordArguments, indentation);
				messageLine.push(keywordMessageCall);
				break;
			default:
				const _never: never = messageCall;

				throw new Error('SoundError');
		}
	}
	return messageLine
}

export function processExpression(s: MessageSendExpression, indentation: number): string {
	// ident stacks if its already idented
	const recurciveIdent = indentation >= 2 ? indentation - 2 : indentation
 
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
	if (typeof primaryName === 'object') {
		throw new Error('typeof primaryName === object');
	}

	const messageLine = [primaryName, ...generateMessageCalls(s.messageCalls, indentation, receiver)];

	// generate ident
	const identStr = ' '.repeat(indentation);
	// пушим строку вызовов сообщений
	const needBrackets = s.kindStatement === 'BracketExpression';

	const messageCall = !needBrackets ? messageLine.join('') : '(' + messageLine.join('') + ')';

	return `${identStr}${messageCall}`


}
