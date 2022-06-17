import { Primary } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";

// If primary is not an expression
export function getAtomPrimary(primary: Primary): string {
	const atom = primary.atomReceiver;
	switch (atom.kindPrimary) {
		case 'Identifer':
		case 'int':
		case 'string':
		case 'bool':
			return atom.value;
		default:
			const _never: never = atom;
			
			throw new Error('SoundError');
	}
}