# Niva
It will be Smalltalk like language, but statically typed.

## What's going on
The simple implementation is ready on TS, now I rewriting it on Kotlin without parser generator(ohm)

## Backend
Now the backend is Nim. Yes it's quite fun `niva` -> `nim` -> `C`.  
I made this decision because I saw how many new languages die quickly, due to lack of time or being disappointed in ideas, so I decided to transpile into a language of a sufficiently high level so as not to mess with LLVM and be able to quickly test ideas. Also nim gives free Js transpile and simple interaction with `C`, `C++` and `Js`.  
  
A lot of languages are translated into js, which is very high-level, so why not be translated into a real language.

P.S. now realization written in ts, in future self ... are planned.

## Name
I haven't decided on the final name yet, so far I've chosen niva because my 2 favorite static languages are nim and vala.

# Examples

#### Hello world
Everything is sending a message to an object. There no such things as `print(obj)`, only `object message`
```F#
"Hello world" echo
```
You can send as many messages as you want 
```Scala
"12.08.2009" asDate days echo 
// 12
```

Okey, but what if out message has some arguments  
Just separate them with colons, this is called a keyword message:  
```Scala
obj foo: argument

1 to: 5 // oh we just created ranges
```
And what about many arguments?  
Easy  
```F#
1 to: 5 do: [ it echo ] // 1 2 3 4 5
```
aand we dont need things like hardcoded for/while/do_while loops in language anymore, the second argument here is a code block

#### Type and methods declaration
Niva is statically typed language, so we need a way to declare custom types, here it is, just the syntax of keyword messages with type keyword.  

Each type automatically has a constructor that represents as the same keyword message, isn't it beautiful?
```Scala
// Declare type Person with 2 fields
type Person name: string age: int
// Instantiation
person = Person name: "Bob" age: 42
```
To declare function for type just type `Type function_signature = body`
```Scala
// unary method declaration for Person receiver
Person sleep = [...]
// method call
person sleep
// with arguments(keyword message)
TimeInterval from: x::Date to: y::Date = [ 
  // using x and y
]
```

In the last example you can see a problem, we dont need local names for from and to, because of that there are 3 different way to declare keyword messages: with locals and types, with locals only, with types only.  
![image](https://user-images.githubusercontent.com/30507409/219905868-bcde0079-9c0c-443d-9bf3-41be072c491c.png)




#### Factorial
`|` -> if/switch expression/statement  
`something | exp -> do |=> else_do` - expression, else branch is necessary  
`x = y > 0 | true |-> false` - expression, every branch must return a value  
`| exp -> do` - statement  
`|-> do` - else branch

```Scala
int factorial -> int = self
| 0 => 1 // switch on self, self is int
|=> self * (self - 1) factorial.

5 factorial echo
```

#### Fibonacci
```F#
int fib -> int = [
  n = self.
  | n < 2 => 1
  |=> (n - 2) fib + (n - 1) fib
].

5 fib echo
```

#### Is even
```F#
int isEven = [
  | self % 2 == 0 => true
  |=> false
].

5 isEven echo.
4 isEven echo.
```

#### Function call
There 3 types
```F#
5 factorial //unary 
5 + 5       // binary (only math symbols allowed)
map at: "key" put: "value" // keyword
```


-----

# Basic

### Type Declaration [Done]
Just like keyword message in Smalltalk
```F#
type Person name: string job: string.
```

### Method Declaration [Done]
This is exactly the same as in Smalltalk.  
The first is an identifier of the type for which the method is declared, then the signature and the body.
```F#
//Unary
int factorial = []
// 4 factrial

//Binary
string / pathPart  = []
// userName = "user"
// "home"/userName/"Documents"
 
//Keyword
Random from: start to: end = []
// random from: 1 to: 10
```
Where the types? You can specify them for each argument as `::type` and return type as `-> type`
```F# 
int factorial -> int = [ ]
Circle + circle::Circle -> Circle = [ ]
Person renameTo: name::string = [ ]
```
But this is not necessary. Types that are not specified will be deduced from the places of use. If a function is called from different places with different types, it will be monomorphized. So the absence of declared types is zero cost in runtime. There only one case when you need to declare type: return type of recurcive functions. 

Also body can be single expression declared without brackets.


### Return statement
```Smalltalk
Math pi -> float = [
  ^ 3.1415926
]
// or
Math pi -> float = [
  3.1415926
]
// or
Math pi = 3.1415926
```
Last expresion returns, so return operator needed only for [early return](https://en.wikipedia.org/wiki/Guard_(computer_science)). 

### Protocols (WIP, need syntax design)
So declarating many methods for same type can looks a bit ugly:
```Swift
Person hasPassport -> bool = []
Person == person::Person -> bool = []
Person inRelationShip: relationType with: person = []
```
So there way to group methods declaration(just like protocoks in Smalltalk)  
Maybe I will find a better syntax:
```Swift
protocol Name for Type 
  methods
```
If you have ideas pls create issue
```Rust
protocol getInformation for Person 
  hasPassport -> bool = []
  == person::Person -> bool = []
  inRelationship: relationType with: person = []
  addRelationshipOfType: relationType with: person = []
```
### Send message (function call) [Done]
```F#
person renameTo: "Alice".
```
### Create instance of Person [WIP]
Just like keyword message to Type
```haskell 
person = Person name: "sas" job: "programmer".
```
### Change fields [WIP, need design]
I think it will be allowed to change the fields only inside the methods of the type.
```F#
type Point x: int y: int.
...
[
  // set field
  point x: 4
  // get field
  point x
]

```
Of course they are inlined
### Code Blocks [WIP]
It's just like lambda.  
Here how to declare type of code block  
`[argsTypes | returnType]`  
To 
```F#
Foo do: code::[int string | int] -> int = [
  code value: 4 value: "string argument" // apply code block
]
```

### Generic Type [Done]
```F#
type Person = name, job: string. // name is generic
```

### Control flow [Done]
Unlike Smalltalk, I decided to include syntax for control flow  
May remind Haskell a bit
```
| caseExpr => thenDoExpr
| ...
|=> elseExpr
```
So else branch is just a regular branch without `caseExpr`  
It can be used as an expression as well as a statement:  

```Haskell
| x > 100  => "sas" echo
| x < 10   => "sus" echo
| x isEven => "even" echo.

y = 
| x > 0 => 5
| x < 0 => 7
|=> 0.

// 
y 
| 5 => "x = 5" echo
| 7 => "x = 7" echo
|=> "x is neither 5 nor 7" echo
```

### Modules [need desing]
There no such thing as modules in Smalltalk, so I still thinking how to better implement that in kinda smalltalk like style.

### Live comments [WIP]
This is a replacement for the ability of a small thread to select any part of the code and execute it. A way to show callstack may be added.  
```F#
x = 42.
x isEven. //!
```
after compile
```F#
x = 42.
x isEven. //! true
```
No need to debug printing anymore.

### Live unit tests [WIP, need design]
Smalltalk contains great features for TDD and even DDD, niva should have them too.  
Write the tests first.

I took this idea from NewSpeak [YouTube demo](https://youtu.be/SG08kxIIlHQ?t=3442) (there are more examples of that in this talk)

Simple example:
```F#
//# 4 addOne -> 5 
//# 5 addOne -> 6 
-int addOne -> int = self + 1
```
after compile
```F#
//# 4 addOne -> 5 ✔
//# 5 addOne -> 4 🞮
-int addOne -> int = self + 1
```


# Message cascading [WIP]
This feature is directly from smalltalk. Its the same as [Clojure doto](https://clojuredocs.org/clojure.core/doto) or [Pascal With Do](https://www.freepascal.org/docs-html/ref/refsu62.html) or [Kotlin with](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/with.html), many langs has something like that:
`;` are cascade operator, that mean the message will be send to the first receiver
```F#
c = {}. // empty list
c add: 1; add: 2; add: 3
```
is the same as 
```F#
c = {}.
c add: 1.
c add: 2.
c add: 3.
```
That is, imagine that you have an add function that takes 2 numbers and returns their sum
C lang
```C
int add(int a, int b) {
  return a + b;
}
```
If you chain functions like this
```C
add(1, add(2, add(3, 4)))
```
you will get 1 + 2 + 3 + 4  
and if you wanna apply all functions to the same value you will need:
```C
int a = 0;
a = add(3, 4);
a = add(a, 2);
a = add(a, 1);
``` 
Lest imagine its Java, and int itself has add method then:
```Java
int a = 0;
a = a.add(1).add(2).add(3).add(4);
```
will give you 1, because each call after `add(1)` was applied to the result of the previous call, and not to the original variable a.  
So to get fluent desing in Java like languages you need to return this or new instance of this on each method. 

So the same on niva will looks like:
Niva
```Smalltalk
-int add: b -> int = [ 
  self + b 
]
a = add: 1; add: 2; add: 3; add 4.
```
This is very convenient for chaining any calls. Since setting field values is also a message, you can do this:
```Smalltalk
person = Person name: "Alice" age: 42.
person name: "Bob"; age: 43. // send message name and age to person

boxWidget = Box width: 40 height: 50.
boxWidget 
  add: Label text: "hello"; 
  add: Button text: "press me";
  height: 100;
```


# Tagged unions 
Declaration:  
```C
union Shape area: int =
| Something
| Rectangle => width: int height: int
| Circle    => radius: int

rectangle = Rectangle area: 42 width: 7 height: 6

rectangle area echo
rectangle width echo
rectangle height echo
```
Every branch is usual type, so you can create them separately.
```C#
Rectangle area: 42 width: 7 height: 6
```
Every branch here has an area field.

# Collections 
I almost stole Rich Hickey's syntax, I hope he won't be offended ^_^
```lua
listLiteral = 
  | "{" spaces "}" -- emptyList
  | "{" listElements "}" -- notEmptyList
listElements = primary whiteSpaces (","? spaces primary)*

hashSetLiteral = 
  | "#{" spaces "}" -- emptyHashSet
  | "#{" listElements "}" -- notEmptyHashSet
```
As you can see from that grammar, commas between elements are optional.

## List
```C#
list = {1 2 3 4}
list add: 5  //! {1 2 3 4 5}
list at: 0   //! 1
list copy at: 0 put: 5 //! {5 2 3 4 5}
list //! {1 2 3 4 5}
```
## Map
```C#
x = #{"sas" 1, "sus" 2}
x at: "ses" put: 3
```
## Set
```Rust
set = #{1 2 3}
set add: 2 //! #{1 2 3}
set add: 4 //! #{1 2 3 4}
set has: 3 //! true
```

### Type System
Now the nominal one is being used, I plan to conduct performance tests, and if it doesn't hurt to switch to structural typing to add more dynamism, or give the user the opportunity to choose hmm.   
All types now only on nim side, yet.
### Distinct type [WIP need design]
If type assigned to one of base types then its distinct type
```F#
type Path = string
```
`Path` is not compatible with the string type, that is, it is not suitable as an string argument. 

# Smalltalk inspiration
### But wait, Smalltalk was a live programming enviroment
When core of the lang will ready I plan to add gui with class browser just like in Smalltalk, so the syntax of method declaration will starts from signature.  
[Here](https://github.com/gavr123456789/Katana) my last GUI project. (Check the video demos)  
I think it will look similar, only with classes and methods instead of a folder hierarchy.  
Here picture of Smalltalk Browser if you are not familiar with it:  

![image](https://user-images.githubusercontent.com/30507409/172440820-b10d0174-a0cc-472f-ace9-17bb5be771e9.png)
![image](https://user-images.githubusercontent.com/30507409/172058279-e1666c93-fee2-4909-a7a8-1ef97b5a89dd.png)

### Does Not Understand [need design]
I plan to make a way to return this functionality to a statically typed language. There will be a way to define the method in a special way, in which the signatures of messages that are called over this object will come to this method, and not just arguments as usual.  
For example:
`someObj foo; bar.`
This special method of `someObj` will get `foo` and `bar` as arguments. 

# How to try
```sh
git clone https://github.com/gavr123456789/Niva.git
cd niva
yarn
yarn test 
yarn ts-node ./src/main.ts ./yourfile.niva
```

# Code examples

## Working now
#### Fibonacci
```rust
-int fib -> int = [
  n = self.
  | n < 2 => 1
  |=> (n - 2) fib + (n - 1) fib
].

5 fib echo
```

#### Is Even
```rust
-int isEven = [
  | self % 2 == 0 => true
  |=> false
].

5 isEven echo.
4 isEven echo.
```

## Can be implemented

#### Get the HTML source of a web page
```F#
"http://www.pharo.org" asUrl retrieveContents
```

#### Compute difference in days between two dates
```F#
('2014-07-01' asDate - '2013/2/1' asDate) days
```

#### Decimal digit length of 42!
```F#
42 factorial decimalDigitLength
```

#### Set up an HTTP server that returns the current timestamp
```Smalltalk
(Server startDefaultOn: 8080) 
  onRequestRespond: [ request | 
    Response ok: (Entity with: (DateAndTime now asString)) ]
```
#### Split a string on dashes, reverse the order of the elements and join them using slashes

```F#
'/' join: ('1969-07-20' split: '-') reverse // 20/07/1969
```

#### Test whether one set is included in another one
```Swift
#{a b c d e f} includesAll: #{f d b}
```

#### Generate a random string
```Rust
(string new: 32) map: [ ('a'..'z') atRandom ]
```

#### Count the number of the leap years between two years
```Smalltalk
(1914 to: 1945) count: [ it isLeapYear ].
```
