
<div align="center">

<p>
<img width="96px" height="96px" src="Niva/niva-icon.svg" />
</p>

<h1>niva</h1>

**[Docs](https://gavr123456789.github.io/niva-site) |
[Learn X in Y minutes](https://learnxinyminutes.com/niva/) |
[Installation](./INSTALL.md) |
[Libs](https://github.com/gavr123456789/bazar/tree/main/Bindings)**
</div>

<p>
<img src="https://github.com/gavr123456789/Niva/assets/30507409/7dbdf939-cb71-4459-8a74-4d50fce2c9d3" width=30% height=30%>
</p>

Niva is a simple language that takes a lot of inspiration from Smalltalk.
But leaning towards the functional side and static types. Everything is still an object, but instead of classes, interfaces, inheritance, and abstract classes, we have tagged unions, which is the only way to achieve polymorphism.

In essence, niva is highly minimalistic, since its ancestor is Smalltalk.
It introduces types, unions, and associated methods. Yes there are no functions.

On an imaginary complexity graph I would place the field here:
Go < Niva < Java < Kotlin < Scala

Everything in niva is a message for some receiver
kinda like eveything is an S-Expression in Lisp `(+ 1 2)`
but in niva there are 3 types of expressions:

```Scala
1 + 2 // binary - 2 args
42 factorial // unary - 1 args(receiver)
"foo-bar" split: "-" // keywoard - n args
```

<details>
    <summary>Backend</summary>

Current backend is Kotlin, because you get 4 backends for free - JVM, Native, JS, Wasm, also ecosystem is rich. A lot of pet-project languages are translated into js, which is very high-level, so why not be translated into a real language.
</details>

<details>
    <summary>About syntax</summary>

If you are not familiar with Smalltalk, its like lisp - everything is an expression(ifs, cicles etc) but not S-expressions, a different one without parentheses.

For example, everything except the declaration is sending messages to objects(receivers).
`1 + 2` is not a `+` operator, but a `+ Int` message for `Int` receiver.

Java-like: `1.inc().echo()`
C-like: `echo(inc(1))`
Lisp: `(echo (inc 1))`
Niva: `1 inc echo`

with args:
Swift\Kotlin:
`person fooBar(foo = 1, bar = 2)`
Niva:
`person foo: 1 bar: 2`
So names of the args and method signature are the same thing.

```Scala
// declare type with 2 fields
type Person name: String age: Int
person = Person name: "Alice" age: 24 // instantiate

person name echo // get name and print it
personWithNewName = person name: "new name"

// unary method declaration
Person hi = "Hi! my name is $name" echo
person hi // unary call

// method with args
Person foo::Int bar::Int = [
  age + foo + bar, echo // same as
  (age + foo + bar) echo
]
person foo: 1 bar: 2 // 27 printed

union Shape =
| Rectangle width: Int height: Int
| Circle    radius: Int

constructor Float PI = 3.14
Float PI // constructor call

// fields can be placed on new lines
type Point
  x: Int
  y: Int

constructor Point new = Point
  x: 0
  y: 0

p = Point new

```

</details>

## Code Examples

<details>
<summary><b>Hello World</b></summary>

```Scala
"Hello World" echo
```
</details>

<details>
<summary><b>Methods</b></summary>

```Scala
type Person
  name: String
  age: Int

Person greet = "Hello, my name is " + name

// create person obj
p = Person name: "Alice" age: 24
// call greet and print
p greet echo
```
[Message declaration](https://gavr123456789.github.io/niva-site/message-declaration.html)
</details>


<details>
<summary><b>Some Strings methods</b></summary>

```Scala
// unary
"foo-bar" count
"drawer" reversed
"BAOBAB" lowercase
// binary
"ee" == "ee"
"foo" + "bar"
// keyword
"foo-bar" split: "-"
"abcdef" forEach: [char -> char echo]
"baobab" filter: [it == 'b'] // it - implicit param
"foo-bar-baz" replace: "-" with: " "
"chocolate" contains: "late"
```
[String STD](https://gavr123456789.github.io/niva-site/stringtype.html)
</details>



<details>
<summary><b>Control flow</b></summary>

```Scala
name = "Alice"
// switching on name
| name
| "Bob"   => "Hi Bob!" echo
| "Alice" => "Hi Alice!" echo
|=> "Hi guest" echo

// if is a message for Boolean that takes 2 lambdas
x = 1 > 2 ifTrue: ["what?!"] ifFalse: ["yea"]

// you can switch on bool (and all primitive types)
| 1 > 2
| true => "what?!" echo
| false => "yea" echo
```
[Control flow](https://gavr123456789.github.io/niva-site/control-flow.html)
</details>

<details>
<summary><b>Fibonacci</b></summary>

```Scala
Int fib -> Int = |this
| 0 => 1
| 1 => 1
|=> (this - 2) fib + (this - 1) fib

6 fib echo
```
[Fibonacci](https://github.com/gavr123456789/Niva/blob/main/Niva/Niva/examples/Algoritms/Factorial/factorial.niva)
</details>


<details>
  <summary><b>Bottles of beer</b></summary>

```Scala
Int bottles = | this
| 0 => "no more bottles"
| 1 => "1 bottle"
|=> "$this bottles"

onTheWall = " of beer on the wall, "
99 downTo: 1 do: [
  it bottles + onTheWall + it bottles + " of beer.", echo
  "Take one down and pass it around, " + it dec bottles + onTheWall, echo
]
"No more bottles of beer on the wall, no more bottles of beer." echo
"Go to the store and buy some more, 99 bottles of beer on the wall." echo
```
</details>


<details>
  <summary><b>Code blocks</b></summary>

```Scala
// A code block is a collection of statements that returns last expr
[
  x = 1
  y = 2
  x + y
]
// to run block send `do` message
x = [1 + 2]
x do echo // 3

[x * 2] // capture

// to run block with args send their names
add2nums = [a::Int, b::Int -> a + b]
result = add2nums a: 21 b: 21 // 42
```
</details>

## Features
- Simple expression based syntax inspired by Smalltalk
- No imports until full type name + fields clash. Since every modern IDE adds imports for you I decided to make the same on the compiler level
- Smalltalk like hierarchy: projects -> packages -> protocols -> methods
- Simple semantics - the whole lang is essentially types(type\enum\union) and methods for them.
- IDE support - LSP with plugins for [VSC](https://github.com/gavr123456789/niva-vscode-bundle) and [Zed](https://github.com/gavr123456789/zed-niva) (check some demos [here](https://github.com/gavr123456789/niva-vscode-bundle?tab=readme-ov-file#features-include))
- No NPE, nullability works the same as in Kotlin\Swift\TS
- Errors are between values and exceptions(Nim\Roc-like effects), all possible errors of the scope is union that can be exhaustively matched. [Error docs](https://gavr123456789.github.io/niva-site/error-handling.html)
- JVM\Kotlin compatibility, easy lib [bindings](https://github.com/gavr123456789/bazar) ([File example](https://github.com/gavr123456789/bazar/blob/main/Bindings/Files/simpleReadWrite.bind.niva))
- Easy serialize any obj via Dynamic type, no Docs yet, but its like json for JS or EDN for Clojure
- Docgen and unit tests included
- Smalltalk syntax is great for creating DSLs without any complicated macros or AST manipulations: `1d/12m/2028y`, in Java-like: `(1.d()) / (12.m()) / (2028.y())`

## Project examples
- [niva in niva impl](https://github.com/gavr123456789/Niva/tree/main/Niva/NivaInNiva) (lexer + parser)
- [writing interpreter in go](https://github.com/gavr123456789/writing-an-interpreter-in-niva) (not finished)
- [tons of small stupid things](https://github.com/gavr123456789/bazar/tree/main/Examples)

## Learn
- [main site](https://gavr123456789.github.io/niva-site/reference.html)
- [learn x in y minutes](https://learnxinyminutes.com/niva/)

## Install

```
git clone https://github.com/gavr123456789/Niva.git
cd Niva/Niva
./gradlew buildJvmNiva # takes one min for the first time :(
# LSP here https://github.com/gavr123456789/niva-vscode-bundle
```

More in [Installation](./INSTALL.md)

## First program
```Scala
// main.niva
"Hello World" echo
```
`niva run`
Default entry point is always `main.niva` file.

Other commands:
`niva filename.niva` to run file
`niva run` to run all files in the folder recursivelly starting from main.niva
`niva build` to output jar\binary file
`niva info > info.md` to output all types and their methods
`niva --help` for more

### Project with 2 files
Try to create new file and put the foo:bar: method in it:
```Scala
//foobar.niva
type FooBar
FooBar foo::Int bar::Int = [
  foo echo
  bar echo
  ^ foo + bar
]
```
in main.niva
```Scala
// to create type without fields send `new`
fb = FooBar new
result = fb foo: 19 bar: 23
result echo
```
now lets rewrite it into single expr, just for fun

```Scala
FooBar new foo: 19 bar: 23, echo
````
What that comma means?
In niva there is only one precedence order: unary > binary > keyword
Here a complicated (scary) example:
```Scala
1 inc inc > 2 dec dec // 3 > 0 -> true
// adding keyword call
1 inc inc > 2 dec dec or: 3 dec < 4 inc

// lets apply () to see the order more clearly
((1 inc inc) > (2 dec dec)) or: ((3 dec) < (4 inc))

// another example
1 inc inc > 2 dec dec ifTrue: ["yay!!!" echo]
((1 inc inc) > (2 dec dec)) ifTrue: ["yay!!!" echo]


// but what if we want to send some unary message to result of a keyword
"foo-bar-baz" replace: "-" with: " " echo
// Here we made a mistake, echo will be sent to " ", not to result of `replace:with:`
// to fix that we can wrap the whole thing:
("foo-bar-baz" replace: "-" with: " ") echo
// or just put a comma
"foo-bar-baz" replace: "-" with: " ", echo

// same situation when we want to chain many keyword messages
"aabbcc"
  replace: "a" with: "d",
  replace: "b" with: "e",
  replace: "d" with: "f"

// list chain
1..10,
  toList
  filter: [it % 2 == 0],
  map: [it toString + "!"],
  forEach: [it echo]
```

### Method with body
```Scala
// square brackets gang
Int add4 = [
  x = this + 1
  add3 = x inc inc inc
  ^ add3 // ^ is return, thats from Smalltalk
]
1 add4 echo
```

### Method with args(keyword)

```Scala
type Person name: String
p = Person name: "Alice"
// the constructor call is a keyword message for the type itself

Person greet
```



### Simple compile time reflection

```Scala
Assert
TODO
```

### Test included
Create a message for Test type anywhere to create test.
Here you can find some lexer tests examples of NIN impl.
https://github.com/gavr123456789/Niva/blob/main/Niva/NivaInNiva/front/lexer/lexTest.niva#L27

```Scala
Test arithmetic = [
  actual = 2 + 2
  expected = 4
  (actual == expected) assert
]
// very simple assert implementation
Boolean assert =
  this == false ifTrue: [Error throwWithMessage: "assert failed", orPANIC]
```
`niva test` outputs: `main > arithmetic ✅`

### Union
https://gavr123456789.github.io/niva-site/unions.html

```Scala
// branches can have fields
union Shape =
| Rectangle width: Int height: Int
| Circle    radius: Double

constructor Double pi = 3.14
Double square = this * this

// match on this(Shape)
Shape getArea -> Double =
| this
| Rectangle => width * height, toDouble
| Circle => Double pi * radius square

// There is exhaustiveness checking, so when you add a new branch
// all the matches will become errors until all cases processed

Shape getArea -> Double = | this
| Rectangle => width * height, toDouble
// ERROR: Not all possible variants have been checked (Circle)
```

### Bind Java std and read the file
Create `io.simple.bind.niva` in the same folder
```Scala
// very simple text read write
Bind package: "java.io" content: [
  type File path: String
  File readText -> String
  File writeText::String -> Unit
  File exists -> Boolean
]
```

`main.niva`
```Scala
// read all text from the file to String
File path: "main.niva", readText
File path: "newFile.txt", writeText: "Hello from niva!"
```

# Name
So far I've chosen niva because my 2 favorite static languages are nim and vala.

# Backend
Current backend is Kotlin, because you get 4 backends for free - JVM, Native, JS, Wasm, also ecosystem is rich. A lot of pet-project languages are translated into js, which is very high-level, so why not be translated into a real language.
