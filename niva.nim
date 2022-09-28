import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
"Hello World!".`echo`()
proc `+++`(self: int, y: auto): int =
  self.`+`(y).`+`(1)
var a = 1.`+++`(2)
a.`echo`()