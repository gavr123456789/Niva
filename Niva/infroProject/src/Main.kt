package mainNiva

// STD


class Error {
    companion object
}
fun Error.Companion.throwWithMessage(message: String): Nothing {
    throw kotlin.Exception(message)
}

inline fun Any?.echo() = println(this)
const val INLINE_REPL = """/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/inline_repl.txt""" 

inline fun IntRange.forEach(action: (Int) -> Unit) {
    for (element in this) action(element)
}

// for cycle
inline fun Int.toDo(to: Int, `do`: (Int) -> Unit) {
    for (element in this.rangeTo(to)) `do`(element)
}

inline fun Int.untilDo(until: Int, `do`: (Int) -> Unit) {
    for (element in this.rangeUntil(until)) `do`(element)
}

inline fun Int.downToDo(down: Int, `do`: (Int) -> Unit) {
    for (element in this.downTo(down)) `do`(element)
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

operator fun <K, V> MutableMap<out K, V>.plus(map: MutableMap<out K, V>): MutableMap<K, V> =
    LinkedHashMap(this).apply { putAll(map) }



inline fun Boolean.isFalse() = !this
inline fun Boolean.isTrue() = this

// end of STD

fun main() {
    val truly = {t: () -> Unit, f: () -> Unit, -> t()}
    val falsy = {t: () -> Unit, f: () -> Unit, -> f()}
    val ify = {c: (() -> Unit,() -> Unit,) -> Unit, t: () -> Unit, f: () -> Unit, -> (c)(t, f)}
    (ify)(truly, {"true".echo()}, {"false".echo()})
    (ify)(falsy, {"true".echo()}, {"false".echo()})
    
}

