
import ohm, { IterationNode, NonterminalNode, TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { BracketExpression, ElseBranch, Expression, MessageCallExpression, SwitchBranch, SwitchExpression, SwitchStatement } from './AST_Nodes/Statements/Expressions/Expressions';
import { Assignment, BodyStatements, Mutability, ReturnStatement } from './AST_Nodes/Statements/Statement';
import { generateNimFromAst } from './CodeGenerator/codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';

import { IntLiteral } from './AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/IntLiteralNode';
import { Receiver } from './AST_Nodes/Statements/Expressions/Receiver/Receiver';
import {
	BinaryArgument,
	BinaryMessage,
	KeywordArgument,
	KeywordMessage,
	MessageCall,
	UnaryMessage
} from './AST_Nodes/Statements/Expressions/Messages/Message';
import { StringLiteral } from './AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/StringLiteralNode';
import { AnyLiteral } from './AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/AnyLiteral';
import { TypeDeclaration, TypedProperty } from './AST_Nodes/Statements/TypeDeclaration/TypeDeclaration';
import { BinaryMethodDeclaration, BinaryMethodDeclarationArg, KeywordMethodArgument, KeywordMethodDeclaration, KeywordMethodDeclarationArg, MethodDeclaration, UnaryMethodDeclaration } from './AST_Nodes/Statements/MethodDeclaration/MethodDeclaration';
import { Identifer } from './AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier';
import { BoolLiteral } from './AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/BoolLiteral';
import { BlockConstructor } from './AST_Nodes/Statements/Expressions/Receiver/BlockConstructor';



let isInMethodBody = false

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
export function generateNimCode(code: string, discardable = false, includePrelude = false): [StatementList, string, NivaError[]] {
	// place to variable name to AST node
	const variables = new Map<string, Map<string, Assignment>>();
	// const typeDeclarations = new Map<string, Map<string, Assignment>>();
	// const unaryMethodDeclarations = new Map<string, Map<string, Assignment>>();
	// const binaryMethodDeclarations = new Map<string, Map<string, Assignment>>();
	// const keywordMethodDeclarations = new Map<string, Map<string, Assignment>>();

	variables.set('global', new Map<string, Assignment>());

	const errors: NivaError[] = [];

	const semantics: NivaSemantics = grammar.createSemantics();

	semantics.addOperation<string | ASTNode | MessageCallExpression>('toAst()', {
		statements(
			_s1,
			statement: NonterminalNode,
			_dot: IterationNode,
			otherStatements: IterationNode,
			_dot2: IterationNode,
			_s3
		): StatementList {
			// echo('statement');
			const firstStatementAst = statement.toAst();
			// echo({ firstStatementAst });

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
			return s.toAst();
		},
		messageCall(receiver: NonterminalNode, maymeMessages, cascadedMessages): MessageCallExpression {
			const astOfReceiver: Receiver = receiver.toAst();
			const result: MessageCallExpression = {
				kindStatement: 'MessageCallExpression',
				receiver: astOfReceiver,
				messageCalls: []
			};
			// check if receiver has messages
			const messages = maymeMessages.children.at(0);
			if (!messages) {
				return result;
			}

			const astMessages = messages.toAst();
			result.messageCalls = astMessages;
			return result;
		},

		/// METHOD DECLARATION

		// Unary
		methodDeclaration(_dash, oneOfTheMessagesType) {
			return oneOfTheMessagesType.toAst();
		},
		unaryMethodDeclaration(
			untypedIdentifier,
			_s,
			unarySelector,
			_s2,
			returnTypeDeclaration,
			_s3,
			_eq,
			_s4,
			methodBody
		): MethodDeclaration {
			// -Person sas = []
			const bodyStatements: BodyStatements = methodBody.toAst();
			const returnType = returnTypeDeclaration.children.at(0)?.toAst()
			const expandableType = untypedIdentifier.sourceString

			 

			const unary: UnaryMethodDeclaration = {
				expandableType,
				bodyStatements,
				methodKind: 'UnaryMethodDeclaration',
				name: unarySelector.sourceString,
				returnType
			};

			const result: MethodDeclaration = {
				kindStatement: "MethodDeclaration",
				method: unary
			}

			return result;
		},

		// Binary
		binaryMethodDeclaration(
			untypedIdentifier,
			_s,
			binaryMethodDeclarationArg,
			_s2,
			returnTypeDeclaration,
			_s3,
			_eq,
			_s4,
			methodBody): MethodDeclaration{
			const bodyStatements: BodyStatements = methodBody.toAst();
			const returnType = returnTypeDeclaration.children.at(0)?.toAst()
			const binarySelector: BinaryMethodDeclarationArg = binaryMethodDeclarationArg.toAst();
			const expandableType = untypedIdentifier.sourceString

			const binary: BinaryMethodDeclaration = {
				methodKind: "BinaryMethodDeclaration",
				expandableType,
				returnType,
				binarySelector: binarySelector.binarySelector,
				identifier: binarySelector.identifier,
				bodyStatements,
			}

			const result: MethodDeclaration = {
				kindStatement: "MethodDeclaration",
				method: binary
			}

			return result;
		},
		binaryMethodDeclarationArg(binarySelector, _s, identifier): BinaryMethodDeclarationArg {
			const ident: Identifer = identifier.toAst()
			const result: BinaryMethodDeclarationArg = {
				binarySelector: binarySelector.sourceString,
				identifier: ident
			}
			return result
		},

		// Keyword
		keywordMethodDeclaration(
			untypedIdentifier,
			keywordMethodDeclarationArgs,
			_s,
			returnTypeDeclaration,
			_s2,
			_eq,
			_s3,
			methodBody
			): MethodDeclaration{
			const bodyStatements: BodyStatements = methodBody.toAst();
			const returnType = returnTypeDeclaration.children.at(0)?.toAst()
			const expandableType = untypedIdentifier.sourceString
			const keywordMethodDeclarationArg: KeywordMethodDeclarationArg = keywordMethodDeclarationArgs.toAst()
			const keyValueNames: KeywordMethodArgument[] = keywordMethodDeclarationArg.keyValueNames

			const keyword: KeywordMethodDeclaration = {
				methodKind: "KeywordMethodDeclaration",
				returnType,
				bodyStatements,
				expandableType,
				keyValueNames
			}

			const result: MethodDeclaration = {
				kindStatement: "MethodDeclaration",
				method: keyword
			}
			return result

		},
		keywordMethodDeclarationArgs(_s, keywordMethodDeclarationArg, _s2, otherKeywordMethodDeclarationArg, _s3 ): KeywordMethodDeclarationArg{
			const firstKeyVal: KeywordMethodArgument = keywordMethodDeclarationArg.toAst()
			const otherKeyVals: KeywordMethodArgument[] = otherKeywordMethodDeclarationArg.children.map(x => x.toAst())
			const keyValueNames: KeywordMethodArgument[] = [firstKeyVal, ...otherKeyVals]

			const result: KeywordMethodDeclarationArg = {
				keyValueNames
			}
			return result
		},
		keywordMethodDeclarationArg(untypedIdentifier, _colon, _s, identifier ): KeywordMethodArgument{
			const ident: Identifer = identifier.toAst();

			return {
				keyName: untypedIdentifier.sourceString,
				identifier: ident
			}
		},

		methodBody(_openBracket, _s1, statements, _s2, _closeBracket): BodyStatements {
			isInMethodBody = true
			const child = statements.children.at(0)
			if (statements.children.length !== 1 || !child){
				throw new Error("statements node must have one child");
			}
			const statementsList: StatementList = child.toAst()

			// const switchChild = switchExpression.children.at(0)
			// const switchReturnAst: SwitchReturn[] = switchChild? switchChild.toAst() : []

			const bodyStatements: BodyStatements = {
				statements: statementsList.statements,
			}
			isInMethodBody = false

			return bodyStatements
		},
		
		returnStatement(_op, _s, expression): ReturnStatement{
			if (!isInMethodBody) {
				throw new Error("Retrun statement must be inside method body");
			}
			const expr = expression.toAst()
			const result: ReturnStatement = {
				kindStatement: "ReturnStatement",
				value: expr
			}
			return result
		},

		switchExpression(_s0, switchBranch, _s, otherSwitchBranch, _s2, switchBranchElseStatement, _s3): SwitchExpression{
			const switchReturn1: SwitchBranch = switchBranch.toAst()
			const switchReturn2: SwitchBranch[] = otherSwitchBranch.children.map(x => x.toAst())
			
			const maybeElseBranch = switchBranchElseStatement.children.at(0)
			if (maybeElseBranch) {
				const elseBranch = maybeElseBranch.toAst()
				const result: SwitchExpression = {
					kindStatement: "SwitchExpression",
					branches: [switchReturn1, ...switchReturn2],
					elseBranch: elseBranch
				}
				return result
			}
			const result: SwitchExpression = {
				kindStatement: "SwitchExpression",
				branches: [switchReturn1, ...switchReturn2],
			}
			return result
		},

		switchBranchElseStatement(_arrow, _s, expression): ElseBranch {
			const elseBranch: ElseBranch = {
				thenDoExpression: expression.toAst()
			}
			return elseBranch
		},



		switchBranch(_pipe, _s, expressionListNode, _s2, _arrow, _s3, thenDoExpression, _s4 ): SwitchBranch{
	
			const expressionList: Expression[] = expressionListNode.toAst()
			if (expressionList.find(x => x.kindStatement === "SwitchExpression")) {
				throw new Error(`${expressionListNode.sourceString} case cant be another switch expression`);
			}
			const thenDoExpressionNode: Expression = thenDoExpression.toAst()
			if (thenDoExpressionNode.kindStatement === "SwitchExpression") {
				throw new Error(`${thenDoExpression.sourceString} nested switch expression are not supported`);
			}
			
			const result: SwitchBranch = {
				caseExpressions: expressionList,
				thenDoExpression: thenDoExpressionNode
			}
			
			return result
		},

		expressionList(expression, _comma, _s,otherExpressionsNode): Expression[]{
			const firstExpression: Expression = expression.toAst()
			const otherExpressions: Expression[] = otherExpressionsNode.children.map(x => x.toAst())
			const result = [firstExpression, ...otherExpressions]
			return result
		},

		switchStatement(receiverNode, switchExpressionNode): SwitchStatement{
			const switchExpression: SwitchExpression = switchExpressionNode.toAst()
			const receiver: Receiver = receiverNode.toAst()
			const result: SwitchStatement = {
				kindStatement: "SwitchStatement",
				receiver,
				switchExpression
			}
			
			return result
		},

		returnTypeDeclaration(_returnTypeOperator, _s, untypedIdentifier): string {
			return untypedIdentifier.sourceString;
		},

		
		receiver_expressionInBrackets(_lb, expression, _rb): BracketExpression {

			const result: MessageCallExpression = expression.toAst()
			const result2: BracketExpression = {...result, kindStatement: "BracketExpression"}

			return result2
		},
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

		keywordMessage(_s1, keywordM, _s2, keywordArgument, _s3, _s4): KeywordMessage {
			const resultArguments: KeywordArgument[] = [];

			// keywordM and keywordArgument always have same length
			keywordM.children.forEach((node, i) => {
				const keywordArgName: string = node.toAst();
				const arg: Receiver = keywordArgument.children[i].toAst();
				resultArguments.push({
					ident: keywordArgName,
					receiver: arg
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
		keywordArgument(receiver, unaryMessages, binaryMessages): Receiver {
			const sas: Receiver = receiver.toAst()
			return sas;
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

		/// CodeBlock
		blockConstructor(_lsquare, _s, blockBody,_s1, _rsquare): BlockConstructor {
			const blockConstructor: BlockConstructor = blockBody.toAst()
			return blockConstructor
		},
		blockBody(s,_s, maybeStatements): BlockConstructor {
			const statements = maybeStatements.children.at(0)
			if (!statements) {
				throw new Error("Empty code block is useless");
			}

			const statementsNode: StatementList = statements.toAst()

			const blockArgList = s.children.at(0)
			const blockArguments = blockArgList? blockArgList.children.map(x => x.toAst()): []

			const result: BlockConstructor = {
				kindStatement: "BlockConstructor",
				blockArguments,
				statements: statementsNode.statements
			}
				
			return result
		},



		///

		assignment(
			assignmentTarget: NonterminalNode,
			_arg1: NonterminalNode,
			_assignmentOp: NonterminalNode,
			_arg3: NonterminalNode,
			expression: NonterminalNode
		) {
			const message = assignmentTarget.source.getLineAndColumnMessage();
			const assignRightValue: Expression = expression.toAst();
			if (assignRightValue.kindStatement === "SwitchExpression" && !assignRightValue.elseBranch) {
				
				throw new Error(`${message} switch assignment must have else branch`);
			}
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
			return astAssign;
		},

		receiver(x): Receiver {
			if (x.sourceString[0] === "(") {
				const result: BracketExpression = x.toAst()
				return result
			}
			if (x.sourceString[0] === "[") {
				const result: BlockConstructor = x.toAst()
				return result
			}

			const result: Receiver = {
				kindStatement: 'Primary',
				atomReceiver: x.toAst()
			};
			return result;
		},

		primary(arg0: NonterminalNode) {
			return arg0.toAst();
		},

		untypedIdentifier(identifierName): Identifer {
			const result: Identifer = { 
				kindPrimary: "Identifer",
				value: identifierName.sourceString,
			}
			return result
		},
		unaryTypedIdentifier(untypedIdentifier, _twoColons, unaryType ): Identifer{
			const result: Identifer = { 
				kindPrimary: "Identifer",
				value: untypedIdentifier.sourceString,
				type: unaryType.sourceString
			}
			return result
		},

		anyLiteral(arg0: NonterminalNode): AnyLiteral {
			return arg0.toAst();
		},
		stringLiteral(_lQuote: TerminalNode, text: IterationNode, _rQuote: TerminalNode): StringLiteral {
			const result: StringLiteral = {
				kindPrimary: 'StringLiteral',
				value: '"' + text.sourceString + '"'
			};
			return result;
		},
		integerLiteral(intLiteral: NonterminalNode): IntLiteral {
			const result: IntLiteral = {
				kindPrimary: 'IntLiteral',
				value: intLiteral.sourceString
			};
			return result;
		},
		boolLiteral(boolLiteral): BoolLiteral{
			const result: BoolLiteral = {
				kindPrimary: "BoolLiteral",
				value: boolLiteral.sourceString
			}
			return result
		}
		
	});

	const matchResult = grammar.match(code);
	if (matchResult.failed()) {
		console.error(matchResult.message);
		throw new Error('grammar failed');
	}
	const Ast: StatementList = semantics(matchResult).toAst();

	const generatedNimCode = generateNimFromAst(Ast, 0, discardable, includePrelude);

	return [ Ast, generatedNimCode, errors ];
}

// console.log(JSON.stringify(generateNimCode('1 + 2'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 from: 2 to: 3'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas ses'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas + 2 sas'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('type Person name: string age: int'), undefined, 2));
// console.log(JSON.stringify(generateNimCode('-Person sas = [ x echo ]')[1], undefined, 2));
// console.log(JSON.stringify(generateNimCode('(1 + 2) echo.')[1], undefined, 2));
