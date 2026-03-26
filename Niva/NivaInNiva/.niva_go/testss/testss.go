package main

import (
    "niva/core"
)

type Person struct {
  Name string
  Age int
}

func Person__hello(this *Person) {
  name := this.Name
  _ = name
  age := this.Age
  _ = age
  core.Any__echo(((("hello my name is 123 ") + (name))))
}


func main() {
  p := &Person{Name: "Alice", Age: 24}
  _ = p
  Person__hello(p)
  list := []int{ 1, 2, 3 }
  _ = list
  hashSet := map[int]struct{}{1: struct{}{}, 2: struct{}{}, 3: struct{}{}}
  _ = hashSet
  hashMap := map[int]int{1: 2, 3: 4}
  _ = hashMap
  core.Any__echo(list)
  core.Any__echo(hashSet)
  core.Any__echo(hashMap)
  core.List__forEach((core.List__filter((core.List__map(list, func(it int) (result int) {
    return ((it) * (2))
  })), func(it int) (result bool) {
    return ((((it) % (2))) == (0))
  })), func(it int) {
    core.Any__echo(it)
  })
  _ = core.Bool__ifTrue__ifFalse(((1) > (2)), func() (result int) {
    return 3
  }, func() (result int) {
    return 4
  })
  _ = (((((1) > (2)))) && ((((2) > (1)))))
}
