import { Receiver } from "../Receiver/Receiver";

export type MessageCall = UnaryMessage | BinaryMessage | KeywordMessage;

export interface UnaryMessage {
	selectorKind: 'unary';
	unarySelector: string;
}

export interface BinaryArgument {
	value: Receiver // x | 
	unaryMessages?: UnaryMessage[] // x + 5 sas
}

export interface BinaryMessage {
	selectorKind: 'binary';
	binarySelector: string;
  argument: BinaryArgument
}

export interface KeywordArgument {
	// from: 10
	// ident: value
	ident: string
	value: string // TODO
}
export interface KeywordMessage {
	selectorKind: 'keyword';
	arguments: KeywordArgument[]
}
