package main

// STD
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException

class Error {
    companion object
}
fun Error.Companion.throwWithMessage(message: String): Nothing {
    throw kotlin.Exception(message)
}

inline fun Any?.echo() = println(this)
const val INLINE_REPL = """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/inline_repl.txt""" 

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


fun <T> inlineRepl(x: T, pathToNivaFileAndLine: String, count: Int): T {
    val q = x.toString()
    // x/y/z.niva:6 5
    val content = pathToNivaFileAndLine + "|||" + q + "***" + count

    try {
        val writer = BufferedWriter(FileWriter(INLINE_REPL, true))
        writer.append(content)
        writer.newLine()
        writer.close()
    } catch (e: IOException) {
        println("File error" + e.message)
    }

    return x
}

inline fun Boolean.isFalse() = !this
inline fun Boolean.isTrue() = this

// end of STD

fun main() {
    val set1 = mutableSetOf(1, 2, 3)
    val set2 = mutableSetOf(2, 3, 4)
    val intersect1 = (set1).intersect(set2)
    inlineRepl(intersect1, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::25""", 1)
    val set3 = mutableSetOf("foo", "foobar")
    val set4 = mutableSetOf("bar", "foobar")
    val intersect2 = (set3).intersect(set4)
    inlineRepl(intersect2, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::32""", 1)
    
}
