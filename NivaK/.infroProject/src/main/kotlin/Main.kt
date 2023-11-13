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
// end of STD

fun main() {
    val map1 = mutableMapOf(1 to 2, 3 to 4)
    val map2 = mutableMapOf(5 to 6)
    val map3 = map1 + map2
    inlineRepl(map3, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::8""", 1)
    val map4 = map3 - 3
    inlineRepl(map4, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::12""", 1)
    val set1 = mutableSetOf(1, 2, 3)
    val set2 = mutableSetOf(3, 4, 5)
    val set3 = set1 + set2
    inlineRepl(set3, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::18""", 1)
    val set4 = set3 - mutableSetOf(4, 5)
    inlineRepl(set4, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::22""", 1)
    (set1).add(10)
    (set1).remove(1)
    inlineRepl(set1, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::25""", 1)
    var sum = 0
    var num = 20000
    (1).untilDo(num, {i: Int, -> 
        (1).untilDo(num, {j: Int, -> if (num % j == 0) {
                sum = sum + j
        
        }})
        if (num == sum) {
            sum.echo()
        }
        sum = 0
        num = num.dec()
    })
    
}

