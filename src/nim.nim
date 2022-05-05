type
  CPerson = concept person
    eatFoodAndThenSleep(person, string, int)
  Person[T] = object 
    name: T
    age: int

proc eatFoodAndThenSleep(self: Person, food: string, hours: int) =
  echo food, " ", hours

proc newPerson[T](name: T, age: int): Person[T] = 
  Person[T](name: name, age: age)


let person = newPerson("sas", 43)
let x: CPerson = person

proc sas(x: CPerson) = 
  x.eatFoodAndThenSleep("xzx", 34)

sas(x)



#
type
  NodeKind = enum  # the different node types
    nkInt,          # a leaf with an integer value
    nkFloat,        # a leaf with a float value
    nkString,       # a leaf with a string value
    nkAdd,          # an addition
    nkSub,          # a subtraction
    nkIf            # an if statement
    Nothing
  Node = ref NodeObj
  NodeObj = object
    case kind: NodeKind  # the `kind` field is the discriminator
    of nkInt: intVal: int
    of nkFloat: floatVal: float
    of nkString: strVal: string
    of nkAdd, nkSub:
      leftOp, rightOp: Node
    of nkIf:
      condition, thenPart, elsePart: Node
    of Nothing:
      discard

# create a new case object:
var n = Node(kind: nkIf, condition: nil)
# accessing n.thenPart is valid because the `nkIf` branch is active:
n.thenPart = Node(kind: nkFloat, floatVal: 2.0)