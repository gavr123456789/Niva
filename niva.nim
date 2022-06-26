import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc plus_plus(self: string, another: string, another: string): string =
  self.`&`(another).`&`(another)
var concat = "abc".plus_plus("xyz", "sas")
concat.`echo`()