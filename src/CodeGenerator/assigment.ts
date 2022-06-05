import { MessageCallExpression } from "../AST_Nodes/Statements/Expressions/Expressions";
import { processExpression } from "./expression/expression";

export function generateAssigment(assignmentTarget: string, to: MessageCallExpression, identation: number, type?: string): string {
	if (to.receiver.kindStatement === "BracketExpression") {
		throw new Error("BracketExpression not supported as left part of assigment");
	}

	const ident = " ".repeat(identation)

	if (to.messageCalls.length === 0) {
		const assignRightValue = to.receiver.atomReceiver.value;
		if (type) {
			return `${ident}let ${assignmentTarget}: ${type} = ${assignRightValue}`;
		} else {
			return `${ident}let ${assignmentTarget} = ${assignRightValue}`;
		}
	} else {
		// identation after '=' not needed
    const messagesAfterAsign = processExpression(to, 0)
		// Todo infer types
		return `${ident}let ${assignmentTarget} = ${messagesAfterAsign}`
	}
}
