import {StatementList} from "../AST_Nodes/AstNode"
import {
  BinaryMethodDeclaration,
  ConstructorDeclaration,
  KeywordMethodArgument, KeywordMethodDeclaration,
  MethodDeclaration,
  UnaryMethodDeclaration
} from "../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration"
import {Statement} from "../AST_Nodes/Statements/Statement"
import {codeDB} from "../niva"
import {generateNimFromAst} from "./codeGenerator"


interface CustomMethodParameters {
  customName?: string,

}

function generateUnary(
  m: UnaryMethodDeclaration,
  unaryName: string,
  ident: string,
  procOrTemplate: "proc" | "template",
  expandableType: string,
  returnType: string,
  methodBody: string,
  isConstructorDeclaration: boolean
): string {

  const isSelfMutatingUnary: boolean = codeDB.hasMutateEffect(m.expandableType, m.name)
  const selfArg = !isConstructorDeclaration? `self: ${isSelfMutatingUnary ? "var " : ""}${expandableType}`: ""
  const result = `${ident}${procOrTemplate} ${unaryName}(${selfArg}): ${returnType} =\n${methodBody}`

  return result
}

function generateBinary(
  ident: string,
  name: string,
  procOrTemplate: "proc" | "template",
  m: BinaryMethodDeclaration,
  isSelfMutatingBinary: boolean,
  expandableType: string,
  argumentType: string,
  returnType: string,
  methodBody: string
): string {
  return `${ident}${procOrTemplate} \`${name}\`(self: ${isSelfMutatingBinary ? "var " : ""}${expandableType}, ${m.argument.value}: ${argumentType}): ${returnType} =\n${methodBody}`
}

function generateKeyword(
  m: KeywordMethodDeclaration,
  keywordProcName: string,
  expandableType: string,
  ident: string,
  procOrTemplate: "proc" | "template",
  returnType: string,
  methodBody: string,
  isConstructorDeclaration: boolean
): string {
  const keyArgs = m.keyValueNames.map(y => generateKeywordMethodArg(y)).join(", ")

  const isSelfMutatingKeyword: boolean = codeDB.hasMutateEffect(m.expandableType, keywordProcName)
  // constructor doesn't need a self
  const selfArg = !isConstructorDeclaration? `self: ${isSelfMutatingKeyword ? "var " : ""}${expandableType}, `: ""
  // const selfArg = !isConstructorDeclaration? `self: var ${expandableType}, `: ""

  const args = `${selfArg}${keyArgs}`
  return `${ident}${procOrTemplate} ${keywordProcName}(${args}): ${returnType} =\n${methodBody}`
}

function getConstructName(isConstructorDeclaration: boolean, name: string, expandableType: string): string {
  return isConstructorDeclaration ? "construct_" + expandableType +"_" + name : name;
}

export function generateMethodDeclaration(methodDec: MethodDeclaration | ConstructorDeclaration, indentation: number): string {
  const {bodyStatements, expandableType, kind} = methodDec.method
  const {statements} = bodyStatements
  const {method: m} = methodDec

  const returnType = m.returnType ? m.returnType : "auto"
  const methodBody = generateMethodBody(statements, indentation + 2)
  const ident = " ".repeat(indentation)

  const procOrTemplate = kind

  const isConstructorDeclaration = methodDec.kindStatement === "ConstructorDeclaration"
  // TODO refactor
  switch (m.methodKind) {
    case "UnaryMethodDeclaration":
      const unaryName = getConstructName(isConstructorDeclaration, m.name, expandableType)
      return generateUnary(m, unaryName, ident, procOrTemplate, expandableType, returnType, methodBody, isConstructorDeclaration);

    case "BinaryMethodDeclaration":
      const argumentType = m.argument.type ?? "auto"
      const isSelfMutatingBinary: boolean = codeDB.hasMutateEffect(m.expandableType, m.name)
      const binaryName = getConstructName(isConstructorDeclaration, m.name, expandableType)

      return generateBinary(ident, binaryName,procOrTemplate, m, isSelfMutatingBinary, expandableType, argumentType, returnType, methodBody);

    case "KeywordMethodDeclaration":
      // from_to
      const keywordProcName = getConstructName(isConstructorDeclaration, m.name, expandableType)
      return generateKeyword(m, keywordProcName, expandableType, ident, procOrTemplate, returnType, methodBody, isConstructorDeclaration);


    default:
      const _never: never = m
      throw new Error("SoundError");
  }
}


// level - degree of nesting of the code
function generateMethodBody(statements: Statement[], identation: number): string {
  const statementList: StatementList = {
    kind: "StatementList",
    statements
  }

  return generateNimFromAst(statementList, identation)
}


function generateKeywordMethodArg(keywordMethodArgument: KeywordMethodArgument): string {
  if (keywordMethodArgument.identifier.type) {
    // const getter = generateGetter(x.identifier.value, x.identifier.type)
    return `${keywordMethodArgument.identifier.value}: ${keywordMethodArgument.identifier.type}`
  } else {
    return `${keywordMethodArgument.identifier.value}: auto`
  }
}

// function generateGetter(value: string, type: string): string {

// }
