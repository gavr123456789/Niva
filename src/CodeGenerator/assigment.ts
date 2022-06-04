import { MessageCallExpression } from "../AST_Nodes/Statements/Expressions/Expressions";

export function generateAssigment(assignmentTarget: string, to: MessageCallExpression, type?: string): string {
	if (to.receiver.kindStatement === "BracketExpression") {
		throw new Error("BracketExpression not supported as left part of assigment");
	}
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
