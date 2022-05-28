import fs from 'fs';
import ohm, { IterationNode, NonterminalNode, TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { Expression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Assignment, Mutability } from './AST_Nodes/Statements/Statement';
import { generateNimFromAst } from './codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';
import { echo } from './utils';
import extras from 'ohm-js/extras';
import { IntLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/IntLiteralNode';
import { Primary } from './AST_Nodes/Statements/Expressions/Primary/Primary';
import { Receiver } from './AST_Nodes/Statements/Expressions/Receiver/Receiver';
import {
	BinaryArgument,
	BinaryMessage,
	KeywordArgument,
	KeywordMessage,
	MessageCall,
	UnaryMessage
} from './AST_Nodes/Statements/Expressions/Messages/Message';
import { StringLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/StringLiteralNode';
import { AnyLiteral } from './AST_Nodes/Statements/Expressions/Primary/Literals/AnyLiteral';
import { TypeDeclaration, TypedProperty } from './AST_Nodes/Statements/TypeDeclaration/TypeDeclaration';

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
		statements(
			statement: NonterminalNode,
			arg1: IterationNode,
			otherStatements: IterationNode,
			arg3: IterationNode
		) {
			echo('statement');
			const firstStatementAst = statement.toAst();
			echo({ firstStatementAst });

			const ast: StatementList = {
				kind: 'StatementList',
				statements: [ firstStatementAst ]
			};

			for (const otherStatemenrs of otherStatements.children) {
				const otherStatementAst = otherStatemenrs.toAst();
				ast.statements.push(otherStatementAst);
			}

			return ast;
		},

		statement(s: NonterminalNode) {
			echo('statement');
			return s.toAst();
		},
		expression(receiver: NonterminalNode, maymeMessages, cascadedMessages) {
			echo('expression');
			// TODO пока только primary
			const astOfReceiver: Receiver = receiver.toAst();
			const result: Expression = {
				kindStatement: 'Expression',
				receiver: astOfReceiver,
				messageCalls: []
			};
			// check if receiver has messages
			const messages = maymeMessages.children.at(0);
			if (!messages) {
				return result;
			}

			const astMessages = messages.toAst();
			console.log('astMessages =', astMessages);
			result.messageCalls = astMessages;
			return result;
		},

		/// METHOD DECLARATION
		
		///


		messages_unaryFirst(unaryMessages, binaryMessages, keywordMessages) {
			const astOfUnaryMessages: MessageCall[] = unaryMessages.children.map((x) => x.toAst()); // call unaryMessage
			const astOfBinaryMessages: MessageCall[] = binaryMessages.children.map((x) => x.toAst()); // call binaryMessages
			const astOfKeywordMessages: MessageCall[] = keywordMessages.children.map((x) => x.toAst()); // call keywordMessages
			return [ ...astOfUnaryMessages, ...astOfBinaryMessages, ...astOfKeywordMessages ];
		},
		unaryMessage(_s, unarySelector) {
			return unarySelector.toAst();
		},
		unarySelector(ident) {
			const result: UnaryMessage = {
				selectorKind: 'unary',
				unarySelector: ident.sourceString
			};
			return result;
		},

		messages_binaryFirst(binaryMessages, keywordMessages) {
			const astOfBinaryMessages: MessageCall[] = binaryMessages.children.map((x) => x.toAst()); // call binaryMessage
			const astOfKeywordMessages: MessageCall[] = keywordMessages.children.map((x) => x.toAst()); // call binaryMessage
			return [ ...astOfBinaryMessages, ...astOfKeywordMessages ];
		},
		binaryMessage(_spaces1, binarySelector, _spaces2, binaryArgument): BinaryMessage {
			const result: BinaryMessage = {
				selectorKind: 'binary',
				argument: binaryArgument.toAst(), // returns its name
				binarySelector: binarySelector.toAst() // returns its name
			};
			return result;
		},
		binarySelector(x) {
			return x.sourceString;
		},
		binaryArgument(receiver, unaryMessageNode): BinaryArgument {
			// get receiver
			const value: Receiver = receiver.toAst();
			const result: BinaryArgument = {
				value
			};

			// check if there unary Messages
			if (unaryMessageNode.children.length > 0) {
				const unaryMessages = unaryMessageNode.children.map((x) => x.toAst());
				result.unaryMessages = unaryMessages;
			}

			return result;
		},

		messages_keywordFirst(keywordMessage) {
			return [ keywordMessage.toAst() ];
		},

		keywordMessage(_s1, keywordM, _s2, keywordArgument, _s3, _s4) {
			const resultArguments: KeywordArgument[] = [];
			keywordM.children.forEach((x, y) => {
				const keyword = x.toAst();
				const arg = keywordArgument.children[y].toAst();
				resultArguments.push({
					ident: keyword,
					value: arg
				});
			});

			const result: KeywordMessage = {
				selectorKind: 'keyword',
				arguments: resultArguments
			};
			return result;
		},
		keywordM(identifier, colon) {
			return identifier.sourceString;
		},
		keywordArgument(receiver, unaryMessages, binaryMessages) {
			return receiver.sourceString;
		},

		typeDeclaration(_type, _s, untypedIdentifier, _s2, typedProperties): TypeDeclaration {
			const typedPropertiesAst: TypedProperty[] = typedProperties.toAst();

			const result: TypeDeclaration = {
				kindStatement: 'TypeDeclaration',
				typeName: untypedIdentifier.sourceString,
				typedProperties: typedPropertiesAst,
				ref: false // TODO
			};

			return result;
		},

		typedProperties(typedProperty, _spaces, other_typedProperty): TypedProperty[] {
			const result: TypedProperty[] = [ typedProperty.toAst() ];
			other_typedProperty.children.forEach((x) => result.push(x.toAst()));
			return result;
		},
		typedProperty(untypedIdentifier, _colon, _s, untypedIdentifierOrNestedType): TypedProperty {
			const typeNameSourceString = untypedIdentifierOrNestedType.sourceString;
			if (typeNameSourceString.startsWith('type')) {
				throw new Error('Nested types not supported yet');
			}

			const result: TypedProperty = {
				identifier: untypedIdentifier.sourceString,
				type: typeNameSourceString
			};

			return result;
		},

		assignment(
			assignmentTarget: NonterminalNode,
			_arg1: NonterminalNode,
			_assignmentOp: NonterminalNode,
			_arg3: NonterminalNode,
			expression: NonterminalNode
		) {
			const message = assignmentTarget.source.getLineAndColumnMessage();
			const assignRightValue: Expression = expression.toAst();

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

		receiver(x): Receiver {
			// TODO Пока только Primary
			const result: Receiver = {
				kindReceiver: 'Primary',
				atomReceiver: x.toAst()
			};
			return result;
		},

		primary(arg0: NonterminalNode) {
			echo('primary');

			return arg0.toAst();
		},

		anyLiteral(arg0: NonterminalNode): AnyLiteral {
			echo(arg0.ctorName);
			return arg0.toAst();
		},
		stringLiteral(_lQuote: TerminalNode, text: IterationNode, _rQuote: TerminalNode): StringLiteral {
			echo('stringLiteral = ', text.sourceString);
			const result: StringLiteral = {
				kindPrimary: 'StringLiteral',
				value: text.sourceString
			};
			return result;
		},

		integerLiteral(arg0: NonterminalNode): IntLiteral {
			echo('integerLiteral, ctorName = ', arg0.ctorName);
			const result: IntLiteral = {
				kindPrimary: 'IntLiteral',
				value: arg0.sourceString
			};
			return result;
		}
	});

	const matchResult = grammar.match(code);
	if (matchResult.failed()) {
		console.error(matchResult.message);
		throw new Error('grammar failed');
	}
	const Ast: StatementList = semantics(matchResult).toAst();

	echo('statementsGb = ', Ast);
	const generatedNimCode = generateNimFromAst(Ast, 0, false);
	echo('generatedNimCode', generatedNimCode);

	return [ Ast, generatedNimCode, errors ];
}

// console.log(JSON.stringify(generateNimCode('1 + 2'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 from: 2 to: 3'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas ses'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas + 2 sas'), undefined, 2) );
console.log(JSON.stringify(generateNimCode('type Person name: string age: int'), undefined, 2));

// generateNimCode('5 echo');
