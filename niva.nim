import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc factorial(self: int): int =
  case self:
  of 0:
    1
  else:
    self.`*`((self.`-`(1)).`factorial`())
5.`factorial`().`echo`()