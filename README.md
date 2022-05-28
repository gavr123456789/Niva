# Niva
It will be Smalltlak like language, but statically typed

# Basic

### Type Declaration 
```F#
type Person name: string job: string.
```

### Function Declaration
```F#
-Person renameTo: name::string = [ self name: name ].
```

### Create instance of Person
```F#
person = Person name: "sas" job: "programmer".
```

### Send message to person(function call)
```F#
person renameTo: "Alice".
```

### Generic Type
```F#
type Person = name, job: string. // name is T
```
