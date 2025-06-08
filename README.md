
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
But leaning towards the functional side and static typed. Everything is still an object, but instead of classes, interfaces, inheritance, and abstract classes, we have tagged unions, which is the only way to achieve polymorphism.
If you are not familiar with Smalltalk, its like lisp - everything is an expression(ifs, cicles etc) but not S-expressions, a different one without parentheses.
  

Everything in niva is a message for some receiver  
kinda like eveything is an S-Expression in Lisp `(+ 1 2)`  
but in niva there are 3 types of expressions:  

```Scala
1 + 2 // binary - 2 args
42 factorial // unary - 1 args(receiver)
"foo-bar" split: "-" // keywoard - n args
```

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
"foo-bar" count
"drawer" reversed
"BAOBAB" lowercase

"ee" == "ee"
"foo" + "bar"

"foo-bar" split: "-"
"abcdef" forEach: [char -> char echo]
"baobab" filter: [it == 'b']
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


## Install
`cd Niva/Niva`  
`./gradlew buildJvmNiva` (takes one min)  
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

### Method declaration

```Scala
// Method with name `sas` for type `Int`
Int sas = 42

1 sas echo
```
This is same as `echo(sas(1))` in C syntax or `1.sas().echo()` in Java-like.  
You may notice some consistency between the declaration and the method call.
```
Type signature = expr
obj signature
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





## Simple compile time reflection

```Scala
Assert
TODO
```

## Test included
Create a message for Test type anywhere to create test.  
Here you can find some lexer tests examples of NIN impl. 
https://github.com/gavr123456789/Niva/blob/main/Niva/NivaInNiva/front/lexer/lexTest.niva#L27  

```Scala
Test simple = [
  actual = 2 + 2
  expected = 4
  actual != expected ifTrue: [

  ]
]
```

----
## Union

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

## Read file
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
File path: "main.niva" |> readText
// write text to file, if this file exists, it becomes overwritten
File path: "newFile.txt" |> writeText
```
