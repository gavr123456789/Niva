@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.compileError
import frontend.resolver.printInfo
import frontend.util.createFakeToken
import frontend.util.div
import frontend.util.fillSymbolTable
import main.utils.generateInfo
import java.io.*
import main.utils.compileProjFromFile
import main.utils.runGradleRunInProject


const val ANSI_RESET = "\u001B[0m"
const val ANSI_BLACK = "\u001B[30m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_YELLOW = "\u001B[33m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_PURPLE = "\u001B[35m"
const val ANSI_CYAN = "\u001B[36m"
const val ANSI_WHITE = "\u001B[37m"

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
    niva FILE — compile and run project with file as main entry
Flags:
    -c      — compile only
    -i      — get info about packages(it is usable to pipe it to .md file)
    -iu     — print info only about user-defined types
    -i pkg  — print info only about specific pkg

In code: 
Project configuration:
    Messages for Project:
    target: "TARGET" — target to jvm/linux/macos/windows(not supported yet)
    mode: "MODE"     — debug/release only for native targets, use debug for faster compilation
    
    package: "PKG"   — set package for the definitions in code below
    protocol: "NAME" — set protocol for the definitions in code below
    use: "PKG"       — set default pkg, like using namespace in C#/Vala
    
    Example: Project target: "linux" mode: "debug" 

Kotlin\Java interop:
    Messages for Bind:
    package: "PKG"  — bind package
    content: [CODE] — bindings
    
    Example:
    Bind package: "java.io" content: [
        type File pathname: String
        File exists -> Boolean
        File readText -> String
    ]
    file = File pathname: "path/to/file"
    text = file readText
    
    Messages for Project:
    loadPackages: {"PKG1" "PKG2"} — load package from Maven Central
    import: "PATH_TO_PKG" — add direct import to generated code

"""


fun main(args: Array<String>) {
    val isThereArgs = args.isNotEmpty()

    if (isThereArgs && args[0] == "--help" || args[0] == "-help") {
        println(HELP)
        return
    }


    val pathToInfroProject = System.getProperty("user.home") / ".niva" / "infroProject"
    if (!File(pathToInfroProject).exists()) {
        createFakeToken().compileError("Path `$pathToInfroProject` doesn't exist, please move the infroProject there from `/Niva/infroProject` there or run compile.sh")
    }

    val pathWhereToGenerateKtAmper = pathToInfroProject / "src"
    val mainNivaFile = File("examples" / "Main" / "main.niva")
    val pathToTheMainExample = mainNivaFile.absolutePath
    val pathToGradle = pathToInfroProject / "build.gradle.kts"
    val pathToAmper = pathToInfroProject / "module.yaml"
    val pathToNivaProjectRootFile = if (isThereArgs) args[0] else pathToTheMainExample

    val startTime = System.currentTimeMillis()


    val resolver = compileProjFromFile(
        pathToNivaProjectRootFile, pathWhereToGenerateKtAmper, pathToGradle,
        pathToAmper
    )
    resolver.printInfo()

    val isShowTimeArg = args.count() > 1 && args[1] == "time"

    val secondTime = System.currentTimeMillis()
    if (isShowTimeArg) {
        val executionTime = secondTime - startTime
        println("Niva compilation time: $executionTime ms")
    }


    val compileOnly = args.find { it == "-c" } != null
    val infoOnly = args.find { it == "-i" } != null
    val infoUserOnly = args.find { it == "-iu" } != null

    if (!(infoOnly || infoUserOnly) ) {
        val inlineRepl = File("inline_repl.txt").absoluteFile

        runGradleRunInProject(
            pathToInfroProject,
            inlineRepl,
            resolver.compilationTarget,
            resolver.compilationMode,
            mainNivaFile.nameWithoutExtension,
            compileOnly
        )
    } else {
        val mdInfo = generateInfo(resolver, infoUserOnly)
        println(mdInfo)
    }



    if (isShowTimeArg) {
        val thirdTime = System.currentTimeMillis()
        val executionTime2 = thirdTime - secondTime
        println("Kotlin compilation + exec time: $executionTime2 ms")
    }
}
