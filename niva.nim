import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
type 

  MalTypeKind* = enum True, False, Nil, Number, Symbol, String
  MalType* = object
    case kind*: MalTypeKind
    of True, False, Nil:
      discard
    of Number:
      number: int
    of Symbol, String:
      str: string
      number2: int