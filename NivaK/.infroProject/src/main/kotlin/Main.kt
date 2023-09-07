// STD
fun Any?.echo() = println(this)

inline fun IntRange.forEach(action: (Int) -> Unit) {
    for (element in this) action(element)
}

// for cycle
inline fun Int.toDo(to: Int, `do`: (Int) -> Unit) {
    val range = this.rangeTo(to)
    for (element in range) `do`(element)
}

inline fun Int.downToDo(down: Int, `do`: (Int) -> Unit) {
    val range = this.downTo(down)
    for (element in range) `do`(element)
}

// while cycles
typealias WhileIf = () -> Boolean

inline fun <T> WhileIf.whileTrue(x: () -> T) {
    while (this()) {
        x()
    }
}

inline fun <T> WhileIf.whileFalse(x: () -> T) {
    while (!this()) {
        x()
    }
}
// end of STD

fun main() {
    val person = Person.newborn()
    (person).person(Person.newborn())
    
}
