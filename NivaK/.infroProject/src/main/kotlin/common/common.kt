class Wallet(var money: Int) { companion object }
fun Wallet.Companion.empty() = Wallet(0)

fun Wallet.empty() {
    this.money = (0)
}

fun Float.Companion.pi() = 3.14

