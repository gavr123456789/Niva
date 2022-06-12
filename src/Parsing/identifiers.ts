import { NonterminalNode, TerminalNode } from "ohm-js"
import { Identifer } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier"

export function unaryTypedIdentifier(untypedIdentifier: NonterminalNode, _twoColons: TerminalNode, unaryType: NonterminalNode): Identifer {
  const result: Identifer = {
    kindPrimary: "Identifer",
    value: untypedIdentifier.sourceString,
    type: unaryType.sourceString
  }
  return result
}

export function untypedIdentifier(identifierName: NonterminalNode): Identifer {
  const result: Identifer = {
    kindPrimary: "Identifer",
    value: identifierName.sourceString,
  }
  return result
}

