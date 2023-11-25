import codogen.generateKtProject
import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.typer.Resolver
import frontend.typer.resolve
import frontend.util.OS_Type
import frontend.util.div
import frontend.util.fillSymbolTable
import frontend.util.getOSType
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

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


fun runGradleRunInProject(path: String, inlineReplPath: File) {
    // remove repl log file since it will be recreated
    val removeReplFile = {
        if (inlineReplPath.exists()) {
            inlineReplPath.delete()
        }
    }
    removeReplFile()

    val file = File(path)
    if (!file.exists()) {
        throw Exception("Path to infra project doesn't exists")
    }
    when (getOSType()) {
        OS_Type.WINDOWS -> "cmd.exe /c gradlew.bat -q run -Pkotlin.experimental.tryK2=true".runCommand(file, true)
        OS_Type.LINUX -> "./gradlew -q run -Pkotlin.experimental.tryK2=true".runCommand(file, true)
        OS_Type.MAC -> "./gradlew -q run -Pkotlin.experimental.tryK2=true".runCommand(file, true)
    }

    if (inlineReplPath.exists()) {
        inlineReplSystem(inlineReplPath)
        removeReplFile()
    }
}

fun compileProjFromFile(pathToProjectRootFile: String, pathWhereToGenerateKt: String, pathToGradle: String) {

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
    val projectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths = listFilesRecursively(projectFolder, "niva", "scala").filter { it.name != mainFile.name }
    // we have main file, and all other files, so we can create resolver now

    val resolver = Resolver(
        projectName = "common",
        mainFile = mainFile,
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf()
    )

    resolver.resolve()
    val mainProject = resolver.projects["common"]!!
    resolver.generator.generateKtProject(pathWhereToGenerateKt, pathToGradle, mainProject, resolver.topLevelStatements)
}


fun putInMainKotlinCode(code: String) = buildString {
    append("fun main() {\n")
    append(code, "\n")
    append("}\n")
}


fun addNivaStd(mainCode: String): String {
    val inlineReplPath = File("inline_repl.txt").absolutePath
    val quote = "\"\"\""
    val nivaStd = """
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
        
    """.trimIndent()

    return buildString {
        append(nivaStd, "\n")
        append(mainCode)
    }
}

//const val INLINE_REPL_WIN = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\Niva\\NivaK\\Niva\\inline_repl.txt"

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

    // java -jar .\Niva.jar C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\.infroProject C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\Niva\src\nivaExampleProject\collections.niva

    val isThereArgs = args.count() >= 2

    val inlineRepl = File("inline_repl.txt").absoluteFile

    val pathToProjectRoot = if (isThereArgs) args[0] else ".." / ".infroProject"
    val pathWhereToGenerateKt = pathToProjectRoot / "src" / "main" / "kotlin"
    val pathToTheMainExample = File("src" / "examples" / "Main" / "main.niva").absolutePath
    val pathToNivaProjectRootFile = if (isThereArgs) args[1] else pathToTheMainExample
    val pathToGradle = pathToProjectRoot / "build.gradle.kts"


    val startTime = System.currentTimeMillis()


    compileProjFromFile(pathToNivaProjectRootFile, pathWhereToGenerateKt, pathToGradle)

    val isShowTimeArg = args.count() > 2 && args[2] == "time"

    val secondTime = System.currentTimeMillis()
    if (isShowTimeArg || true) {
        val executionTime = secondTime - startTime
        println("Niva compilation time: $executionTime ms")
    }



    runGradleRunInProject(pathToProjectRoot, inlineRepl)

    if (isShowTimeArg || true) {
        val thirdTime = System.currentTimeMillis()
        val executionTime2 = thirdTime - secondTime
        println("Kotlin compilation + exec time: $executionTime2 ms")
    }
}
