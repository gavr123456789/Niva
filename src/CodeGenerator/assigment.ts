import { Expression } from '../AST_Nodes/Statements/Expressions/Expressions';
import { processExpression } from './expression/expression';
import { generateSwitchExpression } from './expression/switchExpression';

export function generateAssigment(assignmentTarget: string, to: Expression, identation: number, type?: string): string {
	// console.log('to = ', to);
	const ident = ' '.repeat(identation);

	if (to.kindStatement === 'BracketExpression' || to.kindStatement === 'MessageCallExpression') {
		if (to.receiver.kindStatement === 'BracketExpression') {
			throw new Error('BracketExpression not supported as left part of assigment');
		}

		if (to.receiver.kindStatement === 'BlockConstructor') {
			throw new Error('BlockConstructor not supported as left part of assigment');
		}
		
		if (to.messageCalls.length === 0) {
			const assignRightValue = to.receiver.atomReceiver.value;
			if (type) {
				return `${ident}var ${assignmentTarget}: ${type} = ${assignRightValue}`;
			} else {
				return `${ident}var ${assignmentTarget} = ${assignRightValue}`;
			}
		} else {
			// identation after '=' not needed
			const messagesAfterAsign = processExpression(to, 0);
			// Todo infer types
			return `${ident}var ${assignmentTarget} = ${messagesAfterAsign}`;
		}
	} else {
			// identation after '=' not needed
			const switchCode = generateSwitchExpression(to, 0);
			// Todo infer types
			return `${ident}var ${assignmentTarget} = ${switchCode}`;
	}
}
