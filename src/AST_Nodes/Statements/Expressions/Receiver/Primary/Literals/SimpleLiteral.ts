import {BoolLiteral} from "./BoolLiteral";
import {IntLiteral} from "./IntLiteralNode";
import {StringLiteral} from "./StringLiteralNode";
import {DecimalLiteral} from "./DecimalLiteral";

export type SimpleLiteral =
  | StringLiteral
  | IntLiteral
  | BoolLiteral
  | DecimalLiteral