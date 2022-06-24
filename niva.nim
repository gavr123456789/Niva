import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
type Passport = object
  number: int

type Person = object
  name: string
  age: int
  passport: Passport

var passport = Passport(number: 1234)
var person = Person(name: "Bob", age: 10, passport: passport)
proc sleepOneYearOfLife(self: var Person): auto =
  self.age = self.age.`+`(1)
person.passport.number.`echo`()
person.`passport`().`number`().`echo`()