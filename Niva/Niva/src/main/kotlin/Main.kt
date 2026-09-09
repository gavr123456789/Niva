//@file:Suppress("unused")

package main


import java.io.File
import kotlin.system.exitProcess
import main.formatter.formatNivaFile
import main.frontend.meta.CompilerError
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.utils.*
import utils.testingLS




fun main(args: Array<String>) {

    val x = mutableMapOf(1 to "a", 2 to "b")
    x.putAll(mapOf(3 to "4"))
//    val args = arrayOf("run","")
//        testingLS()
    if (help(args))
        return
    run(args)
}



// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun run(args2: Array<String>) {
    val args = args2.toMutableList()

    // readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(args)
    val mainArg = am.mainArg()

    if (mainArg == MainArgument.FORMAT) {
        format(args)
        return
    }

    if (mainArg == MainArgument.NEW) {
        createNewProject()
        return
    }

    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg, am.buildSystem)

    if (mainArg == MainArgument.DEV_MODE) {
        daemon(pm, mainArg, am)
    }

    // resolve all files!
    val resolver =
            try {
                compileProjFromFile(
                    pm,
                    dontRunCodegen = mainArg == MainArgument.GRAPHVIZ,
                    compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH,
                    tests = mainArg == MainArgument.TEST,
                    verbose = am.verbose,
                    buildSystem = am.buildSystem
                )
            } catch (e: CompilerError) {
                if (!GlobalVariables.isLspMode) println(e.message)
                exitProcess(-1)
            }
    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)

    val compiler =
            CompilerRunner(
                    pm.pathToInfroProject,
                    resolver.compilationTarget,
                    resolver.compilationMode,
                    pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
                    resolver,
                    nativeImageGradleProperty = am.nativeImageGradleProperty
            )

    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD ->
                compiler.runGradleAmperBuildCommand(
                        dist = true,
                        buildFatJar = true,
                        outputRename = am.outputRename
                )
        MainArgument.DISRT -> compiler.runGradleAmperBuildCommand(dist = true)
        MainArgument.RUN -> compiler.runGradleAmperBuildCommand()
        MainArgument.RUN_MILL -> compiler.runMill(Option.RUN, am.outputRename)
        MainArgument.BUILD_MILL -> compiler.runMill(Option.BUILD, am.outputRename)
        MainArgument.TEST_MILL -> compiler.runMill(Option.TEST, am.outputRename)
        MainArgument.TEST -> {
            // 1) niva test FILE TESTNAME  -> --tests "*File*.TestName"
            // 2) niva test NAME_OR_PKG    -> --tests "*NAME*" (class/file) + "*.NAME" (method)
            val testFilters: List<String>? = if (args[0] == "test") {
                when (args.size) {
                    3 -> {
                        val cls = File(args[1]).nameWithoutExtension
                        val testName = args[2]
                        listOf("*$cls*.$testName")
                    }
                    2 -> {
                        val nameOrPkg = args[1]
                        listOf("*$nameOrPkg*", "*.$nameOrPkg")
                    }
                    else -> null
                }
            } else null
            compiler.runGradleAmperBuildCommand(runTests = true, testFilter = testFilters)
        }
        MainArgument.SINGLE_FILE_PATH -> compiler.runGradleAmperBuildCommand(dist = am.compileOnly)
        MainArgument.INFO_ONLY -> compiler.infoPrint(false, specialPkgToInfoPrint)
        MainArgument.USER_DEFINED_INFO_ONLY -> compiler.infoPrint(true, specialPkgToInfoPrint)
        MainArgument.RUN_FROM_IDEA -> compiler.runGradleAmperBuildCommand(dist = false)
        MainArgument.DEV_MODE -> daemon(pm, mainArg, am)
        MainArgument.WATCH -> {
            val watchArgs = args.toMutableList()
            if (watchArgs.size > 1) {
                watchArgs[1] = File(pm.pathToNivaMainFile).absolutePath
            } else {
                watchArgs += File(pm.pathToNivaMainFile).absolutePath
            }
            compiler.runGradleAmperBuildCommand(programArgs = watchArgs)
        }
        MainArgument.GRAPHVIZ -> {
            graphviz(pm, args, resolver)
        }
        MainArgument.LSP -> TODO()
    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

fun format(args: List<String>) {
    val path = args.getOrNull(1)
    if (path == null) {
        System.err.println("Usage: niva format FILE")
        exitProcess(1)
    }

    val file = File(path)
    if (!file.exists() || !file.isFile) {
        System.err.println("File $path doesn't exist")
        exitProcess(1)
    }

    formatNivaFile(file)
}

enum class Option {
    RUN,
    BUILD,
    TEST
}

const val MAIN_NIVA = "main.niva"
fun getPathToMainOrSingleFile(args: List<String>): String {
    fun fileExists(path: String) = File(path).exists()
    fun mainFileInDirectory(path: String): File = File(path, MAIN_NIVA)
    fun pathToMainFileOrSelf(path: String): String {
        val file = File(path)
        if (!file.isDirectory) return path
        val main = mainFileInDirectory(path)
        return if (main.exists())
            main.path
        else
            createFakeToken().compileError("Can't find `$MAIN_NIVA` in directory $path")
    }

    fun findMainNivaOrDie(): String {
        val main = MAIN_NIVA
        if (fileExists(main)) return main

        println("Can't find `$MAIN_NIVA` please specify the file after run line `niva run file.niva`")
        exitProcess(-1)
    }

    fun findMainNivaOrCompileError(message: String): String {
        val main = MAIN_NIVA
        return if (fileExists(main)) main else createFakeToken().compileError(message)
    }

    val cmd = args.firstOrNull()

    return when {
        // niva (no args)
        args.isEmpty() ->
            File("examples/Main/$MAIN_NIVA").absolutePath

        // Special: `niva test [FILE] [TESTNAME]`
        cmd == "test" -> {
            val candidate = args.getOrNull(1)?.takeIf { fileExists(it) }
            candidate ?: findMainNivaOrDie()
        }

        // niva run/build/graphviz <file>
        args.size >= 2 -> {
            val fileNameArg = args[1]

            when {
                cmd == "graphviz" && fileExists(MAIN_NIVA) ->
                    MAIN_NIVA

                fileExists(fileNameArg) ->
                    pathToMainFileOrSelf(fileNameArg)

                cmd == "graphviz" ->
                    findMainNivaOrCompileError("File $fileNameArg doesn't exist and default $MAIN_NIVA not found")

                else ->
                    createFakeToken().compileError("File $fileNameArg doesn't exist")
            }
        }

        // Single arg "niva sas.niva"
        args.size == 1 && args[0].contains(".") ->
            args[0]

        // niva run/test/build... (без файла)
        else ->
            findMainNivaOrDie()
    }
}
