import {processExpression} from './expression/expression';
import {generateSwitchExpression} from './expression/switchExpression';
import {generateConstructor, generateCustomConstructor} from "./expression/constructor";
import {generateSetter} from "./expression/setter";
import {Assignment} from "../AST_Nodes/Statements/Statement";
import {
  ListLiteral,
  MapLiteral,
  SetLiteral
} from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/CollectionLiteral";
import {generateListLiteral, generateMapLiteral} from "./collections";


export function generateAssigment(assignment: Assignment, indentation: number): string {
  const {assignmentTarget, to, type} = assignment
  const ident = ' '.repeat(indentation);

  switch (to.kindStatement) {
    case 'BracketExpression':
    case 'MessageCallExpression':

      switch (to.receiver.kindStatement) {
        case "Primary":
          break;
        case "BlockConstructor":
          // const statemetList: StatementList = {
          //   kind: "StatementList",
          //   statements: to.receiver.statements
          // }
          // const statementsCode = generateNimFromAst(statemetList, indentation)
          // return `${ident}var ${assignmentTarget} = ${statementsCode}`

          throw new Error("BlockConstructor not implemented")

        case "BracketExpression":
          const expressionCode = processExpression(to, 0)
          if (type) {
            return `${ident}var ${assignmentTarget}: ${type} = ${expressionCode}`;
          } else {
            return `${ident}var ${assignmentTarget} = ${expressionCode}`;
          }
        case "ListLiteral":
          const listLiteralCode: string = generateListLiteral(to.receiver)
          return `${ident}var ${assignmentTarget} = ${listLiteralCode}`
        case "MapLiteral":
          const mapLiteralCode: string = generateMapLiteral(to.receiver)
          return `${ident}var ${assignmentTarget} = ${mapLiteralCode}`

        case "SetLiteral":
          throw new Error("TODO")
        default:
          const _never: never = to.receiver
      }

      // has messages
      if (to.messageCalls.length === 0 && to.receiver.kindStatement === "Primary") {
        const assignRightValue = to.receiver.atomReceiver.value;
        if (type) {
          return `${ident}var ${assignmentTarget}: ${type} = ${assignRightValue}`;
        } else {
          return `${ident}var ${assignmentTarget} = ${assignRightValue}`;
        }
      } else {
        // identation after '=' not needed
        const messagesAfterAsign = processExpression(to, 0);
        // Todo infer types
        return `${ident}var ${assignmentTarget} = ${messagesAfterAsign}`;
      }

    case "SwitchExpression":
      // identation after '=' not needed
      const switchCode = generateSwitchExpression(to, 0);
      // Todo infer types
      return `${ident}var ${assignmentTarget} = ${switchCode}`;

    case "Constructor":
      const constructorCode = generateConstructor(to, indentation)
      if (type){
        return `${ident}var ${assignmentTarget}: ${type} = ${constructorCode}`;
      } else {
        return `${ident}var ${assignmentTarget} = ${constructorCode}`;
      }
    case "CustomConstructor":
      const customConstructorCode = generateCustomConstructor(to, indentation)
      if (type){
        return `${ident}var ${assignmentTarget}: ${type} = ${customConstructorCode}`;
      } else {
        return `${ident}var ${assignmentTarget} = ${customConstructorCode}`;
      }
    case "Setter":
      const setterCode = generateSetter(to, 0)
      return `${ident}var ${assignmentTarget} = ${setterCode}`;


    default:
      const never: never = to
      throw new Error("Sound error")


  }
}
