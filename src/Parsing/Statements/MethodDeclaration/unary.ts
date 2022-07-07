import {IterationNode, NonterminalNode, TerminalNode} from "ohm-js";
import {
  MethodDeclaration,
  UnaryMethodDeclaration
} from "../../../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration";
import {BodyStatements} from "../../../AST_Nodes/Statements/Statement";
import {newBinaryMethodInfo, newUnaryMethodInfo} from "../../../CodeDB/types";
import {codeDB, state} from "../../../niva";
import {inferStatementType} from "../../../CodeDB/InferTypes/inferStatementType";

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
  const isProc = _eq.sourceString === "="
  const extendableType = untypedIdentifier.sourceString
  const selectorName = unarySelector.sourceString
  state.enterMethodScope({
    forType: extendableType,
    kind: "unary",
    withName: selectorName
  })
  let returnType: string | undefined = returnTypeDeclaration.children.at(0)?.toAst()


  codeDB.addUnaryMessageForType(extendableType, selectorName, newUnaryMethodInfo(returnType || "auto"))
  codeDB.setTypedValueToMethodScope(state.insideMessage, "self", extendableType)


  const bodyStatements: BodyStatements = methodBody.toAst();



  // Infer return type
  if (!returnType){
    const lastBodyStatement = bodyStatements.statements.at(-1)
    if (lastBodyStatement){
      returnType = inferStatementType(lastBodyStatement);
      if (!returnType){
        console.log("inferred return type of ", selectorName, " is auto")
        throw new Error(`cant infer return type of ${selectorName}`)
      } else {
        console.log("inferred return type of ", selectorName, " is ", returnType)
      }
      codeDB.addUnaryMessageForType(extendableType, selectorName, newUnaryMethodInfo(returnType))
    }
  }

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



  state.exitFromMethodDeclaration()

  return result;
}