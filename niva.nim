import "nivaPrelude"
{. push discardable .}
type Person = object
  name: string
  age: int

proc sas(self: int): auto =
  var kek = Person(age: 42, name: "sas")
  var x = 3
  var y = x
  var z = y
  var q = z
  kek.`age`()
proc `++`(self: string, string: auto): auto =
  var y = true
proc fromString(self: int, string: auto): auto =
  var z = "sas"