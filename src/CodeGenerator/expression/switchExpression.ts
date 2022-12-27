import {
  Constructor,
  ElseBranch,
  SwitchBranch,
  SwitchExpression
} from '../../AST_Nodes/Statements/Expressions/Expressions';
import {MessageSendExpression, processExpression} from './expression';
import {generateCallLikeExpression} from "./callLikeExpression";

export function generateSwitchExpression(switchExp: SwitchExpression, identation: number): string {
  const branchesCode: string[] = [];
  const ident = ' '.repeat(identation);

  // case -> thenDo branches
  // first must be if, others must be elif
  const firstBranch = switchExp.branches.at(0);
  if (firstBranch) {
    const switchExpLine = generateBranchExpressions('if', firstBranch, identation);

    branchesCode.push(switchExpLine);
    // remove first
    switchExp.branches = switchExp.branches.slice(1);
  }

  // process others elif branches
  switchExp.branches.forEach((x) => {
    const elIfSwitchExpLine = generateBranchExpressions('elif', x, identation);
    branchesCode.push(elIfSwitchExpLine);
  });
  // else branch
  if (switchExp.elseBranch) {
    // if (switchExp.elseBranch.thenDoExpression.kindStatement === "SwitchExpression") {
    // 	throw new Error("nested SwitchExpression doesnt support yet");
    // }

    // const elseExpressionCode = processExpression(switchExp.elseBranch.thenDoExpression, identation + 2);
    const elseExpressionCode = generateElseBranchExpression(switchExp.elseBranch, ident, identation);
    branchesCode.push(elseExpressionCode);
  }
  const switchExpResult = branchesCode.join('\n');
  return switchExpResult;
}

function processManyExpressions(s: MessageSendExpression[], identation: number): string[] {
  return s.map(x => processExpression(x, identation))
}

export function generateBranchExpressions(switchKind: 'if' | 'elif' | 'of', x: SwitchBranch, identation: number): string {
  if (x.thenDoExpression.kindStatement === 'SwitchExpression') {
    throw new Error('nested SwitchExpression doesnt support yet');
  }

  const ident = ' '.repeat(identation);
  const firstCaseExpression = x.caseExpressions.at(0)

  // Switch expression cant get here because of check in lexer
  const caseExpressions = x.caseExpressions as MessageSendExpression[]
  const thenDoExpressionCode = generateCallLikeExpression(x.thenDoExpression, identation + 2);

  if (firstCaseExpression && firstCaseExpression.kindStatement === "Constructor") {
    const notConstructorBranch = x.caseExpressions.find(x => {return x.kindStatement !== "Constructor"})
    if (notConstructorBranch) {
      throw new Error(`If you match against union branches, all the branches must be Constructors names, ${notConstructorBranch}`)
    }
    // Значит это case по union
    console.log("case switch union")
    const manyOrOneConstructors = x.caseExpressions as Constructor[]
    const manyOrOneConstructorsCode = manyOrOneConstructors.map(x => x.type).join(switchKind === "of" ? ", " : " or ")
    const result = `${ident}${switchKind} ${manyOrOneConstructorsCode}:\n${thenDoExpressionCode}`;
    console.log("branch ", result)
    return result;
  }


  // if   x < 2 // identation not needed after if
  const manyExpressions = processManyExpressions(caseExpressions, 0)
  const caseExpressionCode = manyExpressions.join(switchKind === "of" ? ", " : " or ");// switch statement delimiter is ,



  // Example:
  // of "":
  //   echo "sas"
  const result = `${ident}${switchKind} ${caseExpressionCode}:\n${thenDoExpressionCode}`;
  return result;
}

export function generateElseBranchExpression(x: ElseBranch, rootIdent: string, identation: number): string {
  if (x.thenDoExpression.kindStatement === 'SwitchExpression') {
    throw new Error('nested SwitchExpression doesnt support yet');
  }

  const elseExpressionCode = generateCallLikeExpression(x.thenDoExpression, identation + 2);

  const result = `${rootIdent}else:\n${elseExpressionCode}`;
  return result;
}

//
