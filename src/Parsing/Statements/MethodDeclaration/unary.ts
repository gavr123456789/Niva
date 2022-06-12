import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { MethodDeclaration, UnaryMethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";
import { typesBD } from "../../../niva";

export function unaryMethodDeclaration(
  untypedIdentifier: NonterminalNode,
  _s: NonterminalNode,
  unarySelector: NonterminalNode,
  _s2: NonterminalNode,
  returnTypeDeclaration: IterationNode,
  _s3: NonterminalNode,
  _eq: TerminalNode,
  _s4: NonterminalNode,
  methodBody: NonterminalNode
): MethodDeclaration {
  // -Person sas = []
  const bodyStatements: BodyStatements = methodBody.toAst();
  const returnType = returnTypeDeclaration.children.at(0)?.toAst()
  const expandableType = untypedIdentifier.sourceString
  const isProc = _eq.sourceString === "="



  const unary: UnaryMethodDeclaration = {
    kind: isProc ? "proc" : "template",
    expandableType,
    bodyStatements,
    methodKind: 'UnaryMethodDeclaration',
    name: unarySelector.sourceString,
    returnType
  };

  const result: MethodDeclaration = {
    kindStatement: "MethodDeclaration",
    method: unary
  }

  // sas.typeNameToInfo.set(expandableType, )

  return result;
}