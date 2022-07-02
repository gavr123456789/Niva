
import { TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { MessageCallExpression } from './AST_Nodes/Statements/Expressions/Expressions';
import { Assignment } from './AST_Nodes/Statements/Statement';
import { CodeDB, MethodKinds } from './CodeDB/codeDB';
import { generateNimFromAst } from './CodeGenerator/codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';
import { blockBody, blockConstructor } from './Parsing/codeBlock';
import { expressionList } from './Parsing/Expression/expression';
import { binaryArgument, binaryMessage, binarySelector, messages_binaryFirst } from './Parsing/Expression/MessageCall/binary';
import { keywordArgument, keywordM, keywordMessage, messages_keywordFirst } from './Parsing/Expression/MessageCall/keyword';
import { messageCall } from './Parsing/Expression/MessageCall/messageCall';
import { primary, receiver, receiver_expressionInBrackets } from './Parsing/Expression/MessageCall/receiver';
import { messages_unaryFirst, unaryMessage, unarySelector } from './Parsing/Expression/MessageCall/unaryCall';
import { switchBranch, switchBranchElseStatement, switchExpression } from './Parsing/Expression/Switch/switchExpression';
import {unaryTypedIdentifier, untypedIdentifier} from './Parsing/identifiers';
import { anyLiteral, boolLiteral, integerLiteral, stringLiteral } from './Parsing/literals';
import { assignment } from './Parsing/Statements/assignment';
import { binaryMethodDeclaration, binaryMethodDeclarationArg } from './Parsing/Statements/MethodDeclaration/binary';
import { methodBody, methodBodyFull, methodBodyShort } from './Parsing/Statements/MethodDeclaration/body';
import {
	keywordMethodDeclaration,
	keywordMethodDeclarationArgs,
	keywordNoTypeNoLocalName,
	keywordNoTypeWithLocalName,
	keywordWithTypeNoLocalName,
	keywordWithTypeWithLocalName,
	localNameKeywordArg
} from './Parsing/Statements/MethodDeclaration/keyword';
import { returnTypeDeclaration } from './Parsing/Statements/MethodDeclaration/returnTypeDeclaration';
import { unaryMethodDeclaration } from './Parsing/Statements/MethodDeclaration/unary';
import { returnStatement } from './Parsing/Statements/return';
import { statement, statements, switchStatement } from './Parsing/Statements/statements';
import { typeDeclaration, typedProperties, typedProperty } from './Parsing/Statements/typeDeclaration';


export interface ContextInformation {
  kind: MethodKinds | "__global__" ;
	forType: string
	withName: string
}
class State {
	isInMethodBody = false
	insideMessage: ContextInformation = {
		forType: "__global__",
		withName: "__global__",
		kind: "__global__"
	}
	
	enterMethodScope(x: ContextInformation){
		this.insideMessage = x
	}
	exitFromMethodDeclaration(){
		this.insideMessage.forType = "__global__";
		this.insideMessage.withName = "__global__";
		this.insideMessage.kind = "__global__";
		this.isInMethodBody = false
	}


	errors: NivaError[] = []
}


export interface TypeField {
	type: string
}



export const codeDB: CodeDB = new CodeDB()
export const state = new State()

type qwe = State | number

function sas(sas: qwe) {
	if (typeof sas === "object" && "forType" in sas){

	}
}

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
			errorKind: 'RedefinitionOfVariable',
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
	// const variables = new Map<string, Map<string, Assignment>>();

	// variables.set('global', new Map<string, Assignment>());

	const semantics: NivaSemantics = grammar.createSemantics();

	semantics.addOperation<string | ASTNode | MessageCallExpression>('toAst()', {
		statements,

		statement,
		messageCall,

		/// METHOD DECLARATION

		// Unary
		methodDeclaration(_dash: TerminalNode, _s, oneOfTheMessagesType) {
			return oneOfTheMessagesType.toAst();
		},
		unaryMethodDeclaration,

		// Binary
		binaryMethodDeclaration,
		binaryMethodDeclarationArg,

		// Keyword
		keywordMethodDeclaration,
		keywordMethodDeclarationArgs,

		keywordWithTypeWithLocalName,
		keywordNoTypeWithLocalName,
		keywordWithTypeNoLocalName,
		keywordNoTypeNoLocalName,

		methodBody,
		methodBodyFull,
		methodBodyShort,

		returnStatement,

		switchExpression,
		switchBranchElseStatement,

		switchBranch,
		expressionList,
		switchStatement,

		returnTypeDeclaration,


		receiver_expressionInBrackets,
		messages_unaryFirst,
		unaryMessage,
		unarySelector,

		messages_binaryFirst,
		binaryMessage,
		binarySelector,
		binaryArgument,

		messages_keywordFirst,

		keywordMessage,
		keywordM,
		keywordArgument,

		typeDeclaration,
		typedProperties,
		typedProperty,

		/// CodeBlock
		blockConstructor,
		blockBody,

		assignment,

		receiver,

		primary,

		untypedIdentifier,
		unaryTypedIdentifier,

		anyLiteral,
		stringLiteral,
		integerLiteral,
		boolLiteral

	});

	const matchResult = grammar.match(code);
	if (matchResult.failed()) {
		console.error(matchResult.message);
		throw new Error('grammar failed');
	}
	const Ast: StatementList = semantics(matchResult).toAst();

	const generatedNimCode = generateNimFromAst(Ast, 0, discardable, includePrelude);

	return [Ast, generatedNimCode, state.errors];
}

// console.log(JSON.stringify(generateNimCode('int sas = [ 1 echo ].\nint sus = [ 2 echo ].'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 from: 2 to: 3'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas ses'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas + 2 sas'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('type Person name: string age: int'), undefined, 2));
// console.log(JSON.stringify(generateNimCode('type Person name: string. -Person sas = [ x echo ]')[0], undefined, 2));
// const codeHas = `
// int from: sas to: sus = [1 echo]
// `
// console.log(JSON.stringify(generateNimCode(codeHas)[1], undefined, 2));
// const codeHasnt = `
// int from to = [1 echo]
// `
// console.log(JSON.stringify(generateNimCode(codeHasnt)[1], undefined, 2));
