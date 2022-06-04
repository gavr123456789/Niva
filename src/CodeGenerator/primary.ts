import { Primary } from "../AST_Nodes/Statements/Expressions/Primary/Primary";

// If primary is not an expression
export function getAtomPrimary(primary: Primary): string {
	const atom = primary.atomReceiver;
	switch (atom.kindPrimary) {
		case 'Identifer':
			return atom.value;
		case 'IntLiteral':
			return atom.value;
		case 'StringLiteral':
			return atom.value;
		default:
			const _never: never = atom;
			console.log("!!! atom = ", atom);
			
			throw new Error('SoundError');
	}
}