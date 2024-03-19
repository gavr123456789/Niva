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


//    val q = listOf(1, 2)//.sumOf {  }//.partition {  }//.fold(5) { acc, next -> acc + next }
//    val w = listOf(1, 2).fold(mutableSetOf<Int>()) {acc, each -> if (each % 2 == 0) acc.add(each); acc }

//    val args = arrayOf("/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva", "-i")
//    val args = arrayOf("info", "/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva")
    val args = arrayOf("run", "/home/gavr/Documents/Fun/bazar/Examples/GTK/main.niva")

//    val args = arrayOf("dev", "/home/gavr/Documents/Fun/Niva/Niva/Niva/examples/Main/main.niva")
//    val args = arrayOf("dev", "/home/gavr/Documents/Projects/Fun/Niva/Niva/Niva/examples/Main/main.niva")

    if (help(args)) return

    run(args)
}

//fun readJar(pathToJar: String) {
//    val jarUrl = URL("file:///$pathToJar")
//    val loader = URLClassLoader(arrayOf(jarUrl))
////    loader
//    val jar = JarFile(pathToJar)
//
//    val entryes = jar.entries()
//
//    var counter = 0
//    var errorConter = 0
//    val nivaTypes = mutableListOf<Type.UserType>()
//    while (entryes.hasMoreElements()) {
//        val e = entryes.nextElement()
//        if (!e.isDirectory && e.name.endsWith(".class")) {
//            counter++
//            val className = e.name.replace("/", ".").replace(".class", "")
//            try {
//                val c = loader.loadClass(className)
//                val methods = c.methods
////                val params = w.parameters
//                val fields = c.fields.filter { it.name != "INSTANCE" }
//                if (fields.count() > 1 || fields.count() == 0) {
//                    nivaTypes.add(
//                        Type.UserType(
//                            name = c.name,
//                            typeArgumentList = listOf(),
////                            fields = fields.map {
////                                TypeField(
////                                    name = it.name,
////                                    type = Type.UnknownGenericType(name = it.name, pkg = it.type?.`package`?.name ?: "???")
////                                )
////                            }.toMutableList(),
//                            fields = mutableListOf(),
//                            pkg =  c?.`package`?.name ?: "???"
//                        )
//                    )
//                }
//            } catch (e: Throwable) {
//                errorConter++
//                println("ОЙ!")
//            }
//        }
//
//    }
//
//    jar.close()
//}

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

