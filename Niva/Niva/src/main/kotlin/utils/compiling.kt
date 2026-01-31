package main.utils

import main.codogen.generateKtProject
import frontend.resolver.*
import main.Option
import main.codogen.BuildSystem
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.Statement
import main.languageServer.DEV_MODE_FILE_NAME
import utils.devMode_JVM_dependant
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.Pair
import kotlin.collections.List
import kotlin.text.contains
import kotlin.text.lowercase
import kotlin.time.TimeSource.Monotonic.markNow

object GlobalVariables {

    var emitSourceComments = true
        private set

    fun disableSourceComments() { emitSourceComments = false }

    var capabilities = false

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
private fun String.toCommandArgs(): List<String> {
    val tokens = this.split(" ").filter { it.isNotBlank() }

    return buildList {
        var skipNext = false
        tokens.forEachIndexed { index, token ->
            if (skipNext) {
                skipNext = false
                return@forEachIndexed
            }

            if (token == "--tests" && index + 1 < tokens.size) {
                add("--tests"  )
                add(tokens[index + 1].trim('"'))
                skipNext = true
            } else {
                add(token)
            }
        }
    }
}

// if we are running test we need to modify its output
fun String.runCommand(workingDir: File, withOutputCapture: Boolean = false, runTests: Boolean = false) {
//    println("DEBUG: running command: $this")
    val fixed = this.toCommandArgs()//.split(" ").filter { it.isNotBlank() }

    val p = ProcessBuilder(fixed)
        .directory(workingDir)


    if (withOutputCapture && !runTests) {
        p
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
    }

//    if (GlobalVariables.isDemonMode) {
//        println("running '$this' command inside $workingDir")
//    }
    val process = p.start()

    val closeChildThread: Thread = object : Thread() {
        override fun run() {
            process.destroy()
        }
    }

    Runtime.getRuntime().addShutdownHook(closeChildThread)


    val inputStream = BufferedReader(InputStreamReader(process.inputStream))

    // do not watch if its file watcher
    if (!this.contains("-t"))
        process.waitFor()//.waitFor(15, TimeUnit.SECONDS)

    if (runTests) {
        val upToDate = "UP-TO-DATE"
        val first = "> Task :test"
        val last = "4 actionable tasks"

        val w = inputStream.readText()
//        val e = process.errorStream.reader().readText()

        val j = w.substringAfterLast(first).substringBefore(last).replace(upToDate, "").replace("BUILD SUCCESSFUL in", "").replace("STANDARD_OUT", "")
        val l = j.replace("PASSED", "${GREEN}✅$RESET")
        val u = l.replace("FAILED", "${RED}❌$RESET")
            .replace("java.lang.Exception: ", "").trim()

        println(u)
    }


//    if (stillExist) process.destroy()
//    inputStream.close()


}

fun targetToRunCommand(compilationTarget: CompilationTarget) = when (compilationTarget) {
    CompilationTarget.jvm -> "run -DmainClass=mainNiva.MainKt --quiet" // in amper it was jvmRun
    CompilationTarget.linux -> "runLinuxX64DebugExecutableLinuxX64"
    CompilationTarget.macos -> "runMacosArm64DebugExecutableMacosArm64"
    CompilationTarget.jvmCompose -> "jvmRun -DmainClass=mainNiva.MainKt --quiet"
}

class CompilerRunner(
    private val pathToProjectRoot: String,
    private val inlineReplPath: File = File("inline_repl.txt").absoluteFile,
    private val compilationTarget: CompilationTarget,
    private val compilationMode: CompilationMode,
    private val mainNivaFileName: String,
    private val resolver: Resolver,
    private val watch: Boolean = false,
) {
    // all false = run code, dist = zip, buildFatJar = single jar
    private fun gradleCmd(dist: Boolean = false, buildFatJar: Boolean = false, runTests: Boolean = false): String =
        (if (!dist)
            if (runTests)
                "test"
            else
                targetToRunCommand(compilationTarget)
        else
            // not run, but build or dist
            when (compilationTarget) {
                CompilationTarget.jvm, CompilationTarget.jvmCompose -> if (buildFatJar) "fatJar" else "distZip"
                CompilationTarget.linux -> compilationMode.toCompileOnlyTask(compilationTarget)
                CompilationTarget.macos -> compilationMode.toCompileOnlyTask(compilationTarget)
            }) + " --build-cache --parallel " + if (watch) " -t" else ""


    fun runMill(option: Option, outputRename: String?) {
        removeReplFile()


        //2 check that code is generated
        val dotNivaInsideProj = File(pathToProjectRoot)
        if (!dotNivaInsideProj.exists()) {
            throw Exception("Infro project doesn't exists, install niva from repo readme")
        }
        if (compilationMode == CompilationMode.release && compilationTarget == CompilationTarget.jvm) {
            warning("Release mode is useless with jvm target")
        }

        // 3 generate command and run it
        val cmd = when(option) {
            Option.RUN -> "niva.run"
            Option.BUILD -> "niva.assembly"
            Option.TEST -> "niva.test"
        }
        val defaultArgs = "--ticker false -s"
        runFinalCommand("./mill","cmd.exe /c mill.bat",defaultArgs, cmd, dotNivaInsideProj, option == Option.TEST)
        // 4 inline repl
//        inlineReplIfExists()

        // 5 move jar if needed
        if (option == Option.BUILD) {
            val binName = outputRename ?: "main.jar"
            val destination = dotNivaInsideProj.parentFile.resolve(binName)
            val pathToJar = dotNivaInsideProj
                .resolve("out/niva/assembly.dest/out.jar")
            pathToJar.copyTo(destination, true)
            println("jar created in $destination")
        }
    }
    fun removeReplFile() {
//        if (inlineReplPath.exists()) {
//            inlineReplPath.delete()
//        }
    }
    fun runGradleAmperBuildCommand(
        dist: Boolean = false,
        buildFatJar: Boolean = false,
        runTests: Boolean = false,
        outputRename: String? = null,
        testFilter: String? = null,
    ) {
        // 1 remove repl log file since it will be recreated
        removeReplFile()

        // 2 check that code is generated
        val file = File(pathToProjectRoot)
        if (!file.exists()) {
            throw Exception("Infro project doesn't exists, run compile script from niva repo")
        }

        if (compilationMode == CompilationMode.release && compilationTarget == CompilationTarget.jvm) {
            warning("Release mode is useless with jvm target")
        }
        // 3 generate a command and run it
        var cmd = gradleCmd(dist, buildFatJar, runTests)
        if (runTests && !testFilter.isNullOrBlank()) {
            // Gradle test filtering. Supports patterns like ClassName.testName
            // We quote the value to keep it intact across shells.
            val escaped = testFilter.replace("\"", "\\\"")
            cmd += " --tests \"$escaped\""
        }
        val defaultArgs = if (runTests) "--warning-mode=none" else "-q --console=plain"// if not verbose --console=plain
        runFinalCommand("./gradlew","cmd.exe /c gradlew.bat", defaultArgs, cmd, file, runTests)
        // 4 inline repl
//        inlineReplIfExists()

        val fileName = outputRename ?: mainNivaFileName
        // 5 move jar if needed
        if (dist || buildFatJar) {
            buildJarOrDistGradle(buildFatJar, fileName)
        }
    }

    // unused
//    private fun inlineReplIfExists() {
//        if (inlineReplPath.exists()) {
//            if (compilationTarget == CompilationTarget.jvm) {
//                inlineReplSystem(inlineReplPath)
//                removeReplFile()
//            } else {
//                warning("inline repl currently supported only in jvm target")
//            }
//        }
//    }


    private fun runFinalCommand(
        commandForUnix: String,
        commandForWindows: String,
        defaultArgs: String,
        cmd: String,
        file: File,
        runTests: Boolean
    ) {
        (when (getOSType()) {
            CurrentOS.WINDOWS -> "$commandForWindows $defaultArgs $cmd"
            CurrentOS.LINUX, CurrentOS.MAC -> "$commandForUnix $defaultArgs $cmd"
        }).runCommand(file, true, runTests)
    }

    // move the build jar into current folder and rename it
    fun buildJarOrDistGradle(buildFatJar: Boolean, fileName: String) {
        when (compilationTarget) {
            CompilationTarget.jvm, CompilationTarget.jvmCompose -> {

                if (buildFatJar) {
                    val jarFile = File("./${fileName}.jar")
                    val fromPath =
                        pathToProjectRoot / "build" / "libs" / "$mainNivaFileName.niva.jar"
                    File(fromPath).copyTo(jarFile, true)
                } else {
                    val zipName = File("./${fileName}.zip")
                    val pathToNativeExe =
                        pathToProjectRoot / "build" / "distributions" / "infroProject-0.0.1.zip"
                    File(pathToNativeExe).copyTo(zipName, true)
                }
            }

            CompilationTarget.linux -> {
                val execName = File("./$fileName")
                val pathToNativeExe = compilationMode.toBinaryPath(compilationTarget, pathToProjectRoot)
                File(pathToNativeExe).copyTo(execName, true)
                execName.setExecutable(true)
            }

            CompilationTarget.macos -> {}
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
            if (x.isNotEmpty()) println("${CYAN}Verbose$RESET: $x")
        }
    }
}

fun listFilesDownUntilNivaIsFoundRecursively(directory: File, ext: String): MutableList<File> {
    val fileList = mutableListOf<File>()
    val filesAndDirs = directory.listFiles()
    if (filesAndDirs == null) return mutableListOf()

    for (file in filesAndDirs) {
        if (file.isFile && (file.extension == ext)) {
            fileList.add(file)
        } else if (file.isDirectory) {
            fileList.addAll(listFilesDownUntilNivaIsFoundRecursively(file, ext))
        }
    }

    return fileList
}

// refactor - separate full resolve and generating the code into 2 functions
fun compileProjFromFile(
    pm: PathManager,
    compileOnlyOneFile: Boolean,
    dontRunCodegen: Boolean = false,
    tests: Boolean = false,
    verbose: Boolean = false,
    onEachStatement: ((Statement, Map<String, Type>?, Map<String, Type>?, File) -> Unit)? = null,
    customAst: Pair<List<Statement>, List<Pair<String, List<Statement>>>>? = null,
    buildSystem: BuildSystem,
    previousFilePath: MutableList<File>? = null,
    evalCustomExpr: String? = null,
): Resolver {

    val mainFile = File(pm.pathToNivaMainFile)
    val nivaProjectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths =
        previousFilePath
            ?: if (!compileOnlyOneFile)
                listFilesDownUntilNivaIsFoundRecursively(nivaProjectFolder, "niva")
                    .asSequence()
                    .filter { it.name != mainFile.name }
                    .sortedBy { file -> file.name }
                    .toMutableList()
            else
                mutableListOf()

    // we have main file, and all other files, so we can create resolver now
    val resolver = Resolver(
        projectName = "common",
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf(),
        onEachStatement = onEachStatement,
        currentResolvingFileName = mainFile
    )

    val verbosePrinter = VerbosePrinter(verbose)


    // we need custom ast to fill file to ast table in LS(non incremental store)
    val (mainAst, otherAst) = run {
        if (customAst == null) {
            val qwf = if (evalCustomExpr != null) {
                """
                    [
                        $evalCustomExpr
                    ] do echo
                """.trimIndent()
            } else null
            val mainText = qwf ?: mainFile.readText()
            val beforeParserMark = markNow()
            val w = parseFilesToAST(
                mainFileContent = mainText,
                otherFileContents = resolver.otherFilesPaths,
                mainFilePath = mainFile.absolutePath,
                resolveOnlyOneFile = compileOnlyOneFile,
                verbosePrinter = verbosePrinter,
            )
            verbosePrinter.print { "Parsing full: ${beforeParserMark.getMs()} ms" }

            Pair(w.first, w.second)
        }
        else
            customAst
    }


    if (resolver.otherFilesPaths.count() != otherAst.count()) {
        val tok = mainAst.first().token
        tok.compileError("resolver.otherFilesPaths = ${resolver.otherFilesPaths}, otherAst = $otherAst, customAST = $customAst")
    }
    resolver.resolveWithBackTracking(
        mainAst,
        otherAst,
        mainFile.absolutePath,
        mainFile.nameWithoutExtension,
        verbosePrinter
    )

    if (!dontRunCodegen) {
        val defaultProject = resolver.projects["common"]!!
        val codegenMark = markNow()

        resolver.generator.generateKtProject(
            pm.pathWhereToGenerateKtAmper,
            pm.pathToBuildFileGradle,
            pm.pathToBuildFileAmper,
            pm.pathToBuildFileMill,
            pm.nivaRootFolder,
            defaultProject,
            resolver.topLevelStatements,
            resolver.compilationTarget,
            mainFileName = mainFile.name,
            pm.pathToInfroProject,
            tests,
            buildSystem = buildSystem,
        )

        verbosePrinter.print { "BuildSystem = $buildSystem" }
        verbosePrinter.print { "Codegen to ${pm.pathWhereToGenerateKtAmper}" }
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
        devMode_JVM_dependant
    else ""

    val nivaStd = $$"""
        
        // STD
        $$jvmSpecific
        
        
        object Compiler {
            var cliArgs = listOf<String>()
            fun cliArgs() = cliArgs
        }
        
        typealias Bool = Boolean

        
        
        // Dynamic
        sealed class Dynamic()

        class DynamicStr(val value: String): Dynamic()
        class DynamicInt(val value: Int): Dynamic()
        class DynamicDouble(val value: Double): Dynamic()
        class DynamicBoolean(val value: Boolean): Dynamic()
        class DynamicList(val value: List<Dynamic>): Dynamic()
        class DynamicObj(val value: MutableMap<String, Dynamic>): Dynamic()

       //class Dynamic(val name: String, val fields: Map<String, Any?>) {
//            override fun toString(): String {
//                val fields = fields.map { (k, v) ->
//                    val w = if (v is Dynamic) {
//                        "    $k: \n" + v.toString().prependIndent("        ")
//                    } else "    $k: $v"
//                    w
//                }.joinToString("\n")
//                return "Dynamic$name\n" +
//                        "$fields"
//            }
//        }
        
        fun throwWithMessage(message: String): Nothing {
            //@ core.niva:::0
            throw kotlin.Exception(message)
        }

        inline fun Any?.echo() = println(this)
        inline fun Any?.echonnl() = print(this)
        inline fun <T> T.orPANIC() = this
        
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
                block(arguments.filterNotNull())
            } else null
        }


        const val INLINE_REPL = $$quote$$inlineReplPath$$quote

        inline fun IntRange.forEach(action: (Int) -> Unit) {
            for (element in this) action(element)
        }
        
        inline fun Int.repeat(action: (Int) -> Unit) {
            for (element in 0..<this) action(element)
        }

        // for cycle
        inline fun Int.toDo(to: Int, `do`: (Int) -> Unit) {
            for (element in this.rangeTo(to)) `do`(element)
        }
        
        inline fun Int.toByDo(to: Int, by: Int, `do`: (Int) -> Unit) {
            if (this <= to) {
                for (element in this..to step by) `do`(element)
            } else {
                for (element in this downTo to step by) `do`(element)
            }
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
            if (!this) { x() }
        }
        
        inline fun <T> Boolean.ifTrueIfFalse(x: () -> T, y: () -> T): T {
            return if (this) x() else y()
        }
        
        inline fun <T> T?.unpackOrValue(v: T) = this ?: v 

        inline fun <T> Boolean.ifFalseIfTrue(x: () -> T, y: () -> T): T = this.ifTrueIfFalse(y, x)
        
        inline fun <T> Iterable<T>.joinWithTransform(separator: String, noinline transform: ((T) -> CharSequence)): String {
            return this.joinToString(separator, transform = transform)
        }

        operator fun <K, V> MutableMap<out K, V>.plus(map: MutableMap<out K, V>): MutableMap<K, V> =
            LinkedHashMap(this).apply { putAll(map) }



        inline fun Boolean.isFalse() = !this
        inline fun Boolean.isTrue() = this
        inline fun Throwable.`throw`(): Nothing = throw this

//        typealias CodeBlock<T> = () -> T
//        inline fun <T>CodeBlock<T>.ifError(lambda: (Throwable) -> T): T {
//            return try {
//                this()
//            } catch (it: Throwable) {
//                lambda(it)
//            }
//        }
        
        fun <T> T?.unpackOrPANIC(): T {
            //@ core.niva:::0
            return this!!
        }
        fun <T> T?.unpackOrMsg(msg: String): T {
            //@ core.niva:::0
            return this ?: throw kotlin.Exception(msg)
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


fun putInMainKotlinCode(
    code: String,
    compilationTarget: CompilationTarget,
    pathToInfroProject: String,
    pathToMainNivaFileFolder: String
) = buildString {
    val devDataJsonPath = pathToMainNivaFileFolder / DEV_MODE_FILE_NAME
    val isJVMTarget = compilationTarget == CompilationTarget.jvm

    append("fun main(args: Array<String>) {\n")

    appendLine("Compiler.cliArgs = args.toList()")

    append("try {\n")

    appendLine(code)

    val catchExpressions = if (!isJVMTarget) """
        } catch (e: Throwable) {
        println("----------")
        println(e.message)
        println("----------")
        println(e.stackTraceToString())
    }
    """ else $$"""
        } catch (e: Throwable) {

        println("----------")
        println("\u001B[31m" + (e.message ?:  e.toString()) + "\u001B[0m")
        println("----------")
        val q = e.stackTrace
        
        val thisProjectPath = "$${pathToInfroProject.replace("\\", "/")}/src/"
        
        fun replaceLinesInStackTrace(x: List<StackTraceElement>) {

            class FileAndLine(val file: String, val line: Int)
            val getNearestNivaLine = { kotlinLine: Int, file: String ->
                if (kotlinLine == -2) FileAndLine("EntryPoint!", 0)
                else {
                    if (kotlinLine < 0) throw Exception("Cant find line")

                    
                    val thisFileContent = java.io.File(thisProjectPath + file).readText()
                    val lines = thisFileContent.split("\n")


                    val y = lines.getOrNull(kotlinLine - 1)
                    if (y != null) {
                        val splitted = y.split("@")
                        if (splitted.count() != 2) throw Exception("Cant find niva line above " + kotlinLine)
                            val fileAndLineNumber = splitted[1].trim()
                            val (file, lineStr) = fileAndLineNumber.split(":::")
                            val line = lineStr.toInt()
                            FileAndLine(file, line)
                        } else {
                            null
                        }
                }
            }
            
            val checkExistAsNivaFile = { file: String ->
                java.io.File(thisProjectPath + file).exists()
            }

            val stackTracesWithFiles = x.filter { it.fileName != null }
            stackTracesWithFiles.forEach {
                val pathToFile =
                    "main/kotlin/" + (if (it.fileName != "Main.kt") it.fileName.split(".").first() + "/" + it.fileName else it.fileName)
                val nivaLine = if (checkExistAsNivaFile(pathToFile))
                    getNearestNivaLine(it.lineNumber - 1, pathToFile)
                else FileAndLine(
                    it.fileName,
                    it.lineNumber
                )

                val methodLabel = "Method: "
                val fileLabel = "File: "
                val padding = 30
                
                if (nivaLine != null) {
                    val methodColWidth = padding - methodLabel.length 
                    
                    println(buildString {
                        append(methodLabel)
                    
                        val minWidth = it.methodName.length + 1
                        val targetWidth = maxOf(methodColWidth, minWidth)
                    
                        append(it.methodName.padEnd(targetWidth))
                    
                        append("\u001B[37m")
                        append(fileLabel)
                        append(nivaLine.file)
                        append(":")
                        append(nivaLine.line)
                        append("\u001B[0m")
                    })
                } else {
                    println("Can't locate source line of error from: ${it.methodName}")
                }
            }
        }
        val methodName = q[0].methodName
        replaceLinesInStackTrace(if (methodName == "unpackOrPANIC" || methodName == "throwWithMessage") q.drop(1) else q.toList())
    }
    """

    append(
        catchExpressions
    )

    // save DevMod json only in jvm
    if (isJVMTarget) {
        val safePath = devDataJsonPath.replace("\\", "\\\\")
        appendLine(
            """finally {
        if (NivaDevModeDB.wasDevModeUsed) java.io.File("$safePath").writeText(NivaDevModeDB.db.toJson().toString())
    }"""
        )
    }

    append("\n}\n")
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
