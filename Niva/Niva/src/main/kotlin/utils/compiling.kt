package main.utils

import main.codogen.generateKtProject
import frontend.resolver.*
import inlineReplSystem.inlineReplSystem
import main.frontend.parser.types.ast.Statement
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.Error
import kotlin.text.contains
import kotlin.text.lowercase
import kotlin.time.TimeSource.Monotonic.markNow

object GlobalVariables {

    var needStackTrace = true
        private set

    @Suppress("unused")
    fun enableStackTrace() {
        needStackTrace = true
    }


    var printTime = false
        private set

    @Suppress("unused")
    fun enableTimePrinting() {
        printTime = true
    }


    var isDemonMode = false
        private set

    fun enableDemonMode() {
        isDemonMode = true
    }

    var isLspMode = false
        private set

    fun enableLspMode() {
        isLspMode = true
    }

}

fun String.runCommand(workingDir: File, withOutputCapture: Boolean = false, runTests: Boolean = false) {
    val p = ProcessBuilder(this.split(" "))
        .directory(workingDir)

    if (withOutputCapture && !runTests) {
        p.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
    }

//    println("command: $this")

    val process = p.start()

    val closeChildThread: Thread = object : Thread() {
        override fun run() {
            process.destroy()
        }
    }

    Runtime.getRuntime().addShutdownHook(closeChildThread)


    val inputStream = BufferedReader(InputStreamReader(process.inputStream))
//    while (process.isAlive) {
//        inputStream.readLine()?.also { output = it } //!= null ||
//        println(output)
//    }


    process.waitFor()//.waitFor(15, TimeUnit.SECONDS)
    if (runTests) {
        val first = "> Task :jvmTest"
        val last = "> Task :allTests"

        val w = inputStream.readText()

        val j = w.substringAfterLast(first).substringBefore(last)
        val l = j.replace("PASSED", "${GREEN}✅$RESET")
        val u = l.replace("FAILED", "${RED}❌$RESET")
            .replace("java.lang.Exception: ", "")

        println(u)
    }


//    if (stillExist) process.destroy()
//    inputStream.close()


}

fun targetToRunCommand(compilationTarget: CompilationTarget) = when (compilationTarget) {
    CompilationTarget.jvm -> "run"
    CompilationTarget.linux -> "runLinuxX64DebugExecutableLinuxX64"
    CompilationTarget.macos -> "runMacosArm64DebugExecutableMacosArm64"
    CompilationTarget.jvmCompose -> "jvmRun -DmainClass=mainNiva.MainKt --quiet"
}

class CompilerRunner(
    private val pathToProjectRoot: String,
    private val inlineReplPath: File,
    private val compilationTarget: CompilationTarget,
    private val compilationMode: CompilationMode,
    private val mainNivaFileName: String,
    private val resolver: Resolver
) {
    // all false = run code, dist = zip, buildFatJar = single jar
    private fun cmd(dist: Boolean = false, buildFatJar: Boolean = false, runTests: Boolean = false): String =
        (if (!dist)
            if (runTests)
                "allTests"
            else
                targetToRunCommand(compilationTarget)
        else
            when (compilationTarget) {
                CompilationTarget.jvm, CompilationTarget.jvmCompose -> if (buildFatJar) "fatJar" else "distZip"
                CompilationTarget.linux -> compilationMode.toCompileOnlyTask(compilationTarget)
                CompilationTarget.macos -> compilationMode.toCompileOnlyTask(compilationTarget)
            }) + " --build-cache --parallel -Pkotlin.experimental.tryK2=true" // --configuration-cache


    fun runCommand(
        dist: Boolean = false,
        buildFatJar: Boolean = false,
        runTests: Boolean = false
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
            throw Exception("Infro project doesn't exists, run compile script from niva repo")
        }

        if (compilationMode == CompilationMode.release && compilationTarget == CompilationTarget.jvm) {
            warning("Release mode is useless with jvm target")
        }

        val cmd = cmd(dist, buildFatJar, runTests)
        val defaultArgs = if (runTests) "--warning-mode=none" else "-q --console=plain"// if not verbose --console=plain
        (when (getOSType()) {
            CurrentOS.WINDOWS -> "cmd.exe /c gradlew.bat $defaultArgs $cmd"
            CurrentOS.LINUX, CurrentOS.MAC -> "./gradlew $defaultArgs $cmd"
//            CurrentOS.MAC -> "./gradlew $defaultArgs $cmd"
        }).runCommand(file, true, runTests)

        if (inlineReplPath.exists()) {
            if (compilationTarget == CompilationTarget.jvm) {
                inlineReplSystem(inlineReplPath)
                removeReplFile()
            } else {
                warning("inline repl currently supported only in jvm target")
            }
        }
        if (dist || buildFatJar) {
            when (compilationTarget) {
                CompilationTarget.jvm, CompilationTarget.jvmCompose -> {
                    if (buildFatJar) {
                        val jarFile = File("./${mainNivaFileName}.jar")
                        val fromPath =
                            pathToProjectRoot / "build" / "libs" / "$mainNivaFileName.niva.jar"
                        File(fromPath).copyTo(jarFile, true)
                    } else {
                        val zipName = File("./${mainNivaFileName}.zip")
                        val pathToNativeExe =
                            pathToProjectRoot / "build" / "distributions" / "infroProject-SNAPSHOT-1.0.zip"
                        File(pathToNativeExe).copyTo(zipName, true)
                    }

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

    fun infoPrint(onlyUserDefined: Boolean, specialPkg: String?) {
        if (specialPkg != null) {
            println("info for package: $specialPkg")
            val pkgInko = generatePkgInfo(resolver, specialPkg)
            println(pkgInko)
            return
        }
        val mdInfo = generateInfo(resolver, onlyUserDefined)
        println(mdInfo)
    }
}


class VerbosePrinter(val isVerboseOn: Boolean) {
    inline fun print(string: () -> String) {
        if (isVerboseOn) {
            val x = string()
            if (x != "") println("${CYAN}Verbose$RESET: ${string()}")
        }
    }
}

fun listFilesRecursively(directory: File, ext: String, ext2: String, ext3: String): List<File> {
    val fileList = mutableListOf<File>()
    val filesAndDirs = directory.listFiles()
    if (filesAndDirs != null) {
        for (file in filesAndDirs) {
            if (file.isFile && (file.extension == ext || file.extension == ext2)) {
                fileList.add(file)
            } else if (file.isDirectory) {
                fileList.addAll(listFilesRecursively(file, ext, ext2, ext3))
            }
        }
    }

    return fileList
}

fun compileProjFromFile(
    pm: PathManager,
    compileOnlyOneFile: Boolean,
    resolveOnly: Boolean = false,
    tests: Boolean = false,
    verbose: Boolean = false,
    onEachStatement: ((Statement, Map<String, Type>?, Map<String, Type>?, File) -> Unit)? = null

): Resolver {
    val pathToNivaMainFile = pm.pathToNivaMainFile
    val pathWhereToGenerateKt = pm.pathWhereToGenerateKtAmper
    val pathToGradle = pm.pathToGradle
    val pathToAmper = pm.pathToAmper
    val verbosePrinter = VerbosePrinter(verbose)




    val mainFile = File(pathToNivaMainFile)
    val nivaProjectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths =
        if (!compileOnlyOneFile)
            listFilesRecursively(nivaProjectFolder, "niva", "scala", "nivas").filter { it.name != mainFile.name }
        else
            listOf()

    // we have main file, and all other files, so we can create resolver now
    val resolver = Resolver(
        projectName = "common",
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf(),
        onEachStatement = onEachStatement,
        currentResolvingFileName = mainFile
    )

    resolver.resolve(mainFile, verbosePrinter)

    if (!resolveOnly) {
        val defaultProject = resolver.projects["common"]!!
        val codegenMark = markNow()
        resolver.generator.generateKtProject(
            pathWhereToGenerateKt,
            pathToGradle,
            pathToAmper,
            defaultProject,
            resolver.topLevelStatements,
            resolver.compilationTarget,
            mainFileName = mainFile.name,
            pm.pathToInfroProject,
            tests
        )

        verbosePrinter.print { "Codegen took: ${codegenMark.getMs()} ms" }


    }
    // printing all >?
    if (!GlobalVariables.isLspMode)
        resolver.printInfoFromCode()

    return resolver
}

fun addStd(mainCode: String, compilationTarget: CompilationTarget): String {
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

        fun throwWithMessage(message: String): Nothing {
            throw kotlin.Exception(message)
        }
        
        // for ct reflection
        class TypeType(
            val name: String,
            val fields: MutableMap<String, TypeType> = mutableMapOf(),
            val genericParams: MutableList<TypeType> = mutableListOf()
        )

        inline fun Any?.echo() = println(this)
        inline fun Any?.echonnl() = print(this)
        
        inline fun <T, R> T?.unpack(block: (T) -> R) {
            if (this != null)
                block(this)
        }
        
        inline fun <T, R> T?.unpackOr(block: (T) -> R, or: R): R {
            return if (this != null)
                block(this)
            else or 
        }
        
        inline fun <T : Any, R : Any> letIfAllNotNull(vararg arguments: T?, block: (List<T>) -> R): R? {
            return if (arguments.all { it != null }) {
                block(arguments.toList() as List<T>)
            } else null
        }


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
        
        inline fun <T> Boolean.ifTrue(x: () -> T) {
            if (this) {
                x()
            }
        }
        
        inline fun <T> Boolean.ifFalse(x: () -> T) {
            if (!this) {
                x()
            }
        }
        
        inline fun <T> Boolean.ifTrueIfFalse(x: () -> T, y: () -> T) {
            if (this) {
                x()
            } else y()
        }
        
        inline fun <T> Boolean.ifFalseIfTrue(x: () -> T, y: () -> T) {
            if (!this) {
                x()
            } else y()
        }
        
        inline fun <T> Iterable<T>.joinWithTransform(separator: String, noinline transform: ((T) -> CharSequence)): String {
            return this.joinToString(separator, transform = transform)
        }

        operator fun <K, V> MutableMap<out K, V>.plus(map: MutableMap<out K, V>): MutableMap<K, V> =
            LinkedHashMap(this).apply { putAll(map) }



        inline fun Boolean.isFalse() = !this
        inline fun Boolean.isTrue() = this
        
        fun <T> T?.unpackOrError(): T {
            return this!!
        } 
        
        // because default iterator on map from kotlin needs unpacking Map.Entry with ()
        inline fun <K, V> Map<out K, V>.forEach(action: (x: K, y: V) -> Unit): Unit {
            for (element in this) action(element.key, element.value)
        }

        // end of STD

    """.trimIndent()

    return buildString {
        append(nivaStd, "\n")
        append(mainCode)
    }
}


fun putInMainKotlinCode(code: String, compilationTarget: CompilationTarget, pathToInfroProject: String) = buildString {
    append("fun main(args: Array<String>) {\n")
    append("try {\n")

    append(code, "\n")

    val catchExpressions = if (compilationTarget != CompilationTarget.jvm) """
        } catch (e: Throwable) {
        println("----------")
        println(e.message)
        println("----------")
        println(e.stackTraceToString())
    }
    """ else """
        } catch (e: Throwable) {

        println("----------")
        println("\u001B[31m" + (e.message ?: "no message") + "\u001B[0m")
        println("----------")
        val q = e.stackTrace
        
        val thisProjectPath = "$pathToInfroProject/src/"
        
        fun replaceLinesInStackTrace(x: List<StackTraceElement>) {

            class FileAndLine(val file: String, val line: Int)
            val getNearestNivaLine = { kotlinLine: Int, file: String ->
                if (kotlinLine == -2) FileAndLine("EntryPoint!", 0)
                else {
                    if (kotlinLine < 0) throw Exception("Cant find line")

                    
                    val thisFileContent = java.io.File(thisProjectPath + file).readText()
                    val lines = thisFileContent.split("\n")


                    val y = lines[kotlinLine - 1]
                    val splitted = y.split("@")
                    if (splitted.count() != 2) throw Exception("Cant find niva line above " + kotlinLine)
                    val fileAndLineNumber = splitted[1].trim()
                    val (file, lineStr) = fileAndLineNumber.split(":::")
                    val line = lineStr.toInt()

                    FileAndLine(file, line)
                }
            }
            
            val checkExistAsNivaFile = { file: String ->
                java.io.File(thisProjectPath + file).exists()
            }

            val stackTracesWithFiles = x.filter { it.fileName != null }
            stackTracesWithFiles.forEach {
                val pathToFile =
                    if (it.fileName != "Main.kt") it.fileName.split(".").first() + "/" + it.fileName else it.fileName
                val nivaLine = if (checkExistAsNivaFile(pathToFile))
                    getNearestNivaLine(it.lineNumber - 1, pathToFile)
                else FileAndLine(
                    it.fileName,
                    it.lineNumber
                )
//            val newElement = StackTraceElement(it.className, it.methodName, nivaLine.file, nivaLine.line)
                println(kotlin.text.buildString {
                    append("Method: ")
                    append(it.methodName)
                    append("\t\t")
                    append("\u001B[37mFile: ")
                    append(nivaLine.file)
                    append("::")
                    append(nivaLine.line)
                    append("\u001B[0m")
                })
//            replacedStack.add(newElement)
            }
//        return replacedStack
        }
        val methodName = q[0].methodName
        replaceLinesInStackTrace(if (methodName == "unpackOrError" || methodName == "throwWithMessage") q.drop(1) else q.toList())

//    println(e.stackTraceToString())
    }
    """.trimIndent()

    append(
        catchExpressions
    )


    append("}\n")
}

enum class CurrentOS {
    WINDOWS,
    LINUX,
    MAC
}

fun getOSType(): CurrentOS {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("dows") -> CurrentOS.WINDOWS
        osName.contains("nux") -> CurrentOS.LINUX
        osName.contains("mac") -> CurrentOS.MAC
        else -> throw Error("Unknown OS: $osName")
    }
}
