package main.utils

import main.codogen.BuildSystem
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import java.io.File
import kotlin.system.exitProcess

enum class MainArgument {
    BUIlD,
    DISRT,
    RUN,
    RUN_MILL,
    BUILD_MILL,
    TEST_MILL,

    SINGLE_FILE_PATH,
    INFO_ONLY, // only means no kotlin compilation
    USER_DEFINED_INFO_ONLY,
    RUN_FROM_IDEA,
    DEV_MODE, TEST, LSP
}

operator fun String.div(arg: String) = buildString { append(this@div, "/", arg) }


const val OUT_NAME_ARG = "--out-name="
class ArgsManager(val args: MutableList<String>) {

    val compileOnly = "-c" in args // args.find { it == "-c" } != null
    val js = if ("--js" in args) {
        args.remove("--js")
        true
    } else false
    val verbose = if ("--verbose" in args) {
        args.remove("--verbose")
        true
    } else false
    val mill = if ("--mill" in args) {
        args.remove("--mill")
        true
    } else false
    val buildSystem = if (mill) BuildSystem.Mill else BuildSystem.Gradle

    val jsRuntime = run {
        val jsRuntimeArg1 = this@ArgsManager.args.find { it.startsWith("--js-runtime=") }
        if (jsRuntimeArg1 != null) {
            this@ArgsManager.args.remove(jsRuntimeArg1)
            jsRuntimeArg1.replaceFirst("--js-runtime=", "")
        } else "bun"
    }

    val outputRename = run {
        val outputRename1 = this@ArgsManager.args.find { it.startsWith(OUT_NAME_ARG) }
        if (outputRename1 != null) {
            this@ArgsManager.args.remove(outputRename1)
            outputRename1.replaceFirst(OUT_NAME_ARG, "")
        } else null
    }
    val infoIndex = args.indexOf("-i")
    val isShowTimeArg = verbose

    fun mainArg(): MainArgument {
        return if (args.isNotEmpty()) {
            when (val firstArg = args[0]) {
                "run" -> if (mill) MainArgument.RUN_MILL
                    else MainArgument.RUN

                "build" -> if (mill)
                    MainArgument.BUILD_MILL
                else
                    MainArgument.BUIlD
                "distr" -> MainArgument.DISRT
                "info", "i" -> MainArgument.INFO_ONLY
                "infoUserOnly", "iu" -> MainArgument.USER_DEFINED_INFO_ONLY
                "dev" -> MainArgument.DEV_MODE
                "test" -> if (mill) MainArgument.TEST_MILL
                else MainArgument.TEST
                else -> {
                    if (!File(firstArg).exists()) {
                        println("There are no such command or File \"$firstArg\" is not exist, to run all files starting from main.niva run ${WHITE}niva run$RESET, to run single file use ${WHITE}niva path/to/file$RESET")
                        println(HELP)
                        exitProcess(0)
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
            println("${CYAN}Verbose$RESET: backend lang compilation + exec time: $executionTime ms")
        else
            println("${CYAN}Verbose$RESET: Niva compilation time: $executionTime ms")
    }
}

fun MainArgument.mainArgIsMill() = when(this) {
    MainArgument.RUN_MILL,
    MainArgument.BUILD_MILL,
    MainArgument.TEST_MILL -> true
    else -> false
}
// TODO replace with function that return a class with all paths
class PathManager(nivaMainOrSingleFile: String, mainArg: MainArgument, buildSystem: BuildSystem?) {
    val mainNivaFileWhileDevFromIdea = File("examples" / "Main" / "main.niva")
    private val pathToTheMainExample = mainNivaFileWhileDevFromIdea.absolutePath


    val pathToNivaMainFile: String = when (mainArg) {
        MainArgument.SINGLE_FILE_PATH,
        MainArgument.LSP,
        MainArgument.RUN,
        MainArgument.INFO_ONLY,
        MainArgument.USER_DEFINED_INFO_ONLY,
        MainArgument.BUIlD,
        MainArgument.DISRT,
        MainArgument.DEV_MODE,
        MainArgument.TEST,

        MainArgument.RUN_MILL,
        MainArgument.BUILD_MILL,
        MainArgument.TEST_MILL
            -> nivaMainOrSingleFile

        MainArgument.RUN_FROM_IDEA -> pathToTheMainExample

    }
    // parent of main.niva
    val nivaRootFolder = File(pathToNivaMainFile).toPath().toAbsolutePath().parent.toString()

    val pathToInfroProject = if (buildSystem == BuildSystem.Mill)
        nivaRootFolder / ".nivaBuild"
    else
        System.getProperty("user.home") / ".niva" / "infroProject"

    val pathWhereToGenerateKtAmper = pathToInfroProject // path before src or test
    val pathToBuildFileGradle = pathToInfroProject / "build.gradle.kts"
    val pathToBuildFileAmper = pathToInfroProject / "module.yaml"
    val pathToBuildFileMill = pathToInfroProject / "build.mill"

    init {
        val codegenProjFolder = File(pathToInfroProject)
        val isBuildFileExist = codegenProjFolder.exists()
        val isMillBuild = mainArg.mainArgIsMill()
        if (!isBuildFileExist) {
            if (isMillBuild) {
                // we don't need to create dir here, because later we this is a trigger to generate .bat file
//                codegenProjFolder.mkdirs()
            }
            else {
                createFakeToken().compileError("Path ${WHITE}`$pathToInfroProject`${RESET} doesn't exist, please move the infroProject there from ${WHITE}`/Niva/infroProject`${RED} there or run compile.sh")
            }
        }

    }
}


fun help(args: Array<String>): Boolean {
    if (args.isEmpty())
        return true
    if (args[0] == "--help" || args[0] == "-help") {
        println(HELP)
        return true
    }
    return false
}


fun getSpecialInfoArg(args: List<String>, minusIindex: Int): String? {
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

fun warning(string: String) {
    if (!GlobalVariables.isLspMode)
        println("${YEL}Warning:$RESET $string$RESET")
}

//Flags for single file run:
//-c      — compile only(creates binary in current folder)
//-i      — get info about packages(it is usable to pipe it to .md file)
//-iu     — print info only about user-defined types
//-i pkg  — print info only about specific pkg
const val HELP = """
Usage:
    ${WHITE}FILE$RESET — compile and run single file
    ${WHITE}run$RESET — compile and run project from "main" file
    ${WHITE}dev$RESET — rerun on changed files or input, for faster iteration
    ${WHITE}run FILE$RESET — compile and run project from root file
    ${WHITE}run FILE -v$RESET or ${WHITE}-verbose$RESET — with verbose printing
    ${WHITE}build$RESET — compile only(creates jar\binary in current folder)
        to rename binary use `niva --out-name=NAME build`
    ${WHITE}info$RESET or ${WHITE}i$RESET — get info about packages
    ${WHITE}distr$RESET — create easy to share jvm distribution
    ${WHITE}infoUserOnly$RESET or ${WHITE}iu$RESET — get info about user defined packages

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

