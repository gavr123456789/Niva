import { CallLikeExpression } from "../../../CodeGenerator/expression/callLikeExpression";
import {KeywordArgument, KeywordMessage, MessageCall} from "./Messages/Message";
import { Receiver } from "./Receiver/Receiver";

export type Expression = 
| CallLikeExpression
| SwitchExpression



interface BaseMessageCallExpression {
	selfTypeName: string
	receiver: Receiver;
	messageCalls: MessageCall[];
	type?: string
}

export interface MessageCallExpression extends BaseMessageCallExpression {
  kindStatement: "MessageCallExpression" 
}
export interface Constructor {
	selfTypeName: string

	// Person name: "sas" age: 34
	type: string
	call: KeywordMessage,
  kindStatement: "Constructor"
}

export interface Getter {
	kindStatement: "Getter"

	selfTypeName: string

	// person name
	valueName: string
	fieldName: string

	// all except first
	messageCalls: MessageCall[];

}

// TODO добавить в иерархию
// person name: "sas"
// keyword сообщение с одним аргуменом
export interface Setter extends BaseMessageCallExpression {
	kindStatement: "Setter"

	valueName: string

	argument: KeywordArgument

}



export interface BracketExpression extends BaseMessageCallExpression {
	kindStatement: "BracketExpression"
}

export interface SwitchExpression  {
	kindStatement: "SwitchExpression"
	type?: string
	branches: SwitchBranch[]
  elseBranch?: ElseBranch
}

export interface SwitchBranch {
	caseExpressions: Expression[],
	thenDoExpression: Expression,
}

export interface ElseBranch{
	thenDoExpression: Expression,
}

export interface SwitchStatement{
	kindStatement: "SwitchStatement"
	switchExpression: SwitchExpression
	receiver: Receiver
}
