import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
type Person = object
  name: string

proc sas(self: Person): auto =
  x.`echo`()