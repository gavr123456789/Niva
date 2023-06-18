fun Any?.echo() = println(this)
fun main() {
    fun Int.sas() = this.echo()

    1.sas()
    val x = "Hello" + " World" + " from Niva!"
    x.echo()
    if (x.count() < 5) {
        x.count().echo()
    } else if (x.count() == 22) {
        val y = x.count() + 20
        y.echo()

    } else {
        "count < 10".echo()
    }

}
