import { StatementList } from '../AST_Nodes/AstNode';
import { Mutability, ReturnStatement } from '../AST_Nodes/Statements/Statement';
import { generateAssigment } from './assignment';
import { processExpression } from './expression/expression';
import { generateSwitchExpression } from './expression/switchExpression';
import { generateSwitchStatement } from './expression/switchStatement';
import { generateMethodDeclaration } from './methodDeclaration';
import { generateTypeDeclaration } from './typeDeclaration';
import {generateConstructor} from "./expression/constructor";


// То что может быть на первом уровне вложенности
export function generateNimFromAst(x: StatementList, identation = 0, discardable = false, includePrelude = false): string {
	let lines: string[] = [];
	if (includePrelude) {
		lines.push(`import "nivaPrelude"`)
	}

	if (discardable) {
		lines.push('{. push discardable .}');
	}

	for (const s of x.statements) {
		switch (s.kindStatement) {
			case 'MessageCallExpression':
			case "BracketExpression":
			case "Constructor":
				const expressionCode = processExpression(s, identation)
				lines.push(expressionCode)
			break;
			// :{
			// 	const firstMessage = s.messageCalls.at(0)
			// 	if (firstMessage?.selectorKind !== "keyword" || s.receiver.kindStatement !== "Primary"){
			// 		throw new Error("keyword message is not constuctor");
			// 	}
			// 	const argsCode: string[] = []
			// 	generateConstructor(s.receiver, firstMessage.arguments, argsCode)
			// 	break
			// }

			case 'Assignment':
				// codeGenerateExpression(s.value, lines)
				const assignment = s;
				if (assignment.mutability === Mutability.IMUTABLE) {
					
					lines.push(generateAssigment(assignment.assignmentTarget, assignment.to, identation, s.type));
				}
				break;

			case 'ReturnStatement':
			  const retrunCode: string =	generateReturn(s, identation)
				lines.push(retrunCode)
				break;
			case 'TypeDeclaration':
				const typeDeclarationAst = s
				const typeDeclarationCode: string = generateTypeDeclaration(typeDeclarationAst, identation)
				lines.push(typeDeclarationCode)

				break;
			case 'MethodDeclaration':
				const methodDeclarationAst = s
				const methodDeclarationCode = generateMethodDeclaration(methodDeclarationAst, identation);
				lines.push(methodDeclarationCode)

			break;

			case 'SwitchExpression':
				const switchCode = generateSwitchExpression(s, identation);
				lines.push(switchCode)
				break;
			case 'SwitchStatement':
				const switchStatementCode = generateSwitchStatement(s, identation)
				lines.push(switchStatementCode)
				
				break;
				
			default:
				const _never: never = s;
				
				throw new Error('SoundError');
		}
	}

	return lines.join('\n');
}
function generateReturn(s: ReturnStatement, identation: number): string {
	const ident = " ".repeat(identation) 
	if (s.value.kindStatement === "SwitchExpression"){
		const switchCode = generateSwitchExpression(s.value, 0)
		return `${ident}return ${switchCode}` 
	}

	// if (s.value.kindStatement === "SwitchStatement"){
	// 	const switchStatementCode = generateSwitchStatement(s.value, 0)
	// 	return `${ident}return ${switchStatementCode}` 

	// }

	const exprCode = processExpression(s.value, 0)
	return `${ident}return ${exprCode}` 
}

