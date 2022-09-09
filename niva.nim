import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc fib(self: int): int =
  var n: int = self
  if n.`<`(2):
    1
  else:
    (n.`-`(2)).`fib`().`+`((n.`-`(1)).`fib`())
proc fib2(self: int): int =
  case self:
  of 0:
    1
  of 1:
    1
  else:
    (self.`-`(2)).`fib`().`+`((self.`-`(1)).`fib`())
5.`fib`().`echo`()
6.`fib2`().`echo`()


