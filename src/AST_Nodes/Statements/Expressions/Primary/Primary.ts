import { Expression } from "../Expressions";
import { Identifer } from "./Identifier";
import { AnyLiteral } from "./Literals/AnyLiteral";


export interface Primary {
  kindStatement: "Primary"
  atomReceiver: AnyLiteral | Identifer
}