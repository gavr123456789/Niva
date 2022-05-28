type Person = object
  name: string
  age: int

proc from2(self: Person, x: int) =
  x.echo()

proc `+`(self: Person, x: int): int =
  "hello world".echo();
  1
