@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex

import main.utils.Compiler
import main.utils.compileProjFromFile
import java.io.*
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.utils.ArgsManager
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.daemon
import main.utils.getSpecialInfoArg
import main.utils.help
import main.utils.time
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}


fun main(args: Array<String>) {
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/JSON/lexer.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/GTK/AdwDela/main.niva")

    if (help(args)) return

    run(args)
}


fun run(args: Array<String>) {
    val argsSet = args.toSet()

//    readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(argsSet, args)
    val mainArg = am.mainArg()
    val pm = PathManager(args, mainArg)

    if (mainArg == MainArgument.DAEMON) {
        daemon(pm, mainArg)
    }

    // resolve all files!
    val resolver = try {
        compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH)
    } catch (e: CompilerError) {
        println(e.message)
        exitProcess(0)
    }
    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler = Compiler(
        pm.pathToInfroProject,
        inlineRepl,
        resolver.compilationTarget,
        resolver.compilationMode,
        pm.mainNivaFileWhileDev.nameWithoutExtension,
        resolver
    )


    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD -> compiler.run(dist = true, buildFatJar = true)
        MainArgument.DISRT -> compiler.run(dist = true)
        MainArgument.RUN ->
            compiler.run()

        MainArgument.SINGLE_FILE_PATH -> {
            compiler.run(dist = am.compileOnly, singleFile = true)
        }

        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDEA -> {
            compiler.run(dist = false, singleFile = true)
        }

        MainArgument.DAEMON -> {
            daemon(pm, mainArg)
        }

    }

    am.time(System.currentTimeMillis() - secondTime, true)
}

