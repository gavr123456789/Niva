import { NonterminalNode, TerminalNode } from "ohm-js"
import { Identifier } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier"

export function unaryTypedIdentifier(untypedIdentifier: NonterminalNode, _twoColons: TerminalNode, unaryType: NonterminalNode): Identifier {
  const result: Identifier = {
    kindPrimary: "Identifier",
    value: untypedIdentifier.sourceString,
    type: unaryType.sourceString
  }
  return result
}

export function untypedIdentifier(identifierName: NonterminalNode): Identifier {
  const result: Identifier = {
    kindPrimary: "Identifier",
    value: identifierName.sourceString,
  }
  return result
}



