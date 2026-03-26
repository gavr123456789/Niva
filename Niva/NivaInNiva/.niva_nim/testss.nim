import core
import sets
import tables

type Person* = ref object
  name: string
  age: int


proc Person_hello*(this: Person)

proc `$`*(this: Person): string =
  if this.isNil:
    return "Person(nil)"
  var txt = "Person"
  txt.add("\n  name: " & core.Any_toString(this.name))
  txt.add("\n  age: " & core.Any_toString(this.age))
  txt


proc Person_hello*(this: Person) =
  let name = this.name
  let age = this.age
  core.Any_echo(((("hello my name is 321 ") & (name))))


let p = Person(name: "Alice", age: 24)
Person_hello(p)
let list = @[ 1, 2, 3 ]
let hashSet = toHashSet(@[1, 2, 3])
let hashMap = toTable(@[(1, 2), (3, 4)])
core.Any_echo(list)
core.Any_echo(hashSet)
core.Any_echo(hashMap)
core.Any_echo((core.List_map(list, proc (it: int): int =
  return ((it) * (2)))))
