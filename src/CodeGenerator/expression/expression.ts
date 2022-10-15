import { StatementList } from '../../AST_Nodes/AstNode';
import { BracketExpression, Constructor, MessageCallExpression } from '../../AST_Nodes/Statements/Expressions/Expressions';
import { generateNimFromAst } from '../codeGenerator';
import {  generateBinaryCall, generateKeywordCall, generateUnaryCall } from './messageCalls';
import { getAtomPrimary } from '../primary';
import {MessageCall} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {checkForGetter} from "./callLikeExpression";
import {Receiver} from "../../AST_Nodes/Statements/Expressions/Receiver/Receiver";
import {Primary} from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";
import {
	CollectionLiteral,
	ListLiteral,
	MapLiteral, SetLiteral
} from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/CollectionLiteral";
import {processCollection} from "../collections";

export type MessageSendExpression = MessageCallExpression | BracketExpression

export function generateMessageCalls(messageCalls:  MessageCall[], indentation: number, receiver: Receiver): string[]  {
	const messageLine: string[] = []
	for (const [i, messageCall] of messageCalls.entries()) {
		switch (messageCall.selectorKind) {
			case 'unary':
				const isThisGetter = checkForGetter(receiver, messageCall, messageCalls[i - 1])

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
				const keywordMessageCall = generateKeywordCall(messageCall.name, keywordArguments, messageCall.name, indentation);
				messageLine.push(keywordMessageCall);
				break;
			default:
				const _never: never = messageCall;

				throw new Error('SoundError');
		}
	}
	return messageLine
}

export function getPrimaryCode(receiver: Primary | BracketExpression | CollectionLiteral, recursiveIndent: number): string {
	switch (receiver.kindStatement) {
		case "Primary":
			return getAtomPrimary(receiver)
		case "BracketExpression":
			// the expression already has indent in its str, so we dont need to add more
			// maybe this need to be done only in expression.ts
			return processExpression(receiver, recursiveIndent > 0 ? 0: recursiveIndent)
		case "ListLiteral":
		case "MapLiteral":
		case "SetLiteral":
			return processCollection(receiver)
		default:
			const _n: never = receiver
			console.error("receiver = ", receiver)
			throw new Error("Sound error")
	}


}

export function processExpression(s: MessageSendExpression, indentation: number): string {
	// ident stacks if its already indented
	// use only for recursive processExpression calls

	const receiver = s.receiver;

	if (receiver.kindStatement === "BlockConstructor") {
		const statementList: StatementList = {
			kind: "StatementList",
			statements: receiver.statements
		}
		if (receiver.blockArguments.length === 1) {
			// TODO  add it
		}
		const statementsCode = generateNimFromAst(statementList, indentation)
		return statementsCode
	}

	const primaryName = getPrimaryCode(receiver, indentation);
	if (typeof primaryName === 'object') {
		throw new Error('typeof primaryName === object');
	}

	const messageLine = [primaryName, ...generateMessageCalls(s.messageCalls, indentation, receiver)];
	// console.log("messageLine = ", messageLine)
	// generate ident
	const identStr = ' '.repeat(indentation);
	// пушим строку вызовов сообщений
	const needBrackets = s.kindStatement === 'BracketExpression';

	const messageCall = !needBrackets
		? messageLine.join('')
		: '(' + messageLine.join('') + ')';

	return `${identStr}${messageCall}`


}
