import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { Identifier } from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";
import { BinaryMethodDeclaration, BinaryMethodDeclarationArg, MethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";
import { newBinaryMethodInfo } from "../../../CodeDB/types";
import { codeDB, state } from "../../../niva";

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

  const binarySelector: BinaryMethodDeclarationArg = binaryMethodDeclarationArg.toAst();
  const extendableType = untypedIdentifier.sourceString
  const selectorName = binarySelector.binarySelector
  const returnType = returnTypeDeclaration.children.at(0)?.toAst()

  // set state 
  state.enterMethodScope({
    forType: extendableType,
    kind: "binary",
    withName: selectorName
  })

  codeDB.addBinaryMessageForType(extendableType, selectorName, newBinaryMethodInfo(returnType || "auto"))
  codeDB.setTypedValueToMethodScope(state.insideMessage, "self", extendableType)

  //

  const bodyStatements: BodyStatements = methodBody.toAst();
  const isProc = _eq.sourceString === "="
  
  const binary: BinaryMethodDeclaration = {
    kind: isProc ? "proc" : "template",
    methodKind: "BinaryMethodDeclaration",
    expandableType: extendableType,
    returnType,
    binarySelector: selectorName,
    argument: binarySelector.identifier,
    bodyStatements,
  }

  const result: MethodDeclaration = {
    kindStatement: "MethodDeclaration",
    method: binary
  }
  state.exitFromMethodDeclaration()

  return result;
}

export function binaryMethodDeclarationArg(binarySelector: NonterminalNode, _s: NonterminalNode, identifier: NonterminalNode): BinaryMethodDeclarationArg {
  const ident: Identifier = identifier.toAst()
  const result: BinaryMethodDeclarationArg = {
    binarySelector: binarySelector.sourceString,
    identifier: ident
  }
  return result
}