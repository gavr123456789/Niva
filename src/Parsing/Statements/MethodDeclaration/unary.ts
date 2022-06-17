import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { MethodDeclaration, UnaryMethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";
import { newUnaryMessageInfo } from "../../../CodeDB/types";
import { codeDB, state } from "../../../niva";

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

  const extendableType = untypedIdentifier.sourceString
  const selectorName = unarySelector.sourceString
  state.enterMethodScope({
    forType: extendableType,
    kind: "unary",
    withName: selectorName
  })

  codeDB.addUnaryMessageForType(extendableType, selectorName, newUnaryMessageInfo())

  // TODO добавить в лексер обработку ноды мессадж кола и вынести туда, вместо того чтобы из каждой делать
  const bodyStatements: BodyStatements = methodBody.toAst();
  const returnType = returnTypeDeclaration.children.at(0)?.toAst()

  const isProc = _eq.sourceString === "="
  
  const unary: UnaryMethodDeclaration = {
    kind: isProc ? "proc" : "template",
    expandableType: extendableType,
    bodyStatements,
    methodKind: 'UnaryMethodDeclaration',
    name: selectorName,
    returnType
  };

  const result: MethodDeclaration = {
    kindStatement: "MethodDeclaration",
    method: unary
  }

  // sas.typeNameToInfo.set(expandableType, )

  return result;
}