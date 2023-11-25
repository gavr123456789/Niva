import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js"
import { StatementList } from "../../../AST_Nodes/AstNode"
import { BodyStatements, Statement } from "../../../AST_Nodes/Statements/Statement"
import { state } from "../../../niva"

export function methodBody(fullOrShort: NonterminalNode): BodyStatements {
  state.isInMethodBody = true
  const body: BodyStatements = fullOrShort.toAst()
  return body
}

export function methodBodyFull(_openBracket: TerminalNode, _s1: NonterminalNode, statements: IterationNode, _s2: NonterminalNode, _closeBracket: TerminalNode): BodyStatements {
  const child = statements.children.at(0)
  if (statements.children.length !== 1 || !child) {
    throw new Error("statements node must have one child");
  }
  const statementsList: StatementList = child.toAst()

  const bodyStatements: BodyStatements = {
    statements: statementsList.statements,
  }

  return bodyStatements
}

export function methodBodyShort(statementNode: NonterminalNode): StatementList {
  const statement: Statement = statementNode.toAst()
  const statementList: StatementList = {
    kind: "StatementList",
    statements: [statement]
  }
  return statementList
}
