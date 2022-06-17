type Person = object 
  name: string

proc renameTo(self: var Person, newName: string) =
  self.name = newName

var person = Person(name: "sas")
person.renameTo("kek")
echo(person)
  