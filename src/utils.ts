import { MatchResult } from "ohm-js";
import grammar from "./niva.ohm-bundle";

export function grammarMatch(input: string, startRule?: string): MatchResult {
  return grammar.match(input, startRule)
}


export let isDebug = {
  isDebug: true
} 
export function getName(variable: any){ return Object.keys(variable)[0]}

// export function echo( ...optionalParams: (object | string)[]) {
//   if (!isDebug.isDebug) return
//   for (const x of optionalParams) {
//     if (typeof x === "string"){
//       console.log(x);
//     } else if (typeof x === "object" && x) {
//       const name = getName(x)
//       console.log(name,": ", Reflect.get(x, name));
//     }
//   }
//   console.log("\n");
// }
