import {Expression, Getter} from '../AST_Nodes/Statements/Expressions/Expressions';
import {processExpression} from './expression/expression';
import {generateSwitchExpression} from './expression/switchExpression';
import {generateConstructor} from "./expression/constructor";
import {generateGetter} from "./expression/getter";
import {generateSetter} from "./expression/setter";

export function generateAssigment(assignmentTarget: string, to: Expression, identation: number, type?: string): string {
  const ident = ' '.repeat(identation);

  switch (to.kindStatement) {
    case 'BracketExpression':
    case 'MessageCallExpression':
      if (to.receiver.kindStatement === 'BracketExpression') {
        throw new Error('BracketExpression not supported as left part of assigment');
      }

      if (to.receiver.kindStatement === 'BlockConstructor') {
        throw new Error('BlockConstructor not supported as left part of assigment');
      }

      if (to.messageCalls.length === 0) {
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
      const constructorCode = generateConstructor(to)
      return `${ident}var ${assignmentTarget} = ${constructorCode}`;

    case "Getter":
      const getterCode = generateGetter(to, 0)
      return `${ident}var ${assignmentTarget} = ${getterCode}`;
    case "Setter":
      const setterCode = generateSetter(to, 0)
      return `${ident}var ${assignmentTarget} = ${setterCode}`;


    default:
      const never: never = to
      throw new Error("Sound error")


  }
}
