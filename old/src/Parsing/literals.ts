import { IterationNode, NonterminalNode, TerminalNode } from "ohm-js"
import { SimpleLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/SimpleLiteral"
import { BoolLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/BoolLiteral"
import { IntLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/IntLiteralNode"
import { StringLiteral } from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/StringLiteralNode"
import {DecimalLiteral} from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/DecimalLiteral";
import {
  CollectionLiteral, KeyValue,
  ListLiteral, MapLiteral, SetLiteral
} from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/CollectionLiteral";
import {Primary} from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Primary";

export function boolLiteral(boolLiteral: NonterminalNode): BoolLiteral {
  const result: BoolLiteral = {
    kindPrimary: "bool",
    value: boolLiteral.sourceString
  }
  return result
}

export function integerLiteral(intLiteral: NonterminalNode): IntLiteral {
  const result: IntLiteral = {
    kindPrimary: 'int',
    value: intLiteral.sourceString
  };
  return result;
}

export function decimalLiteral(intLiteral: NonterminalNode, arg1: TerminalNode, arg2: IterationNode): DecimalLiteral {
  const result: DecimalLiteral = {
    kindPrimary: 'float',
    value: intLiteral.sourceString + arg1.sourceString + arg2.sourceString
  };
  return result;
}

export function stringLiteral(_lQuote: TerminalNode, text: IterationNode, _rQuote: TerminalNode): StringLiteral {
  const result: StringLiteral = {
    kindPrimary: 'string',
    value: '"' + text.sourceString + '"'

  };
  return result;
}

export function simpleLiteral(arg0: NonterminalNode): SimpleLiteral {
  const result: SimpleLiteral = arg0.toAst()
  return result;
}

export function listLiteral(_rb: TerminalNode, listElementsNode: NonterminalNode, lb: TerminalNode): ListLiteral {
  const listElements: Primary[] = listElementsNode.toAst()
  const result: ListLiteral = {
    kindStatement: "ListLiteral",
    value: listElements,
    type: "auto"
  }
  return result
}


export function mapLiteral(arg0: TerminalNode, mapElementsNode: NonterminalNode, arg2: TerminalNode): MapLiteral {

  const mapElements: KeyValue[] = mapElementsNode.toAst()
  const result: MapLiteral = {
    kindStatement: "MapLiteral",
    type: "auto",
    value: mapElements
  }
  return result
}

export function hashSetLiteral(arg0: TerminalNode, listElementsNode: NonterminalNode, arg2: TerminalNode): SetLiteral {
  const listElements: Primary[] = listElementsNode.toAst()

  const result: SetLiteral = {
    kindStatement: "SetLiteral",
    type: "auto",
    value: listElements
  }
  return result
}

export function mapElements(maybeMapElement: IterationNode, _c: IterationNode, _s: IterationNode, otherMapElements: IterationNode): KeyValue[] {
  const p: KeyValue | undefined = maybeMapElement.children.at(0)?.toAst()
  const others: KeyValue[] = otherMapElements.children.map(x => x.toAst())

  const result: KeyValue[] = []
  if (p){
    result.push(p)
  }
  if (others.length > 0){
    result.push(...others)
  }

  return result
}

export function mapElement(primaryKey: NonterminalNode, _s: NonterminalNode, primaryValue: NonterminalNode): KeyValue {

  const result: KeyValue = {
    key: primaryKey.toAst(),
    value: primaryValue.toAst()
  }
  return  result
}


export function listElements(maybePrimary: IterationNode, _s0: NonterminalNode, _comma: IterationNode, _s1: IterationNode, othersPrimary: IterationNode): Primary[] {
  const p: Primary | undefined = maybePrimary.children.at(0)?.toAst()
  const others: Primary[] = othersPrimary.children.map(x => x.toAst())

  const result: Primary[] = []
  if (p){
    result.push(p)
  }
  if (others.length > 0){
    result.push(...others)
  }

  return result
}


