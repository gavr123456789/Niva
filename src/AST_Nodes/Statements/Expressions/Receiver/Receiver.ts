import { BracketExpression } from "../Expressions";
import { BlockConstructor } from "./BlockConstructor";
import { Primary } from "./Primary/Primary";
import {CollectionLiteral} from "./Primary/Literals/CollectionLiteral";

export type Receiver = 
  | Primary
  | BlockConstructor
  | BracketExpression
  | CollectionLiteral
