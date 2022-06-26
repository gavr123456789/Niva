import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { Identifier } from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";
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

  codeDB.addKeywordMessageForType(extendableType, selectorName, newKeywordMethodInfo(returnType || "auto"))
  codeDB.setTypedValueToMethodScope(state.insideMessage, "self", extendableType)


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

  state.exitFromMethodDeclaration()
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

// identifier typed or untyped
// localNameKeywordArg typed or untyped
export function keywordMethodDeclarationArg(identifier: NonterminalNode, colon: TerminalNode, localNameKeywordArg: IterationNode): KeywordMethodArgument {
  const ident: Identifier = identifier.toAst();
  const localName: Identifier | undefined = localNameKeywordArg.children.at(0)?.toAst()

  // Теперь тайп есть либо на первом либо на втором
  // from x::int = []
  // from::int = []
  // if first has type, then must be no localNameKeywordArg
  if (localName){
    // from x::int = []
    if (ident.type && localName.type){
      // !! from::int x::int = []
      throw new Error("you must specify type only once for keyword argument")
    }

    // console.log("has local name")
    // console.log("result = ",{
    //   identifier: localName,
    //   keyName: ident.value
    // } )
    return {
      identifier: localName,
      keyName: ident.value
    }

  } else {
    // from:int = []
    // console.log("no local name")
    // console.log("result = ",{
    //   keyName: ident.value,
    //   identifier: ident,
    // } )
    return {
      keyName: ident.value,
      identifier: ident,
    }
  }
}

export function localNameKeywordArg( _s: NonterminalNode, ident: NonterminalNode): Identifier {
  const identifier: Identifier = ident.toAst()
  return identifier
}

