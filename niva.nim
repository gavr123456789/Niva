import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc between_and(self: int, x: int, y: int): bool =
  if x.`<`(self).`&&`((self.`<`(y))):
    true
  elif x.`>`(self).`&&`((self.`>`(y))):
    false
  else:
    false
var y = 5.between_and(1, 10)
y.`echo`()