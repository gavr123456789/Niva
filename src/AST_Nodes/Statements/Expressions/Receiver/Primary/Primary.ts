import { Identifier } from "./Identifier";
import { SimpleLiteral } from "./Literals/SimpleLiteral";


export interface Primary {
  kindStatement: "Primary"
  type?: string
  atomReceiver: SimpleLiteral | Identifier
}
