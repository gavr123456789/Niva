# Niva
It will be Smalltlak like language, but statically typed.

## Backend
Now the backend is Nim. Yes it's quite fun `niva` -> `nim` -> `C`.  
I made this decision because I saw how many new languages die quickly, due to lack of time or being disappointed in ideas, so I decided to transpile into a language of a sufficiently high level so as not to mess with LLVM and be able to quickly test ideas. Also nim gives free Js transpile and simple interaction with `C`, `C++` and `Js`.  
  
A lot of languages are translated into js, which is very high-level, so why not be translated into a real language.

P.S. now realization written in ts, in future self ... are planned.

## Name
I haven't decided on the final name yet, so far I've chosen niva because my 2 favorite static languages are nim and vala.

# Basic

### Type Declaration [Done]
Just like keyword message in Smalltalk
```F#
type Person name: string job: string.
```

### Type System
Now the nominal one is being used, I plan to conduct performance tests, and if it doesn't hurt to switch to structural typing to add more dynamism, or give the user the opportunity to choose hmm.

### Method Declaration [Done]
This is exactly the same as in Smalltalk.  
The first is an identifier of the type for which the method is declared, then the signature and the body.
```F#
//Unary
-int factorial = []
// 4 factrial

//Binary
-string / pathPart  = []
// userName = "user"
// "home"/userName/"Documents"
 
//Keyword
-Random from: start to: end = []
// random from: 1 to: 10
```
Where the types? You can specify them for each argument as `::type` and return type as `-> type`
```F# 
-int factorial -> int = [ ]
-Circle + circle::Circle -> Circle = [ ]
-Person renameTo: name::string = [ ]
```
But this is not necessary. Types that are not specified will be deduced from the places of use. If a function is called from different places with different types, it will be monomorphized. So the absence of declared types is zero cost in runtime. There only one case when you need to declare type: return type of recurcive functions. 

Also body can be single expression declared without brackets.


### Return statement
```Smalltalk
-Math pi -> float = [
  ^ 3.1415926
]
// or
-Math pi -> float = [
  3.1415926
]
// or
-Math pi = 3.1415926
```
Last expresion returns, so return operator needed only for [early return](https://en.wikipedia.org/wiki/Guard_(computer_science)). 

### Protocols (WIP, need syntax design)
So declarating many methods for same type can looks a bit ugly:
```Swift
-Person hasPassport -> bool = []
-Person == person::Person -> bool = []
-Person inRelationShip: relationType with: person = []
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
### Send message to person (function call) [Done]
```F#
person renameTo: "Alice".
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

### Modules (need desing)
There no such thing as modules in Smalltalk, so I still thinking how to better implement that in kinda smalltalk like style.

### 

### Live comments (WIP)
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

### Live unit tests (WIP, need design)
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

# Code examples

Fibonacci [works]
```rust
-int fib -> int = [
  n = self.
  | n < 2 => 1
  |=> (n - 2) fib + (n - 1) fib
].

5 fib echo
```


# Collections (WIP)
I almost stole Rich Hickey's syntax, I hope he won't be offended.
```lua
listLiteral = 
  | "{" spaces "}" -- emptyList
  | "{" listElements "}" -- notEmptyList
listElements = primary whiteSpaces (","? spaces primary)*

mapLiteral = 
  | "{" spaces "}" -- emptyMap
  | "{" mapElements "}" -- notEmptyList
mapElements = mapElement (","? spaces mapElement)*
mapElement = ":"primary whiteSpaces primary

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
map = {:"one" 1 :"two" 2 :"three" 3}
map add: 5 //! {1 2 3 4 5}
map get: 0 //! 1
```
## Set
```Rust
set = #{1 2 3}
set add: 2 //! #{1 2 3}
set add: 4 //! #{1 2 3 4}
set has: 3 //! true
```

# Smalltalk inspiration
### But wait, Smalltalk was a live programming enviroment
When core of the lang will ready I plan to add gui with class browser just like in Smalltalk, so the syntax of method declaration will starts from signature.  
[Here](https://github.com/gavr123456789/Katana) my last GUI project. (Check the video demos)  
I think it will look similar, only with classes and methods instead of a folder hierarchy.  
Here picture of Smalltalk Browser if you are not familiar with it:  

![Smalltalk Browser](https://www.researchgate.net/profile/Stephane-Ducasse/publication/40637510/figure/fig4/AS:669413896380430@1536612118655/The-browser-showing-the-printString-method-of-class-object.png)


# How to try
```sh
git clone https://github.com/gavr123456789/Niva.git
cd niva
yarn
yarn test 
yarn ts-node ./src/main.ts ./yourfile.niva
```
