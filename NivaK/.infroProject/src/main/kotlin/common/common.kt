package common

import main.*
import common.*
class Person(var name: String) {
	override fun toString(): String {
		return "Person name: $name"    
    }
    companion object
}
fun Person.buy() {
    val wallet = Wallet(23)
    wallet.something()
    "buy".echo()
}

class Wallet(var money: Int) {
	override fun toString(): String {
		return "Wallet money: $money"    
    }
    companion object
}
fun Wallet.something() = "wallet".echo()

