import { Expression } from "../Expressions";
import { Identifer } from "./Identifier";
import { AnyLiteral } from "./Literals/AnyLiteral";

// export type Primary =  AnyLiteral | Identifer 

export interface Primary {
  kindReceiver: "Primary"
  atomReceiver: AnyLiteral | Identifer
}