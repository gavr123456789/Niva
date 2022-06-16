import { Statement } from "../../Statement"
import { Identifer } from "./Primary/Identifier"



export interface BlockConstructor {
  kindStatement: "BlockConstructor"
  type?: string // TODO: add type
  blockArguments: Identifer[]
  statements: Statement[]
}