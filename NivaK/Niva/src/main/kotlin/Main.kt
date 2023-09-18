import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.typer.Resolver
import frontend.typer.generateKtProject
import frontend.util.OS_Type
import frontend.util.div
import frontend.util.fillSymbolTable
import frontend.util.getOSType
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit


//fun check(source: String, tokens: MutableList<TokenType>) {
//    val lexer = Lexer(source, "sas")
//    lexer.fillSymbolTable()
//    val result = lexer.lex().map { it.kind }
//    if (tokens != result) {
//        throw Throwable("\n\ttokens: $tokens\n\tresult: $result")
//    }
//}

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
        OS_Type.MAC -> TODO()
    }

    if (inlineReplPath.exists()) {
        inlineReplSystem(inlineReplPath)
        removeReplFile()
    }
}

fun compileProjFromFile(pathToProjectRootFile: String, pathWhereToGenerateKt: String) {

    fun listFilesRecursively(directory: File): List<File> {
        val fileList = mutableListOf<File>()

        // Получаем все файлы и поддиректории в данной директории
        val filesAndDirs = directory.listFiles()

        if (filesAndDirs != null) {
            for (file in filesAndDirs) {
                if (file.isFile) {
                    // Если это файл, добавляем его в список
                    fileList.add(file)
                } else if (file.isDirectory) {
                    // Если это директория, рекурсивно вызываем эту же функцию для нее
                    fileList.addAll(listFilesRecursively(file))
                }
            }
        }

        return fileList
    }


    val mainFile = File(pathToProjectRootFile)
    val projectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths = listFilesRecursively(projectFolder).filter { it.name != mainFile.name }
    // we have main file, and all other files, so we can create resolver now

    val resolver = Resolver(
        projectName = "common",
        mainFile = mainFile,
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf()
    )

    resolver.generateKtProject(pathWhereToGenerateKt)


}


fun putInMainKotlinCode(code: String) = buildString {
    append("fun main() {\n")
    append(code, "\n")
    append("}\n")
}


fun String.addNivaStd(): String {
    val inlineReplPath = File("inline_repl.txt").absolutePath
    val quote = "\"\"\""
    val nivaStd = """
        // STD
        import java.io.BufferedWriter
        import java.io.FileWriter
        import java.io.IOException
        
        inline fun Any?.echo() = println(this)
        const val INLINE_REPL = $quote$inlineReplPath$quote 
        
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
        
    """.trimIndent()
    return buildString {
        append(nivaStd, "\n")
        append(this@addNivaStd)
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

fun main(args: Array<String>) {
    // java -jar .\Niva.jar C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\.infroProject C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\Niva\src\nivaExampleProject\collections.niva

    val isThereArgs = args.count() >= 2


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val pathToProjectRoot = if (isThereArgs) args[0] else ".." / ".infroProject"
    val pathWhereToGenerateKt = pathToProjectRoot / "src" / "main" / "kotlin"
    val pathToTheMainExample = File("src" / "examples" / "Main" / "main.niva").absolutePath
    val pathToNivaProjectRootFile =
        if (isThereArgs) args[1] else pathToTheMainExample


    val startTime = System.currentTimeMillis()


    compileProjFromFile(pathToNivaProjectRootFile, pathWhereToGenerateKt)


    val secondTime = System.currentTimeMillis()
    val executionTime = secondTime - startTime
    println("Niva compilation time: $executionTime ms")


    val isGradleWatching = args.count() > 1 && args[2] == "watch"
    if (!isGradleWatching) {
        runGradleRunInProject(pathToProjectRoot, inlineRepl)
    }


    val thirdTime = System.currentTimeMillis()
    val executionTime2 = thirdTime - secondTime
    println("Kotlin compilation + exec time: $executionTime2 ms")


//    if (inlineRepl.exists())
//        inlineReplSystem(inlineRepl)
}
