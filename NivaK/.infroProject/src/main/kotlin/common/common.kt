class Person(var age: Int) { companion object }
operator fun Person.plus(x: Int) {
    this.age = (this.age + x)
}

