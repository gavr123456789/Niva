import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { Identifer } from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";
import { KeywordMethodArgument, KeywordMethodDeclaration, KeywordMethodDeclarationArg, MethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";
import { newKeywordMethodInfo } from "../../../CodeDB/types";
import { codeDB, state } from "../../../niva";

export function keywordMethodDeclaration(
  untypedIdentifier: NonterminalNode,
  keywordMethodDeclarationArgs: NonterminalNode,
  _s: NonterminalNode,
  returnTypeDeclaration: IterationNode,
  _s2: NonterminalNode,
  _eq: TerminalNode,
  _s3: NonterminalNode,
  methodBody: NonterminalNode
): MethodDeclaration {
  const returnType = returnTypeDeclaration.children.at(0)?.toAst()
  const extendableType = untypedIdentifier.sourceString
  const keywordMethodDeclarationArg: KeywordMethodDeclarationArg = keywordMethodDeclarationArgs.toAst()
  const keyValueNames: KeywordMethodArgument[] = keywordMethodDeclarationArg.keyValueNames
  const isProc = _eq.sourceString === "="

  const selectorName = keywordMethodDeclarationArg.keyValueNames.map(x => x.keyName).join("_")
  
  state.enterMethodScope({
    forType: extendableType,
    kind: "keyword",
    withName: selectorName
  })

  codeDB.addKeywordMessageForType(extendableType, selectorName, newKeywordMethodInfo())
  

  const bodyStatements: BodyStatements = methodBody.toAst();
  const keyword: KeywordMethodDeclaration = {
    kind: isProc ? "proc" : "template",
    methodKind: "KeywordMethodDeclaration",
    returnType,
    bodyStatements,
    expandableType: extendableType,
    keyValueNames
  }

  const result: MethodDeclaration = {
    kindStatement: "MethodDeclaration",
    method: keyword
  }

  
  return result

}

export function keywordMethodDeclarationArgs(_s: NonterminalNode, keywordMethodDeclarationArg: NonterminalNode, _s2: IterationNode, otherKeywordMethodDeclarationArg: IterationNode, _s3: IterationNode): KeywordMethodDeclarationArg {
  const firstKeyVal: KeywordMethodArgument = keywordMethodDeclarationArg.toAst()
  const otherKeyVals: KeywordMethodArgument[] = otherKeywordMethodDeclarationArg.children.map(x => x.toAst())
  const keyValueNames: KeywordMethodArgument[] = [firstKeyVal, ...otherKeyVals]

  const result: KeywordMethodDeclarationArg = {
    keyValueNames
  }
  return result
}

export function keywordMethodDeclarationArg(untypedIdentifier: NonterminalNode, _colon: TerminalNode, _s: NonterminalNode, identifier: NonterminalNode): KeywordMethodArgument {
  const ident: Identifer = identifier.toAst();

  return {
    keyName: untypedIdentifier.sourceString,
    identifier: ident
  }
}