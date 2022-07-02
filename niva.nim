import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc `--`(self: int, sas: int): int =
  "sas".`echo`()
  5
proc `--`(self: int, sus: string): string =
  "sus".`echo`()
  "string"
proc `++`(self: string, ses: string): int =
  "ses".`echo`()
  6
3.`--`((5.`toString`()))
3.`--`(3)
3.`toString`().`++`(5.`toString`())