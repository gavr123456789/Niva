package main.utils

import main.frontend.meta.compileError
import main.frontend.util.createFakeToken
import java.io.File

enum class MainArgument {
    BUIlD,
    DISRT,
    RUN,
    SINGLE_FILE_PATH,
    INFO_ONLY, // only means no kotlin compilation
    USER_DEFINED_INFO_ONLY,
    RUN_FROM_IDEA,
    DAEMON
}

operator fun String.div(arg: String) = buildString { append(this@div, "/", arg) }


class ArgsManager(private val argsSet: Set<String>, private val args: Array<String>) {

    val compileOnly = "-c" in argsSet // args.find { it == "-c" } != null
    val infoIndex = args.indexOf("-i")
//    val infoOnly = infoIndex != -1
//    val infoUserOnly = "-iu" in argsSet//argsSet.find { it == "-iu" } != null
    val isShowTimeArg = "time" in argsSet//argsSet.find { it == "time" } != null

    fun mainArg(): MainArgument {
        return if (args.isNotEmpty()) {
            val firstArg = args[0]
            when (firstArg) {
                "run" -> MainArgument.RUN
                "build" -> MainArgument.BUIlD
                "distr" -> MainArgument.DISRT
                "info", "i" -> MainArgument.INFO_ONLY
                "infoUserOnly", "iu" -> MainArgument.USER_DEFINED_INFO_ONLY
                "dev" -> MainArgument.DAEMON
                else -> {
                    if (!File(firstArg).exists()) {
                        createFakeToken().compileError("File $firstArg is not exist, to run full project use ${WHITE}niva run$RESET, to run single file use ${WHITE}niva path/to/file$RESET")
                    }
                    MainArgument.SINGLE_FILE_PATH
                }
            }
        } else MainArgument.RUN_FROM_IDEA
    }
}

fun ArgsManager.time(executionTime: Long, kotlinPhase: Boolean) {
    if (isShowTimeArg) {
        if (kotlinPhase)
            println("Niva compilation time: $executionTime ms")
        else
            println("Kotlin compilation + exec time: $executionTime ms")
    }
}


class PathManager(val args: Array<String>, mainArg: MainArgument) {

    val pathToInfroProject = System.getProperty("user.home") / ".niva" / "infroProject"
    val pathWhereToGenerateKtAmper = pathToInfroProject / "src"
    val pathToGradle = pathToInfroProject / "build.gradle.kts"
    val pathToAmper = pathToInfroProject / "module.yaml"


    val mainNivaFileWhileDev = File("examples" / "Main" / "main.niva")
    private val pathToTheMainExample = mainNivaFileWhileDev.absolutePath

    private fun getPathToMain() =
        // just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
        if (args.count() >= 2) {
            // first arg is run already
            val fileNameArg = args[1]
            if (File(fileNameArg).exists()) {
                fileNameArg
            } else {
                createFakeToken().compileError("File $fileNameArg doesn't exist")
            }

        } else {
            val mainNiva = "main.niva"
            val mainScala = "main.scala"
            if (File(mainNiva).exists())
                mainNiva
            else if (File(mainScala).exists())
                mainScala
            else
                createFakeToken().compileError("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
        }


    val pathToNivaMainFile: String = when (mainArg) {
        MainArgument.SINGLE_FILE_PATH -> args[0]
        MainArgument.RUN_FROM_IDEA -> pathToTheMainExample

        MainArgument.RUN,
        MainArgument.INFO_ONLY,
        MainArgument.USER_DEFINED_INFO_ONLY,
        MainArgument.BUIlD,
        MainArgument.DISRT,
        MainArgument.DAEMON -> getPathToMain()
    }

    val nivaRootFolder = File(pathToNivaMainFile).toPath().toAbsolutePath().parent.toString()


    init {
        if (!File(pathToInfroProject).exists()) {
            createFakeToken().compileError("Path ${WHITE}`$pathToInfroProject`${RESET} doesn't exist, please move the infroProject there from ${WHITE}`/Niva/infroProject`${RED} there or run compile.sh")
        }
    }
}


fun help(args: Array<String>): Boolean {
    if (args.isNotEmpty() && (args[0] == "--help" || args[0] == "-help")) {
        println(HELP)
        return true
    }
    return false
}


fun getSpecialInfoArg(args: Array<String>, minusIindex: Int): String? {
    val specialPkgToInfoPrint = if (minusIindex != -1)
    // get word after -i
        if (args.count() - 1 > minusIindex)
            args[minusIindex + 1]
        else null
    else if (args.count() > 1 && args[0] == "info") {
        // if info filename then its getting all info, so return null
        // if info name then its getting special pkg
        if (File(args[1]).exists()) {
            null
        } else
            args[1]
    } else null

    return specialPkgToInfoPrint
}

fun warning(string: String) = println("${YEL}Warning: $string$RESET")


const val HELP = """
Usage:
    ${WHITE}FILE$RESET — compile and run single file
    ${WHITE}run$RESET — compile and run project from "main" file
    ${WHITE}run FILE$RESET — compile and run project from root file
    ${WHITE}build$RESET — compile only(creates binary in current folder)
    ${WHITE}info$RESET or ${WHITE}i$RESET — get info about packages
    ${WHITE}infoUserOnly$RESET or ${WHITE}iu$RESET — get info about user defined packages

Flags for single file run:
    -c      — compile only(creates binary in current folder)
    -i      — get info about packages(it is usable to pipe it to .md file)
    -iu     — print info only about user-defined types
    -i pkg  — print info only about specific pkg

In code: 
    > EXPR  — inline print result of expression in comment above
    >? TYPE — print all info about TYPE

Project configuration:
    Messages for ${YEL}Project$RESET:
    ${CYAN}target: $GREEN"TARGET"$RESET — target to jvm/linux/macos/windows(not supported yet)
    ${CYAN}mode: $GREEN"MODE"$RESET     — debug/release only for native targets, use debug for faster compilation
    
    ${CYAN}package: $GREEN"PKG"$RESET   — set package for the definitions in code below
    ${CYAN}protocol: $GREEN"NAME"$RESET — set protocol for the definitions in code below
    ${CYAN}use: $GREEN"PKG"$RESET       — set default pkg, like using namespace in C#/Vala
    
    Example: ${YEL}Project ${CYAN}target: $GREEN"linux" ${CYAN}mode: $GREEN"debug"$RESET 

Kotlin\Java interop:
    Messages for ${YEL}Bind$RESET:
    ${CYAN}package: $GREEN"PKG"$RESET  — bind package
    ${CYAN}content: $WHITE[CODE]$RESET — bindings
    
    Example:
    ${YEL}Bind ${CYAN}package: $GREEN"java.io" ${CYAN}content: $RESET[
        ${RED}type ${YEL}File ${CYAN}pathname: ${YEL}String
        ${YEL}File ${CYAN}exists ${RED}-> ${YEL}Boolean
        ${YEL}File ${CYAN}readText ${RED}-> ${YEL}String
    $RESET]
    ${WHITE}file = ${YEL}File ${CYAN}pathname: $GREEN"path/to/file"
    ${WHITE}text = ${WHITE}file ${CYAN}readText$RESET
    
    Messages for ${YEL}Project$RESET:
    ${CYAN}loadPackages: $RESET{$GREEN"PKG1" "PKG2"$RESET} — load package from Maven Central
    ${CYAN}import: $GREEN"PATH_TO_PKG"$RESET — add direct import to generated code

"""

