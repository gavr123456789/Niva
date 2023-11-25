// TODO MOVE
import {
  ListLiteral,
  MapLiteral,
  SetLiteral
} from "../AST_Nodes/Statements/Expressions/Receiver/Primary/Literals/CollectionLiteral";
import {getPrimaryCode} from "./expression/expression";

export function generateListLiteral(listLiteral: ListLiteral) {
  const {value} = listLiteral
  const joined = value.map(x => getPrimaryCode(x, 0)).join(", ")
  return `@[${joined}]`
}

export function generateMapLiteral(mapLiteral: MapLiteral) {
  const {value} = mapLiteral

  const joined = value.map(x => {
    const key = getPrimaryCode(x.key, 0)
    const value = getPrimaryCode(x.value, 0)
    return `${key}: ${value}`
  }).join(", ")
  return `{${joined}}.toTable()`
}

export function processCollection(x: MapLiteral | ListLiteral | SetLiteral): string {
  switch (x.kindStatement) {
    case "MapLiteral":
      return generateMapLiteral(x)
    case "ListLiteral":
      return generateListLiteral(x)
    case "SetLiteral":
      throw new Error("todo")
  }
}