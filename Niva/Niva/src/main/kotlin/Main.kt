@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex

import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
//import main.languageServer.LS
//import main.languageServer.OnCompletionException
//import main.languageServer.onCompletion
//import main.languageServer.resolveAllFirstTime
//import main.languageServer.resolveNonIncremental


import main.utils.*
import java.io.File
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}



const val fakeFileSourceGOOD = """
testString::String = "++++ > < << >> /*some comment*/ DPND somefile.ebf foo % foo # bar\n@bar\r!#bar\tEND"

("Test String: " + testString) echo

lexer = Lexer input: testString 
  
  ast = AST empty
[lexer ch != '\u0000'] whileTrue: [
    tok = lexer nextToken
    ast append: tok
]

ast print // debug

astc = ASTC empty
parser = Parser ast: ast
[parser tok kind != TokenType.END] whileTrue: [
    command = parser nextToken
    astc append: command
]

astc print //debug

interpreter = Interpreter ast: astc
// run interpreter nextCommand until it returns EOP (End of Program) Result
// if it returns an error, send Result to error handler and break loop

"""

fun main(args: Array<String>) {

//    val args = arrayOf("-h")
//    val args = arrayOf("--mill")
//    val args = arrayOf("build", "/Users/gavr/Documents/Fun/niva-code/writing-an-interpreter-in-niva/niva/main.niva")
//    val args = arrayOf("build", "/Users/gavr/Documents/Fun/niva-code/eBF-in-Niva/src/main.niva", "--out-name=sas")
//    val args = arrayOf("--verbose","build", "/home/gavr/Documents/Projects/bazar/Examples/turtle/main.niva")
//    val args = arrayOf("--mill", "build", "/Users/gavr/Documents/Fun/niva-code/writing-an-interpreter-in-niva/niva/main.niva")

//    val qqq = "file:///home/gavr/Documents/Projects/Fun/eBF-in-Niva/main.niva"
//
//    try {
//        val ls = LS()
//        val resolver = ls.resolveAllFirstTime(qqq, true)
//        // 1 file to decl не содержит main
//        val resolver3 =  ls.resolveNonIncremental(qqq, fakeFileSourceGOOD)
////        ls.resolveIncremental(qqq, fakeFileSourceGOOD)
//        val q = ls.onCompletion(qqq, line = 9, character = 17)
//        println(q)
//    }
//    catch (e: OnCompletionException) {
//        println(e.scope)
//    }

    if (help(args)) return
    run(args)
}

// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun run(args2: Array<String>) {
    val args = args2.toMutableList()

//    readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(args)
    val mainArg = am.mainArg()
    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg)

    if (mainArg == MainArgument.DAEMON) {
        daemon(pm, mainArg)
    }

    // resolve all files!
    val resolver = try {
        compileProjFromFile(
            pm,
            dontRunCodegen = false,
            compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH,
            tests = mainArg == MainArgument.TEST,
            verbose = am.verbose,
            buildSystem = am.buildSystem
        )
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
        MainArgument.BUIlD -> compiler.runGradleAmperBuildCommand(dist = true, buildFatJar = true, outputRename = am.outputRename)
        MainArgument.DISRT -> compiler.runGradleAmperBuildCommand(dist = true)
        MainArgument.RUN ->
            compiler.runGradleAmperBuildCommand()
        MainArgument.RUN_MILL ->
            compiler.runMill(Option.RUN, am.outputRename)
        MainArgument.BUILD_MILL ->
            compiler.runMill(Option.BUILD, am.outputRename)
        MainArgument.TEST_MILL ->
            compiler.runMill(Option.TEST, am.outputRename)


        MainArgument.TEST -> {
            compiler.runGradleAmperBuildCommand(runTests = true)
        }

        MainArgument.SINGLE_FILE_PATH ->
            compiler.runGradleAmperBuildCommand(dist = am.compileOnly)


        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDEA ->
            compiler.runGradleAmperBuildCommand(dist = false)

        MainArgument.DAEMON ->
            daemon(pm, mainArg)


        MainArgument.LSP -> TODO()
    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

enum class Option {
    RUN, BUILD, TEST
}

fun getPathToMainOrSingleFile(args: List<String>): String =
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