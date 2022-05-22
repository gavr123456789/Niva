import { MessageCall } from "./Messages/Message";
import { Receiver } from "./Receiver/Receiver";


export interface Expression {
  kindStatement: "Expression"
	receiver: Receiver;
	messageCalls: MessageCall[];
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
