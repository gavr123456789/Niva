import fs from 'fs';
import ohm, { IterationNode, NonterminalNode, TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { Expression, Parentheses } from './AST_Nodes/Statements/Expressions/Expressions';
import { Assignment, Mutability } from './AST_Nodes/Statements/Statement';
import { generateNimFromAst } from './codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';
import { echo } from './utils';

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
	// const statementsGb: StatementList = {
	// 	kind: 'StatementList',
	// 	statements: []
	// };
	// place to variable name to AST node
	const variables = new Map<string, Map<string, Assignment>>();
	variables.set('global', new Map<string, Assignment>());

	const errors: NivaError[] = [];

	const semantics: NivaSemantics = grammar.createSemantics();

	semantics.addOperation<string | ASTNode | Expression>('toAst()', {
		statements(statement: NonterminalNode, arg1: IterationNode, otherStatements: IterationNode, arg3: IterationNode) {
			echo('statement');
			const firstStatementAst = statement.toAst()
			echo({firstStatementAst})

			const result: StatementList = {
				kind: 'StatementList',
				statements: [firstStatementAst]
			};

			for (const q of otherStatements.children) {
				const otherStatementAst = q.toAst();
				result.statements.push(otherStatementAst)
			}
			

			echo({result})

			return result;
		},

		statement_returnStatement(arg0: NonterminalNode) {
			echo('statement_returnStatement');
			return arg0.toAst();
		},
		statement_typeDeclarationStatement(arg0: NonterminalNode) {
			echo('statement_typeDeclarationStatement');
			return arg0.toAst();
		},
		statement_methodDeclarationStatement(arg0: NonterminalNode) {
			echo('statement_methodDeclarationStatement');
			return arg0.toAst();
		},
		statement_expressionStatement(expression: NonterminalNode) {
			echo('statements_expressionStatement arg0.sourceCode = ', expression.sourceCode);
			return expression.toAst();
		},
		statement(s: NonterminalNode) {
			// echo('statements, ', s.children[0].ctorName);
			// const firstStatement = s.children[0];
			// const _dot = s.children[1];
			// const otherStatements = s.children[2];

			// if (!firstStatement) return '';
			// echo('firstStatement node sourcecode = ', firstStatement.sourceString);

			// statementsGb.statements.push(firstStatement.toAst());
			// if (otherStatements) {
			// 	otherStatements.children.forEach((x) => x.toAst());
			// }

			echo('statement');

			return s.toAst();
		},
		typeDeclaration(
			arg0: TerminalNode,
			arg1: NonterminalNode,
			arg2: NonterminalNode,
			arg3: NonterminalNode,
			arg4: TerminalNode,
			arg5: NonterminalNode,
			arg6: NonterminalNode
		) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		typedProperties(
			arg0: NonterminalNode,
			arg1: IterationNode,
			arg2: IterationNode,
			arg3: IterationNode,
			arg4: IterationNode
		) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		typedProperty(arg0: NonterminalNode, arg1: TerminalNode, arg2: NonterminalNode, arg3: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		// methodDeclaration(
		// 	arg0: NonterminalNode,
		// 	arg1: NonterminalNode,
		// 	arg2: NonterminalNode,
		// 	arg3: TerminalNode,
		// 	arg4: NonterminalNode,
		// 	arg5: NonterminalNode
		// ) {
		// 	echo(arg0.ctorName);
		// 	return arg0.toAst();
		// },
		// keywordMethodDeclarationArgs(
		// 	arg0: NonterminalNode,
		// 	arg1: NonterminalNode,
		// 	arg2: IterationNode,
		// 	arg3: IterationNode,
		// 	arg4: IterationNode
		// ) {
		// 	echo(arg0.ctorName);
		// 	return arg0.toAst();
		// },
		// keywordMethodDeclarationArg(
		// 	arg0: NonterminalNode,
		// 	arg1: TerminalNode,
		// 	arg2: NonterminalNode,
		// 	arg3: NonterminalNode
		// ) {
		// 	echo(arg0.ctorName);
		// 	return arg1.toAst();
		// },
		expression(arg0: NonterminalNode) {
			echo('expression');
			return arg0.toAst();
		},
		assignment(
			assignmentTarget: NonterminalNode,
			_arg1: NonterminalNode,
			_assignmentOp: NonterminalNode,
			_arg3: NonterminalNode,
			expression: NonterminalNode
		) {
			const message = assignmentTarget.source.getLineAndColumnMessage();
			const astAssign: Assignment = {
				kindStatement: 'Assignment',
				assignmentTarget: assignmentTarget.sourceString,
				mutability: Mutability.IMUTABLE,
				to: expression.toAst(),
				messagelineAndColumnMessage: message,
				sourceCode: expression.sourceString,
				file: ''
			};

			addGlobalVariableDeclaratuon(variables.get('global'), astAssign, errors);
			echo({ astAssign });
			return astAssign;
		},
		assignmentTarget(arg0: NonterminalNode) {
			echo('assignmentTarget');
			return arg0.toAst();
		},
		basicExpression(primary: NonterminalNode, messages: IterationNode, cascadedMessages: IterationNode) {
			const primaryAst = primary.toAst();
			echo('basicExpression, primary to ast = ', primaryAst);

			return primaryAst;
		},
		primary_expressionInBrackets(arg0: TerminalNode, arg1: NonterminalNode, arg2: TerminalNode) {
			echo('primary_expressionInBrackets');
			const res: Parentheses = {
				kindExpression: 'Parentheses',
				expression: arg1.toAst()
			};
			return res;
		},
		primary(arg0: NonterminalNode) {
			echo('primary');

			return arg0.toAst();
		},
		messages_unaryFirst(arg0: IterationNode, arg1: IterationNode, arg2: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		messages_binaryFirst(arg0: IterationNode, arg1: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		messages(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		keywordM(arg0: NonterminalNode, arg1: TerminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		keywordMessage(
			arg0: NonterminalNode,
			arg1: IterationNode,
			arg2: IterationNode,
			arg3: IterationNode,
			arg4: NonterminalNode
		) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},

		keywordArgument(arg0: NonterminalNode, arg1: IterationNode, arg2: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		binaryMessage(arg0: NonterminalNode, arg1: NonterminalNode, arg2: NonterminalNode, arg3: NonterminalNode) {
			echo('binaryMessage');

			echo(arg0.ctorName);
			return arg0.toAst();
		},
		binaryArgument(arg0: NonterminalNode, arg1: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		unaryMessage(arg0: NonterminalNode, arg1: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		unarySelector(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		cascadedMessages(arg0: IterationNode, arg1: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		blockConstructor(
			arg0: TerminalNode,
			arg1: NonterminalNode,
			arg2: NonterminalNode,
			arg3: NonterminalNode,
			arg4: TerminalNode
		) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		blockBody(arg0: IterationNode, arg1: IterationNode, arg2: NonterminalNode, arg3: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		blockArgument(arg0: NonterminalNode, arg1: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		returnStatement(arg0: NonterminalNode, arg1: NonterminalNode, arg2: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		identifier(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		untypedIdentifier(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		type(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		typedIdentifier(arg0: NonterminalNode, arg1: TerminalNode, arg2: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		identifierName(arg0: NonterminalNode, arg1: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		identifierStart(arg0: NonterminalNode | TerminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		identifierPart(arg0: NonterminalNode) {
			echo(arg0.ctorName);
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
		doubleStringCharacter_nonEscaped(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		doubleStringCharacter_escaped(arg0: TerminalNode, arg1: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		doubleStringCharacter_lineContinuation(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		doubleStringCharacter(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		// integerLiteral_nonZero(arg0: NonterminalNode, arg1: IterationNode) {
		// 	echo("integerLiteral_nonZero");

		// 	return arg0.ctorName;
		// },
		// integerLiteral_zero(arg0: TerminalNode) {
		// 	echo("integerLiteral_zero");

		// 	return arg0.ctorName;
		// },
		integerLiteral(arg0: NonterminalNode) {
			echo('integerLiteral, ctorName = ', arg0.ctorName);

			return {
				kind: 'IntLiteralNode',
				value: arg0.sourceString
			};
		},
		// decimalDigit(arg0: TerminalNode) {
		// 	return arg0.ctorName;
		// },
		// nonZeroDigit(arg0: TerminalNode) {
		// 	return arg0.ctorName;
		// },
		trueLiteral(arg0: TerminalNode) {
			return 'true';
		},
		falseLiteral(arg0: TerminalNode) {
			return 'false';
		},
		boolLiteral(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		nullLiteral(arg0: TerminalNode) {
			return 'null';
		},
		// sourceCharacter(arg0: NonterminalNode) {
		// 	echo(arg0.ctorName);
		// 	return arg0.toAst();
		// },

		unicodeEscapeSequence(
			arg0: TerminalNode,
			arg1: NonterminalNode,
			arg2: NonterminalNode,
			arg3: NonterminalNode,
			arg4: NonterminalNode
		) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		characterEscapeSequence(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		singleEscapeCharacter(arg0: TerminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		nonEscapeCharacter(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		escapeCharacter(arg0: NonterminalNode | TerminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		comment(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},

		reservedWord(arg0: NonterminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		keyword(arg0: TerminalNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		separator(arg0: IterationNode) {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		binaryCharacter(arg0: TerminalNode) {
			return arg0.ctorName;
		},
		binarySelector(arg0: IterationNode) {
			return arg0.ctorName;
		},
		assignmentOp(arg0: TerminalNode) {
			echo(arg0.ctorName);
			return '=';
		},
		returnOp(arg0: TerminalNode) {
			echo(arg0.ctorName);
			return 'return';
		},
		dot(arg0: TerminalNode, arg1) {
			echo(arg0.ctorName);
			return ';';
		}
	});

	///
	const matchResult = grammar.match(code);
	if (matchResult.failed()) {
		console.error(matchResult.message);
		throw new Error('grammar failed');
	}
	const sas: StatementList = semantics(matchResult).toAst();

	echo('statementsGb = ', sas);
	const generatedNimCode = generateNimFromAst(sas);
	echo('generatedNimCode', generatedNimCode);

	return [ sas, generatedNimCode, errors ];
}

generateNimCode('x = 4. y = 6.\n z = 7.');
