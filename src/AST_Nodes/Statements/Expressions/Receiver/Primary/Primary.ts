import { Identifer } from "./Identifier";
import { AnyLiteral } from "./Literals/AnyLiteral";


export interface Primary {
  kindStatement: "Primary"
  type?: string // TODO: add type
  atomReceiver: AnyLiteral | Identifer
}