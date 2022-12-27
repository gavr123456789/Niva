import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
var y: int = 42
var x = if y.`>`(100):
  "vala"
elif y.`==`(7) or y.`/`(2).`==`(8):
  "nim"
elif y.`/`(7).`==`(6):
  "niva"
else:
  "something else"
x.`echo`()
case x:
of "nim", "pascal":
  "no".`echo`()
of "vala", "C#":
  "no".`echo`()
of "D", "C++":
  "no".`echo`()
of "ni", "va", "niva":
  "yes!".`echo`()
else:
  "I dont know".`echo`()
if y.`<`(10) or y.`/`(5).`==`(4):
  "foo".`echo`()
elif x.`==`("niva").`&&`((y.`==`(42))):
  "yay!".`echo`()