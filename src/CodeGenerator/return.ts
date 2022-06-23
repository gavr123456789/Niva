import {ReturnStatement} from "../AST_Nodes/Statements/Statement";
import {generateSwitchExpression} from "./expression/switchExpression";
import {generateCallLikeExpression} from "./expression/callLikeExpression";

export function generateReturn(s: ReturnStatement, identation: number): string {
  const ident = " ".repeat(identation)
  if (s.value.kindStatement === "SwitchExpression") {
    const switchCode = generateSwitchExpression(s.value, 0)
    return `${ident}return ${switchCode}`
  }

  const exprCode = generateCallLikeExpression(s.value, 0)
  return `${ident}return ${exprCode}`
}