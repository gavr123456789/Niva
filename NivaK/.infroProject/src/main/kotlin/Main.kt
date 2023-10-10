package main
import common.*

// STD
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException

inline fun Any?.echo() = println(this)
const val INLINE_REPL = """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/inline_repl.txt""" 

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
    val person = Person("sas")
    person.buy()
    
}

