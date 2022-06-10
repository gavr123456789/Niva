import { StatementList } from '../../AST_Nodes/AstNode';
import { BracketExpression, MessageCallExpression } from '../../AST_Nodes/Statements/Expressions/Expressions';
import { generateNimFromAst } from '../codeGenerator';
import { generateUnaryCall, generateBinaryCall, generateKeywordCall } from '../messageCalls';
import { getAtomPrimary } from '../primary';

export function processExpression(s: MessageCallExpression | BracketExpression, identation: number): string {
	const receiver = s.receiver;

	if (receiver.kindStatement === "BlockConstructor"){
		const statemetList: StatementList = {
			kind: "StatementList",
			statements: receiver.statements
		}
		// process "it"
		if (receiver.blockArguments.length === 1) {

		}
		const statementsCode = generateNimFromAst(statemetList)
		throw new Error("BlockConstructor");
		
	}

	const primaryName =
		receiver.kindStatement === 'BracketExpression'
			? processExpression(receiver, identation)
			: getAtomPrimary(receiver);
	const messageLine = [ primaryName ];
	if (typeof primaryName === 'object') {
		throw new Error('typeof primaryName === object');
	}


	for (const messageCall of s.messageCalls) {
		switch (messageCall.selectorKind) {
			case 'unary':
				switch (receiver.kindStatement) {
					case 'Primary':
					case 'BracketExpression':
						const unaryNimCall = generateUnaryCall(messageCall.unarySelector);
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
						const { binarySelector: messageIdent, argument: binaryArguments } = messageCall;
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
						const keywordMessageCall = generateKeywordCall(keywordArguments, identation);

						messageLine.push(keywordMessageCall);
						break;
					default:
						const _never: never = receiver;
						throw new Error('Sound error');
				}
				break;
			default:
				const _never: never = messageCall;
				console.log('!!! = ', _never);

				throw new Error('SoundError');
		}
	}
	// generate ident
	const identStr = ' '.repeat(identation);
	// пушим строку вызовов сообщений
	const needBrackets = s.kindStatement === 'BracketExpression';


	const messageCall = !needBrackets ? messageLine.join('') : '(' + messageLine.join('') + ')';

	if (identation === 0) {
		return messageCall;
	} else {
		return identStr + messageCall;
	}


}
