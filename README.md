
<img align="left" width="96px" height="96px" src="Niva/niva-icon.svg" />  

# Niva
It's Smalltalk like language, but statically typed.  

<img src="https://github.com/gavr123456789/Niva/assets/30507409/55f54aa9-760b-43ea-9518-33b06b07cc8a" width=30% height=30%>

<img src="https://github.com/gavr123456789/Niva/assets/30507409/7dbdf939-cb71-4459-8a74-4d50fce2c9d3" width=30% height=30%>

## Name
So far I've chosen niva because my 2 favorite static languages are nim and vala.

## Some strange demo
https://github.com/user-attachments/assets/dfeef595-685f-4124-8eaf-9956919e07af

## Examples

You can start from some fibonacci [here](https://github.com/gavr123456789/Niva/tree/main/Niva/Niva/examples/Algoritms)  
And in [this](https://github.com/gavr123456789/writing-an-interpreter-in-niva) repo you can find WIP implementation of interpreter from the book 

## Bullet points
- amm
- ...
- bla bla bla simplicity
- OOP - inheritance - late bindings + some FP(unions, matching, immutability by default)
- kinda scripting experience `"Hello World!" echo`
- Smalltalk Syntax
- Inline REPL
- Imports inference
- JVM/Kotlin interop
- Smalltalk syntax
- Editor support with [LSP](https://github.com/gavr123456789/vaLSe) and [vscode](https://github.com/gavr123456789/niva-vscode-bundle)/zed(not public) plugin

## Compile from sources 
Run in Niva/Niva folder:
`./gradlew buildJvmNiva` - will create jvm based binary in ~/.niva/niva/bin, u can add it in path

### Getting native binary with GraalVM
If you have graalvm in your JAVA_HOME then run:  
`./gradlew buildNativeNiva` this will create native binary in ~/.niva/bin  
 

### How to install GraalVM
Arch: `yay -S jdk22-graalvm-bin`  
`archlinux-java status`  
`archlinux-java set <JAVA_ENV_NAME>`  
[select java on arch](https://wiki.archlinux.org/title/Java#Switching_between_JVM)  
  
macOS: `brew install --cask graalvm-jdk`  
`/usr/libexec/java_home -V`  
`export JAVA_HOME='/usr/libexec/java_home -v 22.0.2'`  
[select java on mac os](https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-macos)  
If you have `expanded from macro 'NS_FORMAT_ARGUMENT'` problem with buildNativeNiva on macOS then [update XCode](https://wails.io/docs/guides/troubleshooting/#my-mac-app-gives-me-weird-compilation-errors)
`xcode-select -p && sudo xcode-select --switch /Library/Developer/CommandLineTools`

## VSCode extension
[VS Code extension](https://github.com/gavr123456789/niva-vscode-bundle) full lsp support with autocompletion, error highlighting, goto definitions


## Nix-Shell Setup

To get started with Niva, use the provided shell.nix file.  

  Enter the following command:

  ```bash
  nix-shell
  ```

This command will set up the necessary dependencies and run the compile script to produce a binary file.  
Afterwards, you can run the Niva compiler with the following command:
```bash
./niva_compiler/niva <file>
```

# Core


Almost everything in this lang is message send(function call), because of that there are 3 ways of doing it(don't worry, none of them requires parentheses). 

#### Hello world
Everything is sending a message to an object. 
There no such things as `function(arg)`, only `object message`
```F#
"Hello world" echo // print is a message for String
```
You can send as many messages as you want 
```Scala
"12.08.2009" asDate days echo // not real, just syntax example
```

Okay, but what if the message has some arguments?  
Just separate them with colons, this is called a keyword message:  
```Scala
obj message: argument

1 to: 5 // oh, we just created nice range syntax
```
And what about many arguments?  
Easy  
```Scala
1 to: 5 do: [ it echo ] // 1 2 3 4 5
```
aand we don't need things like hardcoded for/while/do_while loops in language anymore, the second argument here is a code block.

Here some more examples:
```Scala
5 factorial // unary 
5 + 5       // binary (only math symbols allowed)
map at: "key" put: "value" // keyword
1..5 forEach: [it echo] // all together + codeblock
```

#### Type and methods declaration
Niva is statically typed language, so we need a way to declare custom types, here it is, just the syntax of keyword messages with type keyword.  

Each type automatically has a constructor that represents as the same keyword message, isn't it beautiful?
```Scala
// Declare type Person with 2 fields and create obj
type Person name: String age: Int
person = Person name: "Bob" age: 42
```
To declare method for type just type `Type function_signature = body`
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


In the last example you can see a problem, we dont need local names for from and to.
Because of that there are another way to declare keyword message:
```Scala
Random from::Int to::Int = ... // `from` and `to` are local arguments
```

All methods are extension methods(like in Kotlin or C#), so you can add new methods for default types like `Int`
You can also define many methods at once
```Scala
extend Int [
    on increment = this + 1
    on decrement = this - 1
]

// instead of
Int increment = this + 1
Int decrement = this - 1
```

# Control frow

## Messages
```Scala
1 < 2 ifTrue: ["yes!" echo]
1 > 2 ifFalse: ["no!" echo]
// `ifTrue:ifFalse:` is single keyword message with 2 arguments and boolean receiver
1 > 2 ifTrue: [...] ifFalse: [...]
// ifTrue:ifFalse: can be used as expression
x = 42 < 69 ifTrue: [1] ifFalse: [0] // x == 1
```
There are no special syntax for loops, only messages
```Scala
// Loops
// collections have forEach method that takes block
{1 2 3} forEach: [it echo] // 1 2 3
{1 2 3} forEachIndexed: [i, it -> i echo] // 0 1 2
// IntRange
1..3 forEach: [it echo]  // 1 2 3
1..<3 forEach: [it echo] // 1 2 
```



## Syntax

### If
```Scala
// single expression
1 < 2 => "yes!" echo
// with body
1 < 2 => [
  ...
]
// with else
1 > 2 => "yes" echo |=> "no" echo
// as expression
x = 1 > 2 => "yes" |=> "no" // x == "no"
```
### Switch
```Scala
// multiline switch
x = 1
| x
| 1 => "switch 1" echo
| 2 => "switch 2" echo
|=> "what?" echo

// single line switch
m = |x| 1 => x inc | 2 => x dec |=> 0
// m == 2 because of x inc
```
### If Elif Else chain
There is no elseIf syntax but you can chain simple then-else
```Scala
x = 1

y = x > 2 => ">2" |=>
x < 2 => "<2" |=>
"???"
// y == "<2"
```


#### Program example: Factorial
`factorial` is a message for `Int` type, that returns `Int`.
`self` is context-dependent variable that represents Int on which factorial is called.
The whole function is one expression, we pattern matching on self with `|` operator that acts as swith expression here.
```Scala
// switching on this(Int receiver)
Int factorial -> Int = | this
| 0 => 1
|=> (this - 1) factorial * this

5 factorial echo
```

#### Is even
```F#
Int isEven =
  this % 2 == 0 => true
  |=> false

5 isEven echo
4 isEven echo
```

#### Function call
There 3 types
```F#
5 factorial //unary 
5 + 5       // binary (only math symbols allowed)
map at: "key" put: "value" // keyword
```

## Message cascading [Need design]
This feature is directly from smalltalk. Its the same as [Clojure doto](https://clojuredocs.org/clojure.core/doto) or [Pascal With Do](https://www.freepascal.org/docs-html/ref/refsu62.html) or [Kotlin with](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/with.html), many langs has something like that:
`;` is cascade operator, that mean the message will be send to the first receiver, not to the result of the previous one
```F#
c = {}. // empty list
c add: 1; add: 2; add: 3
```
is the same as 
```F#
c = {}.
c add: 1
c add: 2
c add: 3
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
```C
int a = 0;
a = a.add(1).add(2).add(3).add(4);
```
will give you 1, because each call after `add(1)` was applied to the result of the previous call, and not to the original variable a.  
So to get fluent desing in Java like languages you need to return this or new instance of this on each method. 

So the same on niva will looks like:
```Scala
Int add: b -> Int = [ 
  self + b 
]
a = 0 add: 1; add: 2; add: 3; add 4 // 10
```
This is very convenient for chaining any calls. Since setting field values is also a message, you can do this:
```Scala
person = Person name: "Alice" age: 42
person name: "Bob"; age: 43 // send message name and age to person

boxWidget = Box width: 40 height: 50
boxWidget 
  add: Label text: "hello"; 
  add: Button text: "press me";
  height: 100;
```

## Code blocks

You can create code block like this:
```Scala
block = ["hello from block" print]
```
To evaluate block send value message to it:
```Scala
block value // hello from block printed
```
Block with arguments: 
```Scala
block::[Int -> Int] = [x -> x + 1]
block value: 1 // 2
```
Many args -> many value messages:
```Scala
block::[Int, Int, Int -> Int] = [x y z-> x + y + z]
block value: 1 value: 2 value: 3 // 6
```
If you have a better ideas how to send many arguments to blocks please make an issue.
I have anoter variant in mind with named args
```Scala
block::[Int -> Int] = [it + 1]
block it: 1 // 2

block x: 1 y: 2 z: 3 // 6

```


# Tagged unions 
Declaration:  
```C
union Shape area: Int =
| Rectangle => width: Int height: Int
| Circle    => radius: Int

rectangle = Rectangle area: 42 width: 7 height: 6

rectangle area echo
rectangle width echo
rectangle height echo
```
Both Rectangle and Circle has area field.
Every branch is usual type, so you can create them separately.
```C#
Rectangle area: 42 width: 7 height: 6
```
Every branch here has an area field.

## Built-in collection literals
 
I almost stole Rich Hickey's syntax, I hope he won't be offended `^_^`
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

## Backend
Current backend is Kotlin, because you get 4 backends for free - JVM, Native, JS, Wasm, also ecosystem is rich.
A lot of pet-project languages are translated into js, which is very high-level, so why not be translated into a real language.

## Compile from sources old

P.S. u can find binary releases in the releases  
After running compile.sh you will get niva_compiler folder that contains jvm or native binary.

### JVM

1) `sh compile.sh jvm`
2) run compiler from bin folder

### Native

1) install graalvm `yay -S jdk21-graalvm-bin` and set it default: `sudo archlinux-java set java-21-graalvm` on
   Arch, `nix shell nixpkgs#graalvm-ce` on nix
2) `sh compile.sh bin`

### Usage

Niva can eat .niva and .scala files, because Scala highlight fits well for Niva :3
`niva main.niva` - compile and run  
`niva main.niva -с` - compile only, will create binary for native target and fat-jar for jvm
`niva main.niva -i > info.md` - will generate info about all code base of the projects, `-iu` - only user defined files
`niva run` - run all files in current folder with main.niva as entry point
`niva build` - produce binary



