import { StatementList } from './AST_Nodes/AstNode';
import { IntLiteralNode } from './AST_Nodes/Literals/IntLiteralNode';
import { StringLiteralNode } from './AST_Nodes/Literals/StringLiteralNode';
import { Expression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Mutability } from './AST_Nodes/Statements/Statement';

function generateAssigment(assignmentTarget: string, to: string, type?: string) {
	if (type) {
		return 'let ' + assignmentTarget + ': ' + type + ' = ' + to;
	} else {
		return 'let ' + assignmentTarget + ' = ' + to;
	}
}

export function generateNimFromAst(x: StatementList): string {
	let lines: string[] = [];
	for (const s of x.statements) {
		switch (s.kindStatement) {
			case 'ExpressionStatement':
				// codeGenerateExpression(s.value, lines)
				throw new Error("ExpressionStatement not done");
				

				break;
			case "Assignment":
				// codeGenerateExpression(s.value, lines)
				if (s.mutability === Mutability.IMUTABLE) {
					lines.push(generateAssigment(s.assignmentTarget, s.to.value, s.type));
				} 
				break;


			case 'MethodDeclarationStatement':
				throw new Error('MethodDeclarationStatement not done');
			case 'ReturnStatement':
				throw new Error('ReturnStatement not done');
			case 'TypeDeclarationStatement':
				throw new Error('TypeDeclarationStatement not done');

			default:
				const _never: never = s;
		}
	}

	return lines.join('\n');
}


// function codeGenerateExpression(x: Expression, lines: string[]) {
// 	switch (x.kindExpression) {
// 		case 'Assignment':
// 			if (x.mutability === Mutability.IMUTABLE) {
// 				lines.push(generateAssigment(x.assignmentTarget, x.to.value, x.type));
// 			} 
// 			break;
// 		case 'Parentheses':
// 			break;

// 		default:
// 			const _never: never = x;
// 			break;
// 	}
// }
