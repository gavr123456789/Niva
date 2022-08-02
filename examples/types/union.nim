import "nivaPrelude"
{. push discardable .}
{. push warning[ProveField]:on .}
type
  ShapeKind* = enum Nothing, Rectangle, Circle
  Shape* = object
    case kind*: ShapeKind
    of Nothing:
      discard
    of Rectangle:
      width: int
    of Circle:
      radius: int

