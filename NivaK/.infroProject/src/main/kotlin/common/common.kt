class Person(var name: String) {
	override fun toString(): String {
		return "Person name: $name"    
    }
    companion object
}
class Wallet(var money: Int) {
	override fun toString(): String {
		return "Wallet money: $money"    
    }
    companion object
}
fun Person.buy() = 1.echo()

fun Wallet.something() = 1.echo()

