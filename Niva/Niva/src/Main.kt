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
    niva FILE — compile and run project with file as main entry
Flags:
    -c      — compile only
    -i      — get info about packages(it is usable to pipe it to .md file)
    -iu     — print info only about user-defined types
    -i pkg  — print info only about specific pkg

In code: 
Project configuration:
    Messages for ${YEL}Project$RESET:
    ${CYAN}target: $GREEN"TARGET" — target to jvm/linux/macos/windows(not supported yet)
    ${CYAN}mode: $GREEN"MODE"     — debug/release only for native targets, use debug for faster compilation
    
    ${CYAN}package: $GREEN"PKG"   — set package for the definitions in code below
    ${CYAN}protocol: $GREEN"NAME" — set protocol for the definitions in code below
    ${CYAN}use: $GREEN"PKG"       — set default pkg, like using namespace in C#/Vala
    
    Example: ${YEL}Project ${CYAN}target: $GREEN"linux" ${CYAN}mode: $GREEN"debug" 

Kotlin\Java interop:
    Messages for ${YEL}Bind$RESET:
    ${CYAN}package: "PKG"  — bind package
    ${CYAN}content: [CODE] — bindings
    
    Example:
    ${YEL}Bind ${CYAN}package: $GREEN"java.io" ${CYAN}content: $RESET[
        ${RED}type ${YEL}File ${CYAN}pathname: ${YEL}String
        ${YEL}File ${CYAN}exists ${RED}-> ${YEL}Boolean
        ${YEL}File ${CYAN}readText ${RED}-> ${YEL}String
    $RESET]
    ${WHITE}file = ${YEL}File ${CYAN}pathname: "path/to/file"
    ${WHITE}text = ${WHITE}file ${CYAN}readText
    
    Messages for Project:
    ${CYAN}loadPackages: $RESET{"PKG1" "PKG2"$RESET} — load package from Maven Central
    ${CYAN}import: "PATH_TO_PKG" — add direct import to generated code

"""


fun main(args: Array<String>) {
    val isThereArgs = args.isNotEmpty()

    if (isThereArgs && (args[0] == "--help" || args[0] == "-help")) {
        println(HELP)
        return
    }


    val pathToInfroProject = System.getProperty("user.home") / ".niva" / "infroProject"
    if (!File(pathToInfroProject).exists()) {
        createFakeToken().compileError("Path ${WHITE}`$pathToInfroProject`${RED} doesn't exist, please move the infroProject there from ${WHITE}`/Niva/infroProject`${RED} there or run compile.sh")
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
