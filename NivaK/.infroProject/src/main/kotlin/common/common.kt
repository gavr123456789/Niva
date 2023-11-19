package common

import main.*
class Person(var name: String) {
	override fun toString(): String {
		return "Person name: $name"    
    }
    companion object
}
fun Person.from(from: String) {
    this.name = (from)
}

