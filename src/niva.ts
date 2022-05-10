import fs from 'fs';
import ohm, { IterationNode, NonterminalNode, TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { Expression, MessageCall, UnaryMessage } from './AST_Nodes/Statements/Expressions/Expressions';
import { Assignment, Mutability } from './AST_Nodes/Statements/Statement';
import { generateNimFromAst } from './codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';
import { echo } from './utils';
import extras from 'ohm-js/extras';
import { IntLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/IntLiteralNode';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';
import { Receiver } from './AST_Nodes/Statements/Expressions/Receiver/Receiver';


// import {toAST} from 'ohm-js/extras'

function addGlobalVariableDeclaratuon(
	map: Map<string, Assignment> | undefined,
	assignment: Assignment,
	errors: NivaError[]
) {
	const varName = assignment.assignmentTarget;
	if (!map) {
		throw new Error('map of global variables is undefined!');
	}
	const alreadyDefined = map.get(varName);
	if (alreadyDefined) {
		errors.push({
			errorKind: 'RedefinitionOfVariableError',
			file: '', // TODO add file
			lineAndColMessage: assignment.messagelineAndColumnMessage,
			previousLineAndColMessage: alreadyDefined.messagelineAndColumnMessage,
			variableName: varName
		});
	}

	map.set(varName, assignment);
}

// Returns AST, nim code and errors
export function generateNimCode(code: string): [StatementList, string, NivaError[]] {
	// place to variable name to AST node
	const variables = new Map<string, Map<string, Assignment>>();
	// const typeDeclarations = new Map<string, Map<string, Assignment>>();
	// const unaryMethodDeclarations = new Map<string, Map<string, Assignment>>();
	// const binaryMethodDeclarations = new Map<string, Map<string, Assignment>>();
	// const keywordMethodDeclarations = new Map<string, Map<string, Assignment>>();

	variables.set('global', new Map<string, Assignment>());

	const errors: NivaError[] = [];

	const semantics: NivaSemantics = grammar.createSemantics();

	semantics.addOperation<string | ASTNode | Expression>('toAst()', {
		statements(statement: NonterminalNode, arg1: IterationNode, otherStatements: IterationNode, arg3: IterationNode) {
			echo('statement');
			const firstStatementAst = statement.toAst()
			echo({firstStatementAst})

			const ast: StatementList = {
				kind: 'StatementList',
				statements: [firstStatementAst]
			};

			for (const otherStatemenrs of otherStatements.children) {
				const otherStatementAst = otherStatemenrs.toAst();
				ast.statements.push(otherStatementAst)
			}

			// console.log("!!!, ast = ", JSON.stringify(ast));
			
			

			// echo({ast})

			return ast;
		},

		statement(s: NonterminalNode) {
			echo('statement');
			return s.toAst();
		},
		expression(receiver: NonterminalNode, messages, cascadedMessages) {
			echo('expression');
			// TODO пока только primary
			const astOfReceiver: Receiver = receiver.toAst()
			// 42 echo
			const result: Expression = {
				kindStatement: "Expression",
				receiver: astOfReceiver,
				messageCalls: []
			}
			// const astOfMessages: MessageCall[] = messages.children.map(x => x.toAst())
			messages.children.forEach(x => result.messageCalls.push(...x.toAst()))
			return result
		
		},
		
		unaryMessage(spaces, unarySelector) {
			return unarySelector.toAst()
		},
		unarySelector(ident) {
			const result: UnaryMessage = {
				selectorKind: "unary",
				messageIdent: ident.sourceString
			}
			return result
		},

		messages_unaryFirst(unaryMessages, y, z) {
			const astOfMessages: MessageCall[] = unaryMessages.children.map(x => x.toAst())
			return astOfMessages
		},
		messages_binaryFirst(x, y) {
			return ""
		},


		assignment(
			assignmentTarget: NonterminalNode,
			_arg1: NonterminalNode,
			_assignmentOp: NonterminalNode,
			_arg3: NonterminalNode,
			expression: NonterminalNode
		) {
			const message = assignmentTarget.source.getLineAndColumnMessage();
			const assignRightValue: Expression = expression.toAst()

			const astAssign: Assignment = {
				kindStatement: 'Assignment',
				assignmentTarget: assignmentTarget.sourceString,
				mutability: Mutability.IMUTABLE,
				to: assignRightValue,
				messagelineAndColumnMessage: message,
				sourceCode: expression.sourceString,
				file: ''
			};

			addGlobalVariableDeclaratuon(variables.get('global'), astAssign, errors);
			echo({ astAssign });
			return astAssign;
		},

		receiver(x){
			// TODO Пока только Primary
			const result: Receiver = {
				kindReceiver: "Primary",
				atomReceiver: x.toAst()
			}
			return result
		},

		primary(arg0: NonterminalNode) {
			echo('primary');

			return arg0.toAst();
		},

		anyLiteral(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		stringLiteral(arg0: TerminalNode, arg1: IterationNode, arg2: TerminalNode) {
			echo('stringLiteral = ', arg0.sourceString);
			const valueInsideKovichki = arg1.children.map((x) => x.sourceString).join();
			return valueInsideKovichki;
		},


		integerLiteral(arg0: NonterminalNode) {
			echo('integerLiteral, ctorName = ', arg0.ctorName);
			const result: IntLiteral = {
				kindPrimary: "IntLiteral",
				value: arg0.sourceString
			}
			return result
		},

	});

	const matchResult = grammar.match(code);
	if (matchResult.failed()) {
		console.error(matchResult.message);
		throw new Error('grammar failed');
	}
	const Ast: StatementList = semantics(matchResult).toAst();

	echo('statementsGb = ', Ast);
	const generatedNimCode = generateNimFromAst(Ast);
	echo('generatedNimCode', generatedNimCode);

	return [ Ast, generatedNimCode, errors ];
}

console.log(generateNimCode('x = 5.'));


// generateNimCode('5 echo');
