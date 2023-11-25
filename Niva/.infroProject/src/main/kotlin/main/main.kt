package main

import mainNiva.*
class Person(var name: String, var age: Int) {
	override fun toString(): String {
		return "Person name: $name age: $age"    
    }
    companion object
}
fun Person.say() = (name + " saying Hello World!").echo()

