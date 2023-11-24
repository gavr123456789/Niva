package main

import mainNiva.*
import person.*
import a.*
import b.*
class Person(var name: String, var age: Int) {
	override fun toString(): String {
		return "Person name: $name age: $age"    
    }
    companion object
}
