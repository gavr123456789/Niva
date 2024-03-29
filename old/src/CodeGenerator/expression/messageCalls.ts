import {StatementList} from "../../AST_Nodes/AstNode";
import {BinaryArgument, KeywordArgument} from "../../AST_Nodes/Statements/Expressions/Messages/Message";
import {generateNimFromAst} from "../codeGenerator";
import {processExpression} from "./expression";
import {checkForGetter} from "./callLikeExpression";
import {processCollection} from "../collections";

export function generateUnaryCall(unaryMessageName: string, isGetter: boolean): string {
  return !isGetter ?
    '.' + '`' + unaryMessageName + '`' + '()' :
    '.' + unaryMessageName
}


export function generateBinaryCall(binaryMessageName: string, argument: BinaryArgument): string {
  switch (argument.value.kindStatement) {
    case "Primary":
      // example 1 + 1
      const argValue = argument.value.atomReceiver.value
      const code = generateSimpleBinaryCall(binaryMessageName, argument, argValue)
      return code
    case "BlockConstructor":
      throw new Error("BlockConstructor");

    case "BracketExpression":
      // example: 1 + (1 - 1)
      const expressionInBracketsCode = processExpression(argument.value, 0)
      const codeWithArgumentInBrackets = generateSimpleBinaryCall(binaryMessageName, argument, expressionInBracketsCode)
      return codeWithArgumentInBrackets
    case "ListLiteral":
    case "MapLiteral":
    case "SetLiteral":
      return  processCollection(argument.value)
    default:
      const _never: never = argument.value
      throw new Error("Sound error")
  }


}


function generateSimpleBinaryCall(binaryMessageName: string, argument: BinaryArgument, argValue: string) {

  // if there is no unary message then just return code
  if (!argument.unaryMessages) {
    return '.' + '`' + binaryMessageName + '`' + '(' + argValue + ')';
  }

  const receiver = argument.value

  const listOfUnaryCalls = argument.unaryMessages.map<string>((unaryMessageCall, i, array) => {
    const isThisGetter = checkForGetter(receiver, unaryMessageCall, array[i - 1])
    return generateUnaryCall(unaryMessageCall.name, isThisGetter)
  }).join("")
  const unaryCallsWithReceiver = argValue + listOfUnaryCalls
  return '.' + '`' + binaryMessageName + '`' + '(' + unaryCallsWithReceiver + ')'
}


export function generateKeywordCall(
  currentMethodName: string,
  keyWordArgs: KeywordArgument[],
  keywordMessageName: string,
  identation: number): string {


  const functionName = keywordMessageName
  const argsValuesCode: string[] = []


  const lastKeyWordArgBody = fillKeywordArgsAndReturnStatements(keyWordArgs, argsValuesCode, identation);
  const argumentsSeparatedByComma = argsValuesCode.join(", ")

  if (!lastKeyWordArgBody) {
    const keywordMessageCall = '.' + functionName + '(' + argumentsSeparatedByComma + ')'


    return keywordMessageCall
  } else {
    // one blockCode arg
    if (argsValuesCode.length === 1) {
      
      const keywordMessageCall = `.${functionName}():\n${lastKeyWordArgBody}`
      return keywordMessageCall
    }
    // .sas(1, 2):
    //   blockOfCode
    if (argsValuesCode.length > 1) {
      const allArgsExceptLast = argsValuesCode.slice(0, -1).join(", ")
      const keywordMessageCall = `.${functionName}(${allArgsExceptLast}):\n${lastKeyWordArgBody}`

      return keywordMessageCall
    }

    throw new Error(`Args count must be 1 or more, args: ${argsValuesCode}`);
  }

}

// цель заполнить массив аргументов args кодом для вычисления каждого аргумента из keyWordArgs
// there is a nice helper generateKeywordArgCalls() that generates all unary and binary message calls from kwArg
export function fillKeywordArgsAndReturnStatements(keyWordArgs: KeywordArgument[], args: string[], indentation: number): string | undefined {
  const keyWordArgsCount = keyWordArgs.length
  if(keyWordArgsCount === 0) {
    throw new Error("keyWordArgsCount cant be 0")
  }
  let lastKeyWordArgBody: undefined | string = undefined
  keyWordArgs.forEach((kwArg, i) => {
    switch (kwArg.receiver.kindStatement) {
      // Arg is just a simple thing(identifier or literal)
      case "Primary":
        const keyArgReceiverName = kwArg.receiver.atomReceiver.value
        const receiverVal = kwArg.receiver.atomReceiver.value + generateKeywordArgCalls(kwArg);

        args.push(receiverVal);
        break;

      // Arg is CodeBlock
      // do: [...]
      case "BlockConstructor":
        if (i !== keyWordArgsCount - 1) {
          throw new Error(`BlockConstructor cant be not last argument(arg# ${i + 1}, and the last is ${keyWordArgsCount})`);
        }

        const statementList: StatementList = {
          kind: "StatementList",
          statements: kwArg.receiver.statements
        };

        // проверить последний ли текущий аргумент, если да то присвоить
        const sas = generateNimFromAst(statementList, indentation + 2);
        const isLastArg = i === keyWordArgsCount - 1
        if (isLastArg){
          lastKeyWordArgBody = sas
        }
        args.push(sas)
        break;
      //key: (...)
      case "BracketExpression":
        const expression = processExpression(kwArg.receiver, indentation)
        if (!expression) {
          throw new Error(`error while process BracketExpression as KeywordMessageArgument: ${kwArg.keyName}`)
        }
        const result = expression + generateKeywordArgCalls(kwArg)
        args.push(result)
        break;
      case "SetLiteral":
      case "MapLiteral":
      case "ListLiteral":
        const collection = processCollection(kwArg.receiver)
        args.push(collection)
        break;

      default:
        const _never: never = kwArg.receiver;
        throw new Error("Sound error!");
    }

  });

  if (args.length === 0){
    throw new Error(`cant fill arguments of ${keyWordArgs.map(x => x.keyName)}`)
  }
  return lastKeyWordArgBody;
}

function generateKeywordArgCalls(kwArg: KeywordArgument) {
  let result = ""
  // if there any unary calls, call them first

  for (const [i, unaryMsg] of kwArg.unaryMessages.entries()) {
    // can be getter
    // self age: self age + 1
    const isThisGetter = checkForGetter(kwArg.receiver, unaryMsg, kwArg.unaryMessages[i - 1])
    const unaryCallCode = generateUnaryCall(unaryMsg.name, isThisGetter);

    result = result + unaryCallCode;
  }
  // if there are any binary calls, call them after
  for (const binaryMsg of kwArg.binaryMessages) {
    const binaryCallCode = generateBinaryCall(binaryMsg.name, binaryMsg.argument);
    result = result + binaryCallCode;
  }
  return result;
}
