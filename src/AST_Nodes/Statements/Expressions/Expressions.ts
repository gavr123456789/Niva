import { Primary } from "./Primary/Primary";
import { Receiver } from "./Receiver/Receiver";


export interface Expression {
  kindStatement: "Expression"
	receiver: Receiver;
	messageCalls: MessageCall[];
}

export type MessageCall = UnaryMessage | BinaryMessage | KeywordMessage;
export interface UnaryMessage {
	selectorKind: 'unary';
	messageIdent: string;
}
export interface BinaryMessage {
	selectorKind: 'binary';
	messageIdent: string;
  argument: string
}

interface KeywordArgument {
	// from: 10
	// ident: value
	ident: string
	value: string // TODO
}
export interface KeywordMessage {
	selectorKind: 'keyword';
	arguments: KeywordArgument[]
}

// 5 factorial
// const basicExpression: BasicExpression = {
//   primary: 5,
//   message: {
//     selectorKind: "unary",
//     messageIdent: "factorial"
//   }
// }

// export interface Parentheses {
// 	kindExpression: 'Parentheses';
// 	expression: ExpressionStatement;
// }
