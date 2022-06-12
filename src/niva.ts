
import { TerminalNode } from 'ohm-js';
import { ASTNode, StatementList } from './AST_Nodes/AstNode';
import { MessageCallExpression } from './AST_Nodes/Statements/Expressions/Expressions';
import { BinaryMethodDeclaration, KeywordMethodDeclaration, UnaryMethodDeclaration } from './AST_Nodes/Statements/MethodDeclaration/MethodDeclaration';
import { Assignment } from './AST_Nodes/Statements/Statement';
import { generateNimFromAst } from './CodeGenerator/codeGenerator';
import { NivaError } from './Errors/Error';
import grammar, { NivaSemantics } from './niva.ohm-bundle';
import { blockBody, blockConstructor } from './Parsing/codeBlock';
import { expressionList } from './Parsing/Expression/expression';
import { binaryArgument, binaryMessage, binarySelector, keywordArgument, keywordM, keywordMessage, messageCall, messages_binaryFirst, messages_keywordFirst, messages_unaryFirst, unaryMessage, unarySelector } from './Parsing/Expression/MessageCall/messageCall';
import { primary, receiver, receiver_expressionInBrackets } from './Parsing/Expression/MessageCall/receiver';
import { switchBranch, switchBranchElseStatement, switchExpression } from './Parsing/Expression/Switch/switchExpression';
import { unaryTypedIdentifier, untypedIdentifier } from './Parsing/identifiers';
import { anyLiteral, boolLiteral, integerLiteral, stringLiteral } from './Parsing/literals';
import { assignment } from './Parsing/Statements/assignment';
import { binaryMethodDeclaration, binaryMethodDeclarationArg } from './Parsing/Statements/MethodDeclaration/binary';
import { methodBody, methodBodyFull, methodBodyShort } from './Parsing/Statements/MethodDeclaration/body';
import { keywordMethodDeclaration, keywordMethodDeclarationArg, keywordMethodDeclarationArgs } from './Parsing/Statements/MethodDeclaration/keyword';
import { returnTypeDeclaration } from './Parsing/Statements/MethodDeclaration/returnTypeDeclaration';
import { unaryMethodDeclaration } from './Parsing/Statements/MethodDeclaration/unary';
import { returnStatement } from './Parsing/Statements/return';
import { statement, statements, switchStatement } from './Parsing/Statements/statements';
import { typeDeclaration, typedProperties, typedProperty } from './Parsing/Statements/typeDeclaration';


class State {
	isInMethodBody = false
	errors: NivaError[] = []
}

interface MessageCallInfo {
	callStack: string[]
}

interface UnaryMessageInfo {
	ast: UnaryMethodDeclaration,
	code: string
}

interface BinaryMethodInfo {
	ast: BinaryMethodDeclaration,
	code: string
}

interface KeywordMethodInfo {
	ast: KeywordMethodDeclaration,
	code: string
}


export interface TypeField {
	type: string
}
class TypeInfo {
	// field name to info
	constructor(public fields: Map<string, TypeField>) {}

	structural: boolean = false
	distinct: boolean = false

	unaryMessages: Map<string, UnaryMessageInfo> = new Map()
	binaryMessages: Map<string, BinaryMethodInfo> = new Map()
	keywordMessages: Map<string, KeywordMethodInfo> = new Map()
}

class TypesBD {
	private typeNameToInfo: Map<string, TypeInfo> = new Map()

	addNewType(name: string, fields: Map<string, TypeField>){

		const typeInfo = new TypeInfo(fields)
		this.typeNameToInfo.set(name, typeInfo)
		console.log("addNewType name = ", name, " fields = ", fields);	
	}



	alreadyHasType(name: string){
		return this.typeNameToInfo.has(name)
	}

	autoCompleteMessage(type: string, start: string): string{
		const x = this.typeNameToInfo.get(type)
		if (!x) return ""

		if (start === ""){
			const allFieldsAndMethodsName = ""
			return allFieldsAndMethodsName
		} else {
			const allFieldsAndMethodsStartWith = ""
			return allFieldsAndMethodsStartWith

		}

	}
}


export const typesBD: TypesBD = new TypesBD()

export const state = new State()


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
		methodDeclaration(_dash: TerminalNode, oneOfTheMessagesType) {
			return oneOfTheMessagesType.toAst();
		},
		unaryMethodDeclaration,

		// Binary
		binaryMethodDeclaration,
		binaryMethodDeclarationArg,

		// Keyword
		keywordMethodDeclaration,
		keywordMethodDeclarationArgs,
		keywordMethodDeclarationArg,

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

// console.log(JSON.stringify(generateNimCode('1 + 2'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 from: 2 to: 3'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas ses'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('1 sas + 2 sas'), undefined, 2) );
// console.log(JSON.stringify(generateNimCode('type Person name: string age: int'), undefined, 2));
// console.log(JSON.stringify(generateNimCode('-Person sas = [ x echo ]')[1], undefined, 2));
// console.log(JSON.stringify(generateNimCode('(1 + 2) echo.')[1], undefined, 2));
