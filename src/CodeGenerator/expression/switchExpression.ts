import { SwitchBranch, SwitchExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { processExpression } from "./expression";

export function generateSwitchExpression(switchExp: SwitchExpression, identation: number): string {
	const branchesCode: string[] = [];
	const ident = " ".repeat(identation) 
	

	// case -> thenDo branches
	// first must be if, others must be elif
	const firstBranch = switchExp.branches.at(0);
	if (firstBranch) {
		const { caseExpressionCode, thenDoExpressionCode } = generateBranch(firstBranch, identation + 2);
		const switchExpLine = `${ident}if ${caseExpressionCode}:\n${thenDoExpressionCode}`;
		branchesCode.push(switchExpLine);
		// remove first 
		switchExp.branches = switchExp.branches.slice(1);
	}

	// process others elif branches
	switchExp.branches.forEach(x => {
		const { caseExpressionCode, thenDoExpressionCode } = generateBranch(x, identation + 2);
		const elIfSwitchExpLine = `${ident}elif ${caseExpressionCode}:\n${thenDoExpressionCode}`;
		branchesCode.push(elIfSwitchExpLine);
	});
	// else branch
	if (switchExp.elseBranch) {
		if (switchExp.elseBranch.thenDoExpression.kindStatement === "SwitchExpression") {
			throw new Error("nested SwitchExpression doesnt support yet");
		}
		console.log("else branch ident + 2 = ", identation + 2);
		
		const elseExpressionCode = processExpression(switchExp.elseBranch.thenDoExpression, identation + 2);
		console.log("else expression =", JSON.stringify(elseExpressionCode) );
		
		branchesCode.push(`${ident}else:\n${elseExpressionCode}`);
	}
	const switchExpResult = branchesCode.join("\n");
  return switchExpResult
}



function generateBranch(x: SwitchBranch, identation: number) {
  if (x.caseExpression.kindStatement === "SwitchExpression") {
    //| | x > 5. => "sas" echo
    throw new Error("case expression cant be another switch expression");
  }
  if (x.thenDoExpression.kindStatement === "SwitchExpression") {
    throw new Error("nested SwitchExpression doesnt support yet");
  }

	// if   x < 2 // identation not needed after if
  const caseExpressionCode = processExpression(x.caseExpression, 0);
  const thenDoExpressionCode = processExpression(x.thenDoExpression, identation);

  return { caseExpressionCode, thenDoExpressionCode };
}
