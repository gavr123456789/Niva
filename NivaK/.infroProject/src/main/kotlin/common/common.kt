package common

import main.*
class Person(var name: String, var age: Int) {
	override fun toString(): String {
		return "Person name: $name age: $age"    
    }
    companion object
}
