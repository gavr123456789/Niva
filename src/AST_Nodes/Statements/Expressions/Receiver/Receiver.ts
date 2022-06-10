import { BracketExpression } from "../Expressions";
import { BlockConstructor } from "./BlockConstructor";
import { Primary } from "./Primary/Primary";

export type Receiver = 
  | Primary
  | BlockConstructor
  | BracketExpression