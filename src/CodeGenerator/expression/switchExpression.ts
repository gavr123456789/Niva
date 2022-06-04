import { SwitchBranch, SwitchExpression } from "../../AST_Nodes/Statements/Expressions/Expressions";
import { processExpression } from "./expression";

export function generateSwitchExpression(s: SwitchExpression): string {
	const switchExp = s;
	const branchesCode: string[] = [];

	// case -> thenDo branches
	// first must be if, others must be elif
	const firstBranch = switchExp.branches.at(0);
	if (firstBranch) {
		const { caseExpressionCode, thenDoExpressionCode } = generateBranch(firstBranch);
		const switchExpLine = `if ${caseExpressionCode}:\n${thenDoExpressionCode}`;
		branchesCode.push(switchExpLine);
		// remove first 
		switchExp.branches = switchExp.branches.slice(1);
	}

	// process others elif branches
	switchExp.branches.forEach(x => {
		const { caseExpressionCode, thenDoExpressionCode } = generateBranch(x);
		const elIfSwitchExpLine = `elif ${caseExpressionCode}:\n${thenDoExpressionCode}`;
		branchesCode.push(elIfSwitchExpLine);
	});
	// else branch
	if (switchExp.elseBranch) {
		if (switchExp.elseBranch.thenDoExpression.kindStatement === "SwitchExpression") {
			throw new Error("nested SwitchExpression doesnt support yet");
		}
		const elseExpressionLines: string[] = [];

		processExpression(switchExp.elseBranch.thenDoExpression, 2, elseExpressionLines);
		const elseExpressionCode = elseExpressionLines.at(0) ?? "";

		branchesCode.push(`else:\n${elseExpressionCode}`);
	}
	const switchExpResult = branchesCode.join("\n");
  return switchExpResult
}



function generateBranch(x: SwitchBranch) {
  if (x.caseExpression.kindStatement === "SwitchExpression") {
    //| | x > 5. => "sas" echo
    throw new Error("case expression cant be another switch expression");
  }
  if (x.thenDoExpression.kindStatement === "SwitchExpression") {
    throw new Error("nested SwitchExpression doesnt support yet");
  }

  const caseExpressionLines: string[] = [];
  const thenDoExpressionLines: string[] = [];

  processExpression(x.caseExpression, 0, caseExpressionLines);
  processExpression(x.thenDoExpression, 2, thenDoExpressionLines);

  const caseExpressionCode = caseExpressionLines.at(0) ?? "";
  const thenDoExpressionCode = thenDoExpressionLines.at(0) ?? "";
  return { caseExpressionCode, thenDoExpressionCode };
}
