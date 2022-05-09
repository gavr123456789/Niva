import { MatchResult } from "ohm-js";
import grammar from "./niva.ohm-bundle";

export function grammarMatch(input: string, startRule?: string): MatchResult {
  return grammar.match(input, startRule)
}