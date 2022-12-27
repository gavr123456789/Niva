import { Receiver } from "../Receiver/Receiver";
import {ContextInformation} from "../../../../niva";

export type MessageCall = UnaryMessage | BinaryMessage | KeywordMessage;

export interface MessageType {
	name: string
	generic?: string
}

export interface UnaryMessage {
	selectorKind: 'unary';
	name: string;
	insideMethod: ContextInformation
	type: MessageType
}

export interface BinaryArgument {
	value: Receiver // x | 
	unaryMessages?: UnaryMessage[]
}

export interface BinaryMessage {
	selectorKind: 'binary';
	name: string;
  argument: BinaryArgument
	type: MessageType

}

export interface KeywordArgument {
	keyName: string
	receiver: Receiver
	unaryMessages: UnaryMessage[]
	binaryMessages: BinaryMessage[]
}

export interface KeywordMessage {
	selectorKind: 'keyword';
	name: string
	arguments: KeywordArgument[]
	type: MessageType
}
