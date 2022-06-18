import "nivaPrelude"
{. push discardable .}
type Person = object
  name: string
  age: int

var kek = Person(age: 42, name: "sas")
proc sas(self: int): auto =
  var x = 3
  var y = x
  var z = y
  var q = z
proc `++`(self: string, string: auto): auto =
  var y = true
proc fromString(self: int, string: auto): auto =
  var z = "sas"