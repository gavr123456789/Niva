# import print
import std/tables

from std/strutils import join

# proc echo* = print
### Distinct type
# type Path = distinct string
# var sas: Path = "sas".Path

### apply it
# var nums = @[1, 2, 3, 4]
# nums.applyIt(it * 3)

### toStr
proc toStr*[T](x: seq[T]): string =
  let a = x.join(", ")
  return "{ " & a & " }"    
    
template toStr*(self: auto): string =
  self.`$`()
###

### Tables
template at_put*[K, V](self: var Table[K, V], key: K, value: V) =
  self[key] = value

# since there are no error handling, there will be only safe table methods
template at_or*[K, V](self: var Table[K, V], key: K, defaultValue: V): V =
  self.getOrDefault(key, defaultValue)

proc makeDiscardable[T](a: T): T {.discardable, inline.} = a

template at_orDo*[K, V](self: Table[K, V], key: K, doBlock: typed): auto =
  # when declaredInScope(doBlock):
    try:
      self[key]
    except KeyError:
      doBlock
  # else:
  #   try:
  #     var kek = self[key]
  #     makeDiscardable kek
  #   except KeyError:
  #     var kek = doBlock
  #     makeDiscardable kek

template has_orDo*[K, V](self: Table[K, V], key: K, doBlock: typed): auto =
  if not self.hasKey(key):
    doBlock



# var tabl = {"as": 2}.toTable
# var zxc = tabl.at_orDo("sas"):
#   44
# echo(tabl)

template at*[K, V](self: var Table[K, V], key: K): V =
  self.getOrDefault(key)

template foreach*[K, V](self: var Table[K, V], doBlock: untyped) =
  for x, y in self:
    var key {.inject.} = x
    var value {.inject.} = y
    doBlock


### Arrays


from std/sequtils import mapIt

template filter*(self, pred: untyped): untyped =
  var result = newSeq[typeof(self[0])]()
  for it {.inject.} in items(self):
    if pred: result.add(it)
  result

template map*(self, codeBlock: untyped): untyped =
  mapIt(self, codeBlock)


template foreach*[T](self: seq[T], doBlock: untyped) =
  for i in self:
    var it {.inject.} = i
    doBlock

template at*[T](self: var seq[T], index: int): T =
  self[index]

template at_put*[T](self: var seq[T], index: int, put: T): void =
  self[index] = put



  
# var collection = @[1,2,3]
# var col2 = collection.filter:
#   it > 1

# echo col2
# collection.foreach:
#   echo it

###



template whileTrue*(self: bool, body: typed) =
  while self:
    body
# var t = 0
# (t < 10).whileTrue:
#   t.inc()
#   echo t

### Try many do
# template combineTwo*(body1: untyped, body2: untyped) =
#   body1
#   body2

# combineTwo:
#   echo(4)
# do:
#   echo(4)
#  3 dsd: "343"

###
template timesRepeat*(self: int, z: bool, body: typed) =
  echo z
  for i in 0..self:
    body
    
# 4.timesRepeat(false):
#   echo "sas"

# может сделать дирти чтобы как бы переменные проникали
template to_do*(self: int, to: int, doBlock: untyped) =
  for i in self..to:
    var it {.inject.} = i
    doBlock

# 1.to_do(4):
#   echo(b)


### int add
template add*(self: int, arg: int) =
  self += arg
###
### printnln - echo without newline
template printnln*(s: string) =
  stdout.write(s)

# template print*(s: untyped) =
#   print(s)



func to*(x, y: int): HSlice[system.int, system.int] =
  x..y

### bool
template invert*(self: bool) =
  self = !self

template `&&`*(self: bool, value: bool): bool =
  self and value

template `||`*(self: bool, value: bool): bool =
  self or value



export tables