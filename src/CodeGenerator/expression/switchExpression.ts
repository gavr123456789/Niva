import { ElseBranch, SwitchBranch, SwitchExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { processExpression } from "./expression";

export function generateSwitchExpression(switchExp: SwitchExpression, identation: number): string {
	const branchesCode: string[] = [];
	const ident = " ".repeat(identation) 
	

	// case -> thenDo branches
	// first must be if, others must be elif
	const firstBranch = switchExp.branches.at(0);
	if (firstBranch) {
		const switchExpLine = generateBranchExpressions2("if", firstBranch, identation)

		branchesCode.push(switchExpLine);
		// remove first 
		switchExp.branches = switchExp.branches.slice(1);
	}

	// process others elif branches
	switchExp.branches.forEach(x => {
		const elIfSwitchExpLine = generateBranchExpressions2("elif",x, identation)
		branchesCode.push(elIfSwitchExpLine);
	});
	// else branch
	if (switchExp.elseBranch) {
		// if (switchExp.elseBranch.thenDoExpression.kindStatement === "SwitchExpression") {
		// 	throw new Error("nested SwitchExpression doesnt support yet");
		// }
		
		// const elseExpressionCode = processExpression(switchExp.elseBranch.thenDoExpression, identation + 2);
		const elseExpressionCode = generateElseBranchExpression(switchExp.elseBranch, ident, identation)
		branchesCode.push(elseExpressionCode);
	}
	const switchExpResult = branchesCode.join("\n");
  return switchExpResult
}



// 
export function generateBranchExpressions2(switchKind: "if" | "elif" | "of", x: SwitchBranch, identation: number) {
	if (x.thenDoExpression.kindStatement === "SwitchExpression") {
		throw new Error("nested SwitchExpression doesnt support yet");
	}

	const ident = " ".repeat(identation) 

		if (x.caseExpression.kindStatement === "SwitchExpression") {
			//| | x > 5. => "sas" echo
			throw new Error("case expression cant be another switch expression");
		}
	
		// if   x < 2 // identation not needed after if
		const caseExpressionCode = processExpression(x.caseExpression, 0);
		const thenDoExpressionCode = processExpression(x.thenDoExpression, identation + 2);
	
		const result = `${ident}${switchKind} ${caseExpressionCode}:\n${thenDoExpressionCode}`;
		return result
}

export function generateElseBranchExpression(x: ElseBranch, rootIdent: string, identation: number): string{
	if (x.thenDoExpression.kindStatement === "SwitchExpression") {
		throw new Error("nested SwitchExpression doesnt support yet");
	}
	
	const elseExpressionCode = processExpression(x.thenDoExpression, identation + 2);
	

	const result = (`${rootIdent}else:\n${elseExpressionCode}`);
	return result
}
//

