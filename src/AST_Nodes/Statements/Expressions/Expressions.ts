import { IntLiteralNode } from "../../Literals/IntLiteralNode"
import { StringLiteralNode } from "../../Literals/StringLiteralNode"
import { ExpressionStatement, Mutability } from "../Statement"

export type Expression = 
  // | Assignment 
  | Parentheses // круглые скобки с експрешоном



export interface Parentheses {
  kindExpression: "Parentheses"
  expression: ExpressionStatement
}