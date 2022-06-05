import { BoolLiteral } from "./BoolLiteral";
import { IntLiteral } from "./IntLiteralNode";
import { StringLiteral } from "./StringLiteralNode";

export type AnyLiteral = 
  | StringLiteral 
  | IntLiteral 
  | BoolLiteral