import { Statement } from "../../Statement"
import { Identifer } from "./Primary/Identifier"



export interface BlockConstructor {
  kindStatement: "BlockConstructor"
  blockArguments: Identifer[]
  statements: Statement[]
}