import {IterationNode, NonterminalNode, TerminalNode} from "ohm-js"
import { Identifier } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier"

export function  identifier(maybeModule: IterationNode, identNode: NonterminalNode): Identifier {
  const ident: Identifier = identNode.toAst()


  const moduleNameNode = maybeModule.children.at(0)
  if (moduleNameNode){
    const moduleName: string = moduleNameNode.toAst()
    ident.moduleName = moduleName
  }

  return ident
}

export function moduleName(name: NonterminalNode, dot: TerminalNode): string{
  return name.sourceString
}

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



