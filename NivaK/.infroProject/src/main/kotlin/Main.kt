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
    var sum = 0
    var num = 20000
    val range = 1 .. 5
    (((1 .. 5).step(2))).forEach({it.echo()})
    inlineRepl(range, """/home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/src/examples/Main/main.niva:::37""", 1)
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

