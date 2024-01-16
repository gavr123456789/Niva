@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.compileError
import frontend.util.createFakeToken
import frontend.util.div
import frontend.util.fillSymbolTable
import main.utils.Compiler
import main.utils.compileProjFromFile
import java.io.*


const val RESET = "\u001B[0m"
const val BLACK = "\u001B[30m"
const val RED = "\u001B[31m"
const val GREEN = "\u001B[32m"
const val YEL = "\u001B[33m"
const val BLUE = "\u001B[34m"
const val PURP = "\u001B[35m"
const val CYAN = "\u001B[36m"
const val WHITE = "\u001B[37m"

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    lexer.fillSymbolTable()
    return lexer.lex()
}


//
//sealed class Option<out T>
//class Some<T>(var value: T) : Option<T>()
//data object None : Option<Nothing>()
//
//class Node<T>(
//    val data: T,
//    var prev: Option<Node<T>>
//)
//
//fun <T> Node<T>.toList(): List<T> {
//    val result = mutableListOf<T>(data)
//    var q = prev
//    while (q != None) {
//        when (q) {
//            is None -> {}
//            is Some -> {
//                result.add(q.value.data)
//                q = q.value.prev
//            }
//        }
//    }
//    return result
//}
//
//class MyList<T>(
//    val initialVal: T,
//    var head: Node<T> = Node(initialVal, None)
//)
//
//// 1 next: []
//// 1 next: [2 next: []]
//
//fun <T> MyList<T>.add(data: T) {
//    val result = Node(data = data, prev = Some(head))
//    head = result
//}

const val HELP = """
Usage:
    ${WHITE}FILE$RESET — compile and run single file
    ${WHITE}run$RESET — compile and run project from "main" file
    ${WHITE}run FILE$RESET — compile and run project from root file
    ${WHITE}build$RESET — compile only(creates binary in current folder)
    ${WHITE}info$RESET or ${WHITE}i$RESET — get info about packages
    ${WHITE}infoUserOnly$RESET or ${WHITE}iu$RESET — get info about user defined packages

Flags for single file run:
    -c      — compile only(creates binary in current folder)
    -i      — get info about packages(it is usable to pipe it to .md file)
    -iu     — print info only about user-defined types
    -i pkg  — print info only about specific pkg

In code: 
    > EXPR  — inline print result of expression in comment above
    >? TYPE — print all info about TYPE

Project configuration:
    Messages for ${YEL}Project$RESET:
    ${CYAN}target: $GREEN"TARGET"$RESET — target to jvm/linux/macos/windows(not supported yet)
    ${CYAN}mode: $GREEN"MODE"$RESET     — debug/release only for native targets, use debug for faster compilation
    
    ${CYAN}package: $GREEN"PKG"$RESET   — set package for the definitions in code below
    ${CYAN}protocol: $GREEN"NAME"$RESET — set protocol for the definitions in code below
    ${CYAN}use: $GREEN"PKG"$RESET       — set default pkg, like using namespace in C#/Vala
    
    Example: ${YEL}Project ${CYAN}target: $GREEN"linux" ${CYAN}mode: $GREEN"debug"$RESET 

Kotlin\Java interop:
    Messages for ${YEL}Bind$RESET:
    ${CYAN}package: $GREEN"PKG"$RESET  — bind package
    ${CYAN}content: $WHITE[CODE]$RESET — bindings
    
    Example:
    ${YEL}Bind ${CYAN}package: $GREEN"java.io" ${CYAN}content: $RESET[
        ${RED}type ${YEL}File ${CYAN}pathname: ${YEL}String
        ${YEL}File ${CYAN}exists ${RED}-> ${YEL}Boolean
        ${YEL}File ${CYAN}readText ${RED}-> ${YEL}String
    $RESET]
    ${WHITE}file = ${YEL}File ${CYAN}pathname: $GREEN"path/to/file"
    ${WHITE}text = ${WHITE}file ${CYAN}readText$RESET
    
    Messages for ${YEL}Project$RESET:
    ${CYAN}loadPackages: $RESET{$GREEN"PKG1" "PKG2"$RESET} — load package from Maven Central
    ${CYAN}import: $GREEN"PATH_TO_PKG"$RESET — add direct import to generated code

"""


enum class MainArgument {
    BUIlD,
    RUN,
    SINGLE_FILE_PATH,
    INFO_ONLY, // only means no kotlin compilation
    USER_DEFINED_INFO_ONLY,
    RUN_FROM_IDE
}


class PathManager(val args: Array<String>, mainArg: MainArgument) {

    val pathToInfroProject = System.getProperty("user.home") / ".niva" / "infroProject"

    val pathWhereToGenerateKtAmper = pathToInfroProject / "src"
    val mainNivaFile = File("examples" / "Main" / "main.niva")
    private val pathToTheMainExample = mainNivaFile.absolutePath
    val pathToGradle = pathToInfroProject / "build.gradle.kts"
    val pathToAmper = pathToInfroProject / "module.yaml"

    private fun getPathToMain() =
        // just `run` means default file is main.niva, run file runs with this file as root
        if (args.count() >= 2) {
            // first arg is run already
            val fileNameArg = args[1]
            if (File(fileNameArg).exists()) {
                fileNameArg
            } else {
                createFakeToken().compileError("File $fileNameArg doesn't exist")
            }

        } else {
            val mainNiva = "main.niva"
            val mainScala = "main.scala"
            if (File(mainNiva).exists())
                mainNiva
            else if (File(mainScala).exists())
                mainScala
            else
                createFakeToken().compileError("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
        }


    val pathToNivaMainFile = when (mainArg) {
        MainArgument.SINGLE_FILE_PATH -> args[0]
        MainArgument.RUN_FROM_IDE -> pathToTheMainExample

        MainArgument.RUN,
        MainArgument.INFO_ONLY,
        MainArgument.USER_DEFINED_INFO_ONLY,
        MainArgument.BUIlD -> getPathToMain()
    }

    init {
        if (!File(pathToInfroProject).exists()) {
            createFakeToken().compileError("Path ${WHITE}`$pathToInfroProject`${RESET} doesn't exist, please move the infroProject there from ${WHITE}`/Niva/infroProject`${RED} there or run compile.sh")
        }
    }
}

class ArgsManager(val args: Array<String>) {

    val compileOnly = args.find { it == "-c" } != null
    val infoIndex = args.indexOf("-i")
    val infoOnly = infoIndex != -1
    val infoUserOnly = args.find { it == "-iu" } != null
    val isShowTimeArg = args.find { it == "time" } != null

    fun mainArg(): MainArgument {
        return if (args.isNotEmpty()) {
            when (val arg = args[0]) {
                "run" -> MainArgument.RUN
                "build" -> MainArgument.BUIlD
                "info", "i" -> MainArgument.INFO_ONLY
                "infoUserOnly", "iu" -> MainArgument.USER_DEFINED_INFO_ONLY
                else -> {
                    if (!File(arg).exists()) {
                        createFakeToken().compileError("File $arg is not exist, to run full project use ${WHITE}niva run$RESET, to run single file use ${WHITE}niva path/to/file$RESET")
                    }
                    MainArgument.SINGLE_FILE_PATH
                }
            }
        } else MainArgument.RUN_FROM_IDE
    }
}

fun help(args: Array<String>): Boolean {
    if (args.isNotEmpty() && (args[0] == "--help" || args[0] == "-help")) {
        println(HELP)
        return true
    }
    return false
}

fun ArgsManager.time(executionTime: Long, kotlinPhase: Boolean) {
    if (isShowTimeArg) {
        if (kotlinPhase)
            println("Niva compilation time: $executionTime ms")
        else
            println("Kotlin compilation + exec time: $executionTime ms")
    }
}

fun getSpecialInfoArg(args: Array<String>, minusIindex: Int): String? {
    val specialPkgToInfoPrint = if (minusIindex != -1)
        // get word after -i
        if (args.count() - 1 > minusIindex)
            args[minusIindex + 1]
        else null
    else if (args.count() > 1 && args[0] == "info") {
        // if info filename then its getting all info, so return null
        // if info name then its getting special pkg
        if (File(args[1]).exists()) {
            null
        } else
            args[1]
    } else null

    return specialPkgToInfoPrint
}

var sas: ((String) -> Unit) = {}

fun buildString2(builderAction: StringBuilder.() -> Unit): String {
    val q = StringBuilder()

    val default: (String) -> Unit = {it: String ->
        q.append(it)
    }
    sas = default

    q.builderAction()
    return q.toString()
}


object StringUtils {
    /**
     * Returns a minimal set of characters that have to be removed from (or added to) the respective
     * strings to make the strings equal.
     */
    fun diff(a: String, b: String): Pair<String> {
        return diffHelper(a, b, HashMap())
    }

    /**
     * Recursively compute a minimal set of characters while remembering already computed substrings.
     * Runs in O(n^2).
     */
    private fun diffHelper(a: String, b: String, lookup: MutableMap<Long, Pair<String>>): Pair<String> {
        val key = (a.length.toLong()) shl 32 or b.length.toLong()
        if (!lookup.containsKey(key)) {
            val value: Pair<String>
            if (a.isEmpty() || b.isEmpty()) {
                value = Pair(a, b)
            } else if (a[0] == b[0]) {
                value = diffHelper(a.substring(1), b.substring(1), lookup)
            } else {
                val aa = diffHelper(a.substring(1), b, lookup)
                val bb = diffHelper(a, b.substring(1), lookup)
                value = if (aa.first.length + aa.second.length < bb.first.length + bb.second.length) {
                    Pair(a[0].toString() + aa.first, aa.second)
                } else {
                    Pair(bb.first, b[0].toString() + bb.second)
                }
            }
            lookup[key] = value
        }
        return lookup[key]!!
    }

    class Pair<T>(val first: T, val second: T) {
        override fun toString(): String {
            return "($first,$second)"
        }
    }
}

fun main(args: Array<String>) {

//    println(StringUtils.diff("this is a example", "this is a examp")) // prints (le,)
//    println(StringUtils.diff("Пример первой строки", "Пример второй строки с некоторыми изменениями")) // prints (o,yui)
//    println(StringUtils.diff("Toyota", "Coyote")) // prints (Ta,Ce)
//    println(StringUtils.diff("Flomax", "Volmax")) // prints (Fo,Vo)

//    val args = arrayOf("/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva", "-i")
//    val args = arrayOf("info", "/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva")
//    val args = arrayOf("build", "/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva")

    if (help(args)) return

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(args)
    val mainArg = am.mainArg()
    val pm = PathManager(args, mainArg)
    val resolver = compileProjFromFile(pm, singleFile = mainArg == MainArgument.SINGLE_FILE_PATH)

    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler = Compiler(
        pm.pathToInfroProject,
        inlineRepl,
        resolver.compilationTarget,
        resolver.compilationMode,
        pm.mainNivaFile.nameWithoutExtension,
        resolver
    )


    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD -> compiler.run(compileOnlyNoRun = true)
        MainArgument.RUN ->
            compiler.run()

        MainArgument.SINGLE_FILE_PATH -> {
            compiler.run(compileOnlyNoRun = am.compileOnly, singleFile = true)
        }

        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDE -> {
            compiler.run(compileOnlyNoRun = false, singleFile = true)
        }
    }

    am.time(System.currentTimeMillis() - secondTime, true)
}
