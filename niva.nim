import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
proc fizzbuzz(self: int): string =
  if (self.mod(3.`*`(5))).`==`(0):
    "FizzBuzz"
  elif (self.mod(3)).`==`(0):
    "Fizz"
  elif (self.mod(5)).`==`(0):
    "Buzz"
  else:
    self.`toStr`()
5.`fizzbuzz`().`echo`()
proc fizzbuzzTo(self: int, x: auto): void =
  self.to_do(x):
    it.`fizzbuzz`().`echo`()
1.fizzbuzzTo(100)