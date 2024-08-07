@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex
import kotlinx.coroutines.*

import main.utils.CompilerRunner
import main.utils.compileProjFromFile
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.utils.ArgsManager
import main.utils.GlobalVariables
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.daemon
import main.utils.getSpecialInfoArg
import main.utils.help
import main.utils.time
import java.io.File
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}



const val fakeFileSourceGOOD = """
app = [request::Request ->
    response = Response status: Status.OK
    // query = request query: "name"
    response body: "Hello, " + "gavr"
]

app asServer: (SunHttp port: 9000) |> start


client = JavaHttpClient new
printingClient::HttpHandler = PrintResponse new |> then: client

request = Request method: Method.GET uri: "http://localhost:9000"
responce = printingClient Request: request // BAD


"""

fun main2() = runBlocking { // this: CoroutineScope
    launch { // launch a new coroutine and continue
        delay(4534) // non-blocking delay for 1 second (default time unit is ms)
        println("World!") // print after delay
    }
    println("Hello") // main coroutine continues while a previous one is delayed
}

fun main(args: Array<String>) {
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/experiments/main.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/GTK/AdwLearnGreek/main.niva")
//    val args = arrayOf("build", "/home/gavr/Documents/Projects/bazar/Examples/server/main.niva")

//    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/server/http.bind.niva"
////    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/GTK/AdwLearnGreek/main.niva"
//
//    try {
//        val ls = LS()
//        val resolver = ls.resolveAll(qqq)
//
//
//        ls.resolveAllWithChangedFile(
//            qqq,
//            fakeFileSourceGOOD
//        )
//
//        ls.onCompletion(qqq, 6, 1)
//        ls.onCompletion(qqq, 6, 1)
//    }
//    catch (e: OnCompletionException) {
//        println(e.scope)
//    }

    if (help(args)) return
    run(args)
}

// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun getPathToMainOrSingleFile(args: Array<String>): String =
    if (args.count() >= 2) {
        // niva run/test/build "sas.niva"
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
    }


    else {
        // niva run\test\build...
        val mainNiva = "main.niva"
        val mainScala = "main.scala"

        if (File(mainNiva).exists())
            mainNiva
        else if (File(mainScala).exists())
            mainScala
        else {
            println("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
            exitProcess(-1)
//                createFakeToken().compileError("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
        }
    }

fun run(args: Array<String>) {
    val argsSet = args.toSet()

//    readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(argsSet, args)
    val mainArg = am.mainArg()
    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg)

    if (mainArg == MainArgument.DAEMON) {
        daemon(pm, mainArg)
    }

    // resolve all files!
    val resolver = try {
        compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH, tests = mainArg == MainArgument.TEST, verbose = am.verbose)
    } catch (e: CompilerError) {
        if (!GlobalVariables.isLspMode)
            println(e.message)
        exitProcess(-1)
    }
    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler = CompilerRunner(
        pm.pathToInfroProject,
        inlineRepl,
        resolver.compilationTarget,
        resolver.compilationMode,
        pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
        resolver
    )


    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD -> compiler.runCommand(dist = true, buildFatJar = true)
        MainArgument.DISRT -> compiler.runCommand(dist = true)
        MainArgument.RUN ->
            compiler.runCommand()

        MainArgument.TEST -> {
            compiler.runCommand(runTests = true)
        }

            MainArgument.SINGLE_FILE_PATH -> {
            compiler.runCommand(dist = am.compileOnly)
        }

        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDEA -> {
            compiler.runCommand(dist = false)
        }

        MainArgument.DAEMON -> {
            daemon(pm, mainArg)
        }

        MainArgument.LSP -> TODO()

    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

