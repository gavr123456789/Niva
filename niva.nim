import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
type Person = object
  name: string

proc construct_Person_bob(): Person =
  Person(name: "Bob")
proc construct_Person_fromName(name: auto): auto =
  Person(name: name)
var y: Person = construct_Person_bob()
var x: Person = construct_Person_fromName("qwe")
y.name.`echo`()
x.name.`echo`()