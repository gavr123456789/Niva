@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex

import main.utils.CompilerRunner
import main.utils.compileProjFromFile
import java.io.*
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
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}


const val fakeFileSourceGOOD = """
/// this is a @Person and its @Success full
/// second param is an @Int
type Person name: String age: Int
{name age} = Person name: "Alice" age: 24
name echo
age echo
x::Int = 4
type Success captures: MutableList::String
Success captures: {}

"""


interface Element {
    fun render(builder: StringBuilder, indent: String)
}

class TextElement(val text: String) : Element {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$text\n")
    }
}

@DslMarker
annotation class HtmlTagMarker

@HtmlTagMarker
abstract class Tag(val name: String) : Element {
    val children = arrayListOf<Element>()
    val attributes = hashMapOf<String, String>()

    protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent<$name${renderAttributes()}>\n")
        for (c in children) {
            c.render(builder, indent + "  ")
        }
        builder.append("$indent</$name>\n")
    }

    private fun renderAttributes(): String {
        val builder = StringBuilder()
        for ((attr, value) in attributes) {
            builder.append(" $attr=\"$value\"")
        }
        return builder.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

abstract class TagWithText(name: String) : Tag(name) {
    operator fun String.unaryPlus() {
        children.add(TextElement(this))
    }
}

class HTML : TagWithText("html") {
    fun head(init: Head.() -> Unit) = initTag(Head(), init)

    fun body(init: Body.() -> Unit) = initTag(Body(), init)
}

class Head : TagWithText("head") {
    fun title(init: Title.() -> Unit) = initTag(Title(), init)
}

class Title : TagWithText("title")

abstract class BodyTag(name: String) : TagWithText(name) {
    fun b(init: B.() -> Unit) = initTag(B(), init)
    fun p(init: P.() -> Unit) = initTag(P(), init)
    fun h1(init: H1.() -> Unit) = initTag(H1(), init)
    fun a(href: String, init: A.() -> Unit) {
        val a = initTag(A(), init)
        a.href = href
    }
}

class Body : BodyTag("body")
class B : BodyTag("b")
class P : BodyTag("p")
class H1 : BodyTag("h1")

class A : BodyTag("a") {
    var href: String
        get() = attributes["href"]!!
        set(value) {
            attributes["href"] = value
        }
}

fun html(init: HTML.() -> Unit): HTML {
    val html = HTML()
    html.init()
    return html
}

///

fun main(args: Array<String>) {
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/experiments/main.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/GTK/AdwLearnGreek/main.niva")
//    val args = arrayOf("build", "/home/gavr/Documents/Projects/bazar/Programs/todosGleam/main.niva")
    if (help(args)) return

//    html {
//        head {
//            title { +"XML encoding with Kotlin" }
//        }
//    }

//    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/experiments/main.niva"
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
//    }
//    catch (e: OnCompletionException) {
//        println(e.scope)
//    }

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

