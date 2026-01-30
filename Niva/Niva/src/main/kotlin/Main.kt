//@file:Suppress("unused")

package main

// import main.languageServer.LS
// import main.languageServer.OnCompletionException
// import main.languageServer.onCompletion
// import main.languageServer.resolveAllFirstTime
// import main.languageServer.resolveNonIncremental

import java.io.File
import kotlin.system.exitProcess
import main.frontend.meta.CompilerError
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.codogenjs.generateJsProject
import main.utils.*

fun sus() {
    
}
fun main(args: Array<String>) {

    var x: Int = 0

    val n = 4
    if (n == 1) x = 1
    else if (n == 2) x = 2
    else x = 3

    println(x)

//    val ls = LS { println("Niva LS: $it") }
//    val fromJson = readFromJson("devModeData.json")
//
//    fromJson.data.forEach { (fileName, value) ->
//        val file = File(fileName)
//        value.forEach { (lineNum, values) ->
//            values.forEach {
//                val w = it.toIdentifierExpr(file, lineNum)
//                ls.megaStore.addNew(
//                    s = w,
//                    scope = mapOf(),
//                )
//            }
//        }
//    }
//    println(fromJson)
///////////////////////

//    val args = arrayOf("run","/Users/gavriil.maksyutenko/Documents/Fun/Kotlin/Niva/Niva/Niva/examples/Main/main.niva")
    //    val qqq =
    // "file:///home/gavr/Documents/Projects/Fun/Niva/Niva/NivaInNiva/front/lexer/lex.niva"
    //    try {
    //        val ls = LS()
    //        val resolver = ls.resolveAllFirstTime(qqq, true, null)
    //
    //        // 1 file to decl не содержит main
    //        val resolver3 =  ls.resolveNonIncremental(qqq, fakeFileSourceGOOD)
    ////        ls.resolveIncremental(qqq, fakeFileSourceGOOD)
    //        val q = ls.onCompletion(qqq, line = 9, character = 17)
    //        println(q)
    //    }
    //    catch (e: OnCompletionException) {
    //        println(e.scope)
    //    }

    // sasat
    if (help(args))
        return
    run(args)
}

// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun run(args2: Array<String>) {
    val args = args2.toMutableList()

    //
    // readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(args)
    val mainArg = am.mainArg()
    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg, am.buildSystem)

    if (mainArg == MainArgument.DEV_MODE) {
        daemon(pm, mainArg, am)
    }

    // resolve all files!
    val resolver =
            try {
                compileProjFromFile(
                    pm,
                    dontRunCodegen = false,
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

    if (am.js) {
        val workingDir = File(pm.nivaRootFolder)
        val outputDir = if (am.jsDist) {
            File(workingDir, "dist")
        } else {
            File(pm.pathWhereToGenerateKtAmper)
        }
        
        val mainProject = resolver.projects[resolver.projectName] ?: resolver.projects.values.first()
        generateJsProject(outputDir, mainProject, resolver.topLevelStatements)

        val mainJsFile = outputDir.resolve("mainNiva.js").absolutePath
        val command = if (am.jsRuntime == "gjs") {
            "gjs -m $mainJsFile"
        } else {
            "${am.jsRuntime} $mainJsFile"
        }
        command.runCommand(workingDir, withOutputCapture = true)
        am.time(System.currentTimeMillis() - secondTime, true)
        return
    }

    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler =
            CompilerRunner(
                    pm.pathToInfroProject,
                    inlineRepl,
                    resolver.compilationTarget,
                    resolver.compilationMode,
                    pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
                    resolver
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
            // 1) niva test FILE TESTNAME  -> --tests "Class.TestName"
            // 2) niva test TESTNAME       -> --tests "*.TestName"
            val testFilter = if (args[0] == "test") {
                when (args.size) {
                    3 -> {
                        val cls = File(args[1]).nameWithoutExtension
                        val testName = args[2]
                        "*$cls*.$testName"
                    }
                    2 -> {
                        val testName = args[1]
                        "*.$testName"
                    }
                    else -> null
                }
            } else null
            compiler.runGradleAmperBuildCommand(runTests = true, testFilter = testFilter)
        }
        MainArgument.SINGLE_FILE_PATH -> compiler.runGradleAmperBuildCommand(dist = am.compileOnly)
        MainArgument.INFO_ONLY -> compiler.infoPrint(false, specialPkgToInfoPrint)
        MainArgument.USER_DEFINED_INFO_ONLY -> compiler.infoPrint(true, specialPkgToInfoPrint)
        MainArgument.RUN_FROM_IDEA -> compiler.runGradleAmperBuildCommand(dist = false)
        MainArgument.DEV_MODE -> daemon(pm, mainArg, am)
        MainArgument.LSP -> TODO()
    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

enum class Option {
    RUN,
    BUILD,
    TEST
}

fun getPathToMainOrSingleFile(args: List<String>): String =
        if (args.isNotEmpty() && args[0] == "test") {
            // Special handling for `niva test [FILE] [TESTNAME]`:
            // FILE may be a test class name, not a filesystem path. If args[1] exists as a file, use it;
            // otherwise, fall back to project root file discovery (main.niva or main.scala).
            val candidate = if (args.count() >= 2 && File(args[1]).exists()) args[1] else null
            candidate ?: run {
                val mainNiva = "main.niva"
                val mainScala = "main.scala"
                when {
                    File(mainNiva).exists() -> mainNiva
                    File(mainScala).exists() -> mainScala
                    else -> {
                        println(
                            "Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`"
                        )
                        exitProcess(-1)
                    }
                }
            }
        } else if (args.count() >= 2) {
            // niva run/build "sas.niva"
            val fileNameArg = args[1]
            if (File(fileNameArg).exists()) {
                fileNameArg
            } else {
                createFakeToken().compileError("File $fileNameArg doesn't exist")
            }
        } else if (args.count() == 1 && args[0].contains(".")) {
            // Single arg "niva sas.niva"
            args[0]
        } else if (args.count() == 0) {
            File("examples/Main/main.niva").absolutePath
        } else {
            // niva run\test\build...
            val mainNiva = "main.niva"
            val mainScala = "main.scala"

            if (File(mainNiva).exists()) mainNiva
            else if (File(mainScala).exists()) mainScala
            else {
                println(
                        "Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`"
                )
                exitProcess(-1)
                //                createFakeToken().compileError("Can't find `main.niva` or
                // `main.scala` please specify the file after run line `niva run file.niva`")
            }
        }
