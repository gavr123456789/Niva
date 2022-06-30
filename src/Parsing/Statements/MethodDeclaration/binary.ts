import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js";
import { Identifier } from "../../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";
import { BinaryMethodDeclaration, BinaryMethodDeclarationArg, MethodDeclaration } from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import { BodyStatements } from "../../../AST_Nodes/Statements/Statement";
import { newBinaryMethodInfo } from "../../../CodeDB/types";
import { codeDB, state } from "../../../niva";
import {getStatementType} from "../../../CodeDB/InferTypes/getStatementType";

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
  const argType = binarySelector.identifier.type
  const argName = binarySelector.identifier.value
  let returnType = returnTypeDeclaration.children.at(0)?.toAst()

  // set state 
  state.enterMethodScope({
    forType: extendableType,
    kind: "binary",
    withName: selectorName + "::" + argType ?? "auto"
  })

  // this must be done before ast because inside ast there will be a calls to know this method's
  // variables type
  // we dont know real return type at that point
  // we will know it after ast with the last statement type


  codeDB.addBinaryMessageForType(extendableType, selectorName + "::" + argType ?? "auto", newBinaryMethodInfo(returnType || "auto"))
  codeDB.setTypedValueToMethodScope(state.insideMessage, "self", extendableType)

  if(argType)
    codeDB.setTypedValueToMethodScope(state.insideMessage, argName, argType)
  else {
    console.log("binary selector = ", binarySelector)
    codeDB.needInferBinaryMethodTypeLater(extendableType, selectorName)
    // throw new Error("binary arg doesnt have type")
  }


  const bodyStatements: BodyStatements = methodBody.toAst();
  const isProc = _eq.sourceString === "="



  // Infer return type
  if (!returnType){
    const lastBodyStatement = bodyStatements.statements.at(-1)
    if (lastBodyStatement){
      returnType = getStatementType(lastBodyStatement);
      if(!returnType){
        // throw new Error("no return type")
      }
      console.log("inferred return type of ", selectorName + "::" + argType ?? "auto", " is ", returnType)
      // need to change return type in codeDB because now we know the type of last statement
      codeDB.addBinaryMessageForType(extendableType, selectorName + "::" + argType ?? "auto", newBinaryMethodInfo(returnType))
    }
  }


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