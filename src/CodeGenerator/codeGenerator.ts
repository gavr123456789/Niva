import {StatementList} from '../AST_Nodes/AstNode';
import {ReturnStatement} from '../AST_Nodes/Statements/Statement';
import {generateAssigment} from './assignment';
import {generateSwitchExpression} from './expression/switchExpression';
import {generateSwitchStatement} from './expression/switchStatement';
import {generateMethodDeclaration} from './methodDeclaration';
import {generateTypeDeclaration} from './typeDeclaration';
import {BracketExpression, Constructor, MessageCallExpression} from "../AST_Nodes/Statements/Expressions/Expressions";
import {generateReturn} from "./return";
import {generateCallLikeExpression} from "./expression/callLikeExpression";


// То что может быть на первом уровне вложенности
export function generateNimFromAst(x: StatementList, identation = 0, discardable = false, includePrelude = false): string {
  let lines: string[] = [];
  if (includePrelude) {
    lines.push(`import "nivaPrelude"`)
  }

  if (discardable) {
    lines.push('{. push discardable .}');
    lines.push('{. push warning[ProveField]:on .}');
  }

  for (const s of x.statements) {
    switch (s.kindStatement) {
      case 'MessageCallExpression':
      case "BracketExpression":
      case "Constructor":
      case "CustomConstructor":
      case "Setter":
        const callLikeExpressionCode = generateCallLikeExpression(s, identation)
        lines.push(callLikeExpressionCode)
        break;
      case 'Assignment':
        // if (s.mutability === Mutability.IMUTABLE) {
        lines.push(generateAssigment(s, identation));
        // }
        break;

      case 'ReturnStatement':
        lines.push(generateReturn(s, identation))
        break;
      case 'TypeDeclaration':
        lines.push(generateTypeDeclaration(s, identation))
        break;
      case 'MethodDeclaration':
      case "ConstructorDeclaration":
        lines.push(generateMethodDeclaration(s, identation))
        break;
      case 'SwitchExpression':
        lines.push(generateSwitchExpression(s, identation))
        break;
      case 'SwitchStatement':
        lines.push(generateSwitchStatement(s, identation))
        break;


      default:
        const _never: never = s;

        throw new Error('SoundError');
    }
  }

  return lines.join('\n');
}

