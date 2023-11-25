import {Primary} from "../Primary";
import {Identifier} from "../Identifier";

export type CollectionLiteral =
  | ListLiteral
  | MapLiteral
  | SetLiteral

export interface ListLiteral {
  kindStatement: "ListLiteral"
  type: string
  value: Primary[]
}

export interface MapLiteral {
  kindStatement: "MapLiteral"
  type: string
  value: KeyValue[]
}

export interface KeyValue {
  key: Primary
  value: Primary
}

export interface SetLiteral {
  kindStatement: "SetLiteral"
  value: Primary[]
  type: string
}