import { Expression } from "../Expressions";
import { Primary } from "../Primary/Primary";

export type Receiver = 
  | Primary
  // | ExpressionAsReceiver
  // | BlockConstructor
  // | BracketExpression