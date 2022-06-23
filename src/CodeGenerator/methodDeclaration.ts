import {StatementList} from "../AST_Nodes/AstNode"
import {KeywordMethodArgument, MethodDeclaration} from "../AST_Nodes/Statements/MethodDeclaration/MethodDeclaration"
import {Statement} from "../AST_Nodes/Statements/Statement"
import {codeDB} from "../niva"
import {generateNimFromAst} from "./codeGenerator"


export function generateMethodDeclaration(methodDec: MethodDeclaration, identation: number): string {
  const {bodyStatements, expandableType, kind} = methodDec.method
  const {statements} = bodyStatements
  const {method: m} = methodDec

  const returnType = m.returnType ? m.returnType : "auto"
  const methodBody = generateMethodBody(statements, identation + 2)
  const ident = " ".repeat(identation)

  const procOrTemplate = kind



  // TODO refactor
  switch (m.methodKind) {
    case "UnaryMethodDeclaration":
      const isSelfMutatingUnary: boolean = codeDB.hasMutateEffect(m.expandableType, m.name)
      return `${ident}${procOrTemplate} ${m.name}(self: ${isSelfMutatingUnary ? "var " : ""}${expandableType}): ${returnType} =\n${methodBody}`

    case "BinaryMethodDeclaration":
      const argumentType = m.argument.type ?? "auto"
      const isSelfMutatingBinary: boolean = codeDB.hasMutateEffect(m.expandableType, m.binarySelector)

      return `${ident}${procOrTemplate} \`${m.binarySelector}\`(self: ${isSelfMutatingBinary ? "var " : ""}${expandableType}, ${m.argument.value}: ${argumentType}): ${returnType} =\n${methodBody}`

    case "KeywordMethodDeclaration":
      const keyArgs = m.keyValueNames.map(y => generateKeywordMethodArg(y)).join(", ")
      // from_to

      const keywordProcName = m.keyValueNames.map(y => y.keyName).join("_")
      const isSelfMutatingKeyword: boolean = codeDB.hasMutateEffect(m.expandableType, keywordProcName)

      const args = `self: ${isSelfMutatingKeyword ? "var " : ""}${expandableType}, ${keyArgs}`
      return `${ident}${procOrTemplate} ${keywordProcName}(${args}): ${returnType} =\n${methodBody}`


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


function generateKeywordMethodArg(x: KeywordMethodArgument): string {
  if (x.identifier.type) {
    // const getter = generateGetter(x.identifier.value, x.identifier.type)
    return `${x.identifier.value}: ${x.identifier.type}`
  } else {
    return `${x.identifier.value}: auto`
  }
}

// function generateGetter(value: string, type: string): string {

// }
