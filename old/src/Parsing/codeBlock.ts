import { TerminalNode, NonterminalNode, IterationNode } from "ohm-js"
import { StatementList } from "../AST_Nodes/AstNode"
import { BlockConstructor } from "../AST_Nodes/Statements/Expressions/Receiver/BlockConstructor"

export function blockConstructor(_lsquare: TerminalNode, _s: NonterminalNode, blockBody: NonterminalNode, _s1: NonterminalNode, _rsquare: TerminalNode): BlockConstructor {
  const blockConstructor: BlockConstructor = blockBody.toAst()
  return blockConstructor
}

export function blockBody(s: IterationNode, _s: NonterminalNode, maybeStatements: IterationNode): BlockConstructor {
  const statements = maybeStatements.children.at(0)
  if (!statements) {
    throw new Error("Empty code block is useless");
  }

  const statementsNode: StatementList = statements.toAst()

  const blockArgList = s.children.at(0)
  const blockArguments = blockArgList ? blockArgList.children.map(x => x.toAst()) : []

  const result: BlockConstructor = {
    kindStatement: "BlockConstructor",
    blockArguments,
    statements: statementsNode.statements
  }

  return result
}