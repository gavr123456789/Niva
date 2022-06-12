import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { Identifer } from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";
import { BinaryMethodDeclaration, BinaryMethodDeclarationArg, MethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";

export function binaryMethodDeclaration(
  untypedIdentifier: NonterminalNode,
  _s: NonterminalNode,
  binaryMethodDeclarationArg: NonterminalNode,
  _s2: NonterminalNode,
  returnTypeDeclaration: IterationNode,
  _s3: NonterminalNode,
  _eq: TerminalNode,
  _s4: NonterminalNode,
  methodBody: NonterminalNode): MethodDeclaration {
  const bodyStatements: BodyStatements = methodBody.toAst();
  const returnType = returnTypeDeclaration.children.at(0)?.toAst()
  const binarySelector: BinaryMethodDeclarationArg = binaryMethodDeclarationArg.toAst();
  const expandableType = untypedIdentifier.sourceString
  const isProc = _eq.sourceString === "="


  const binary: BinaryMethodDeclaration = {
    kind: isProc ? "proc" : "template",
    methodKind: "BinaryMethodDeclaration",
    expandableType,
    returnType,
    binarySelector: binarySelector.binarySelector,
    identifier: binarySelector.identifier,
    bodyStatements,
  }

  const result: MethodDeclaration = {
    kindStatement: "MethodDeclaration",
    method: binary
  }

  return result;
}

export function binaryMethodDeclarationArg(binarySelector: NonterminalNode, _s: NonterminalNode, identifier: NonterminalNode): BinaryMethodDeclarationArg {
  const ident: Identifer = identifier.toAst()
  const result: BinaryMethodDeclarationArg = {
    binarySelector: binarySelector.sourceString,
    identifier: ident
  }
  return result
}