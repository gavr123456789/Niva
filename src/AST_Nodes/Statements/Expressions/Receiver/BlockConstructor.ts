import { Statement } from "../../Statement"
import { Identifier } from "./Primary/Identifier"



export interface BlockConstructor {
  kindStatement: "BlockConstructor"
  type?: string // TODO: add type
  blockArguments: Identifier[]
  statements: Statement[]
}