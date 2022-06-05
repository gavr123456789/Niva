{. push discardable .}
proc fib(self: int): int =
  if self.`<`(2):
    1
  else:
    (self.`-`(1)).fib().`+`((self.`-`(2)).fib())
0.fib().echo()
1.fib().echo()
2.fib().echo()
3.fib().echo()
4.fib().echo()
5.fib().echo()