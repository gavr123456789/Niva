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
	keyName: string
	receiver: Receiver
	// TODO
	unaryMessages: UnaryMessage[]
	binaryMessages: BinaryMessage[]
}
export interface KeywordMessage {
	selectorKind: 'keyword';
	selectorName: string
	arguments: KeywordArgument[]
}
