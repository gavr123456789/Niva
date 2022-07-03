import { BoolLiteral } from "./BoolLiteral";
import { IntLiteral } from "./IntLiteralNode";
import { StringLiteral } from "./StringLiteralNode";
import {DecimalLiteral} from "./DecimalLiteral";

export type AnyLiteral = 
  | StringLiteral 
  | IntLiteral 
  | BoolLiteral
  | DecimalLiteral