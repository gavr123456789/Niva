import sequtils

### Distinct type
# type Path = distinct string
# var sas: Path = "sas".Path

### apply it
# var nums = @[1, 2, 3, 4]
# nums.applyIt(it * 3)

###
# template array at: index = array[index]
###
### getter setters generation 
# type Person = object 
#   name: string
#   age: int

# template name(self: Person, value: string) =
#   self.name = value
###
# type
#   Comparable = concept x, y
#     (x < y) is bool

# type Sas = seq[Comparable]

# proc qwe(x: Sas) =
#   echo(x)

# qwe(@[1, 2, 3])

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
  var b {.inject.}: int = 4
  for i in self..to:
    var it {.inject.} = i
    doBlock

# 1.to_do(4):
#   echo(b)


### int add
template add*(self: int, arg: int) =
  self += arg
###
### print - echo withoud newline
template print*(s: string) =
  stdout.write(s)

### toStr
template toStr*(self: auto): string =
  self.`$`()
###


func to*(x, y: int): HSlice[system.int, system.int] =
  x..y

### bool
template invert*(self: bool) =
  self = !self

template `not`*(self: bool) =
  self = !self

template `&&`*(self: bool, value: bool): bool =
  self and value

template `||`*(self: bool, value: bool): bool =
  self or value
### send proc as value
# proc zzz(self: int, bloc: proc(x: int): int) =
#   for i in 1..self:
#     bloc(1).echo()

# 1.zzz(
#   proc(x: int): int = 
#     3)

#### While Bench

# template whileTrue(f: typed, body: typed) =
#   while f:
#     body



# echo x
#############################
# while x > 0:
#   x = x - 1
  
# echo(x)
