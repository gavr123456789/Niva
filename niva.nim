import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc `--`(self: int, sas: int): bool =
  "sas".`echo`()
  false
proc `--`(self: int, sus: string): string =
  "sus".`echo`()
  "string"
3.`--`((5.`toString`()))
3.`--`(3)