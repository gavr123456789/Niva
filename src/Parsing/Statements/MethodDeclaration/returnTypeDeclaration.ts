import { NonterminalNode } from "ohm-js";

export function returnTypeDeclaration(_returnTypeOperator: NonterminalNode, _s: NonterminalNode, untypedIdentifier: NonterminalNode): string {
  return untypedIdentifier.sourceString;
}