import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc `/++/`(self: int, x: auto): string =
  self.`toStr`().`&`(x.`toStr`())
var x: string = (5.`*`(3)).`/++/`((3.`+`((3.`-`(4)))))
x.`echo`()