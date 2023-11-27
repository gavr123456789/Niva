@file:Suppress("unused")

package main

import codogen.generateKtProject
import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.typer.*
import frontend.util.CurrentOS
import frontend.util.div
import frontend.util.fillSymbolTable
import frontend.util.getOSType
import main.utils.generateInfo
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

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

fun String.runCommand(workingDir: File, withOutputCapture: Boolean = false, needWait: Boolean = true) {
    val p = ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)

    if (withOutputCapture) {
        p.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
    }

    val process = p.start()
    if (needWait) {
        process.waitFor(60, TimeUnit.MINUTES)
    }
}


fun runGradleRunInProject(
    pathToProjectRoot: String,
    inlineReplPath: File,
    compilationTarget: CompilationTarget,
    compilationMode: CompilationMode,
    mainNivaFileName: String,
    compileOnlyNoRun: Boolean = false
) {
    // remove repl log file since it will be recreated
    val removeReplFile = {
        if (inlineReplPath.exists()) {
            inlineReplPath.delete()
        }
    }
    removeReplFile()

    val file = File(pathToProjectRoot)
    if (!file.exists()) {
        throw Exception("Path to infra project doesn't exists")
    }

    val cmd = if (!compileOnlyNoRun)
        when (compilationTarget) { // native run is always debug
            CompilationTarget.jvm -> "run"
            CompilationTarget.linux -> "runLinuxX64DebugExecutableLinuxX64"
            CompilationTarget.macos -> "runMacosArm64DebugExecutableMacosArm64"
        } else
        when (compilationTarget) {
            CompilationTarget.jvm -> "distZip"
            CompilationTarget.linux -> compilationMode.toCompileOnlyTask(compilationTarget)
            CompilationTarget.macos -> compilationMode.toCompileOnlyTask(compilationTarget)
        }

    if (compilationMode == CompilationMode.release && compilationTarget == CompilationTarget.jvm) {
        println("${ANSI_YELLOW}Warning: It's useless to use release mode with jvm target$ANSI_RESET")
    }


    when (getOSType()) {
        CurrentOS.WINDOWS -> "cmd.exe /c gradlew.bat -q $cmd -Pkotlin.experimental.tryK2=true".runCommand(file, true)
        CurrentOS.LINUX -> "./gradlew -q $cmd -Pkotlin.experimental.tryK2=true".runCommand(file, true)
        CurrentOS.MAC -> "./gradlew -q $cmd -Pkotlin.experimental.tryK2=true".runCommand(file, true)
    }

    if (inlineReplPath.exists()) {
        if (compilationTarget == CompilationTarget.jvm) {
            inlineReplSystem(inlineReplPath)
            removeReplFile()
        } else {
            println("Warning: inline repl currently supported only in jvm target")
        }
    }
    if (compileOnlyNoRun) {
        when (compilationTarget) {
            CompilationTarget.jvm -> {
                val zipName = File("./${mainNivaFileName}.zip")
                val pathToNativeExe = pathToProjectRoot / "build" / "distributions" / "infroProject-SNAPSHOT-1.0.zip"
                File(pathToNativeExe).copyTo(zipName, true)
            }
            CompilationTarget.linux -> {
                val execName = File("./$mainNivaFileName")
                val pathToNativeExe = compilationMode.toBinaryPath(compilationTarget, pathToProjectRoot)
                File(pathToNativeExe).copyTo(execName, true)
                execName.setExecutable(true)
            }

            CompilationTarget.macos -> {}
        }
    }


}

fun compileProjFromFile(
    pathToProjectRootFile: String,
    pathWhereToGenerateKt: String,
    pathToGradle: String,
    pathToAmper: String
): Resolver {

    fun listFilesRecursively(directory: File, ext: String, ext2: String): List<File> {
        val fileList = mutableListOf<File>()

        val filesAndDirs = directory.listFiles()

        if (filesAndDirs != null) {
            for (file in filesAndDirs) {
                if (file.isFile && (file.extension == ext || file.extension == ext2)) {
                    fileList.add(file)
                } else if (file.isDirectory) {
                    fileList.addAll(listFilesRecursively(file, ext, ext2))
                }
            }
        }

        return fileList
    }


    val mainFile = File(pathToProjectRootFile)
    val nivaProjectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths = listFilesRecursively(nivaProjectFolder, "niva", "scala").filter { it.name != mainFile.name }

    // we have main file, and all other files, so we can create resolver now
    val resolver = Resolver(
        projectName = "common",
        mainFile = mainFile,
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf()
    )

    resolver.resolve()
    val defaultProject = resolver.projects["common"]!!

    resolver.generator.generateKtProject(
        pathWhereToGenerateKt,
        pathToGradle,
        pathToAmper,
        defaultProject,
        resolver.topLevelStatements,
        resolver.compilationTarget
    )

    return resolver
}


fun putInMainKotlinCode(code: String) = buildString {
    append("fun main() {\n")
    append(code, "\n")
    append("}\n")
}


fun addNivaStd(mainCode: String, compilationTarget: CompilationTarget): String {
    val inlineReplPath = File("inline_repl.txt").absolutePath


    val quote = "\"\"\""

    val jvmSpecific = if (compilationTarget == CompilationTarget.jvm)
        """import java.io.BufferedWriter
        import java.io.FileWriter
        import java.io.IOException
        
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
    """.trimIndent() else "fun <T> inlineRepl(x: T, pathToNivaFileAndLine: String, count: Int) {}"

    val nivaStd = """
        // STD
        $jvmSpecific
        
        class Error {
            companion object
        }
        fun Error.Companion.throwWithMessage(message: String): Nothing {
            throw kotlin.Exception(message)
        }
        
        inline fun Any?.echo() = println(this)
        const val INLINE_REPL = $quote$inlineReplPath$quote 
        
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
        
    """.trimIndent()

    return buildString {
        append(nivaStd, "\n")
        append(mainCode)
    }
}

class LineAndContent(val line: Int, val content: String, val count: Int)

fun inlineReplSystem(file: File) {

    val lines = file.useLines { it.toList() }

    val q = lines.map {
        val (patnLineContent, countStr) = it.split("***")
        val (pathLine, content) = patnLineContent.split("|||")
        val (path, line) = pathLine.split(":::")
        val lineAndContent = LineAndContent(line = line.toInt(), content = content, count = countStr.toInt())

        path to lineAndContent
    }

    val lineNumberToContent = mutableMapOf<String, MutableList<LineAndContent>>()
    q.forEach { (t, u) ->
        lineNumberToContent.getOrPut(t) { mutableListOf() }.add(u)
    }

    addCommentAboveLine(lineNumberToContent)
}

fun addCommentAboveLine(lineNumberToContent: Map<String, MutableList<LineAndContent>>) {

    lineNumberToContent.forEach { (k, v) ->
        val lines = File(k).useLines { it.toMutableList() }

        var linesAdded = 0
        v.forEach { it ->


            if (it.line >= 1 && it.line <= lines.size) {
                val lineNumToAdd = it.line - 1 + linesAdded

                // separate when it is already //> on line above, usual >, and > with number
                if (lineNumToAdd > 0 && lines[lineNumToAdd - 1].startsWith("//>")) {
                    // count how many values there already
                    val countValues = lines[lineNumToAdd - 1].count { it == ',' } + 1
                    // after this action there will be one more value
                    val realCount = it.count - 1
                    if (countValues > realCount) {
                        val needToDrop = countValues - realCount
                        val currentValues = lines[lineNumToAdd - 1]
                            .split(", ")
                            .drop(needToDrop).toMutableList()
                        currentValues.add(it.content)
                        val str = "//> ${currentValues.joinToString(", ")}"

                        lines[lineNumToAdd - 1] = str
                    } else {
                        lines[lineNumToAdd - 1] += ", ${it.content}"
                    }
                } else {
                    lines.add(lineNumToAdd, "//> ${it.content}")
                    linesAdded++
                }
            } else {
                throw Exception("Inline REPL System: Got line #${it.line} but all lines are only ${lines.size}")
            }
        }

        val writer = BufferedWriter(FileWriter(k))
        for (updatedLine in lines) {
            writer.write(updatedLine)
            writer.newLine()
        }
        writer.close()
    }
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


fun main(args: Array<String>) {

    val isThereArgs = args.isNotEmpty()

    val inlineRepl = File("inline_repl.txt").absoluteFile

    val pathToProjectRoot = System.getProperty("user.home") / ".niva" / "infroProject" //if (isThereArgs) args[0] else ".." / "infroProject"
//    val pathWhereToGenerateKt = pathToProjectRoot / "src" / "main" / "kotlin"
    val pathWhereToGenerateKtAmper = pathToProjectRoot / "src" // / "main" / "kotlin"
    val mainNivaFile = File("src" / "examples" / "Main" / "main.niva")
    val pathToTheMainExample = mainNivaFile.absolutePath
    val pathToNivaProjectRootFile = if (isThereArgs) args[0] else pathToTheMainExample
    val pathToGradle = pathToProjectRoot / "build.gradle.kts"
    val pathToAmper = pathToProjectRoot / "module.yaml"


    val startTime = System.currentTimeMillis()


    val resolver = compileProjFromFile(
        pathToNivaProjectRootFile, pathWhereToGenerateKtAmper, pathToGradle,
        pathToAmper
    )

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
        runGradleRunInProject(
            pathToProjectRoot,
            inlineRepl,
            resolver.compilationTarget,
            resolver.compilationMode,
            mainNivaFile.nameWithoutExtension,
            compileOnly
        )
    } else {
        val x = generateInfo(resolver, infoUserOnly)
        println(x)
    }



    if (isShowTimeArg) {
        val thirdTime = System.currentTimeMillis()
        val executionTime2 = thirdTime - secondTime
        println("Kotlin compilation + exec time: $executionTime2 ms")
    }
}
