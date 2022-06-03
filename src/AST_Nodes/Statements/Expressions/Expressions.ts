import { MessageCall } from "./Messages/Message";
import { Receiver } from "./Receiver/Receiver";

interface BaseExpression {
	receiver: Receiver;
	messageCalls: MessageCall[];
}

export interface Expression extends BaseExpression {
  kindStatement: "Expression"
}

export interface BracketExpression extends BaseExpression {
	kindStatement: "BracketExpression"
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
