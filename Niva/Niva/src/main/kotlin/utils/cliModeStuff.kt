package main.utils

import main.codogen.BuildSystem
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
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
    DEV_MODE, TEST, LSP, GRAPHVIZ
}

operator fun String.div(arg: String) = buildString { append(this@div, "/", arg) }


const val OUT_NAME_ARG = "--out-name="
class ArgsManager(val args: MutableList<String>) {

    val compileOnly = "-c" in args // args.find { it == "-c" } != null
    val js = if ("--js" in args) {
        args.remove("--js")
        true
    } else false
    val jsDist = if ("--jsdist" in args) {
        args.remove("--jsdist")
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
    val nativeRelease = if ("--nativeRelease" in args) {
        args.remove("--nativeRelease")
        true
    } else false
    val nativeDebug = if ("--nativeDebug" in args) {
        args.remove("--nativeDebug")
        true
    } else false
    val buildSystem = if (mill) BuildSystem.Mill else BuildSystem.Gradle
    val nativeImageGradleProperty = run {
        when {
            nativeDebug && nativeRelease -> {
                System.err.println("Both --nativeRelease and --nativeDebug are set; using --nativeDebug")
                "-PnativeDebug"
            }
            nativeDebug -> "-PnativeDebug"
            nativeRelease -> "-PnativeRelease"
            else -> null
        }
    }

    val jsRuntime = run {
        val jsRuntimeArg1 = this@ArgsManager.args.find { it.startsWith("--js-runtime=") }
        if (jsRuntimeArg1 != null) {
            this@ArgsManager.args.remove(jsRuntimeArg1)
            jsRuntimeArg1.replaceFirst("--js-runtime=", "")
        } else {
            if (isCommandAvailable("bun")) "bun"
            else if (isCommandAvailable("node")) "node"
            else {
                System.err.println("${RED}Error: neither bun nor node are available. Please install one of them or specify runtime with --js-runtime=")
                exitProcess(1)
            }
        }
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
                "graphviz" -> MainArgument.GRAPHVIZ
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
        MainArgument.GRAPHVIZ,

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
        val isMillBuild = mainArg.mainArgIsMill()
        if (!codegenProjFolder.exists() && !isMillBuild) {
            val extracted = ensureInfroProjectFromResources(codegenProjFolder.toPath())
            if (!extracted) {
                createFakeToken().compileError(
                    "Path ${WHITE}`$pathToInfroProject`${RESET} doesn't exist and infroProject resource could not be extracted"
                )
            }
        }
        if (!codegenProjFolder.exists()) {
            if (isMillBuild) {
                // we don't need to create dir here, because later we this is a trigger to generate .bat file
//                codegenProjFolder.mkdirs()
            }
            else {
                createFakeToken().compileError(
                    "Path ${WHITE}`$pathToInfroProject`${RESET} doesn't exist, please copy infroProject there or reinstall niva"
                )
            }
        }

    }
}

private fun ensureInfroProjectFromResources(targetDir: Path): Boolean {
    if (Files.exists(targetDir)) return true
    val parentDir = targetDir.parent
    if (parentDir != null) {
        Files.createDirectories(parentDir)
    }
    return try {
        val extracted = extractZipResource("infroProject.zip", targetDir) ||
            copyResourceDirectory("infroProject", targetDir)
        if (extracted) {
            makeGradleWrapperExecutable(targetDir)
        }
        extracted && Files.exists(targetDir)
    } catch (_: Exception) {
        false
    }
}

private fun extractZipResource(resourceName: String, targetDir: Path): Boolean {
    val classLoader = Thread.currentThread().contextClassLoader ?: PathManager::class.java.classLoader
    val stream = classLoader.getResourceAsStream(resourceName) ?: return false
    ZipInputStream(BufferedInputStream(stream)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val entryPath = targetDir.resolve(entry.name).normalize()
            if (!entryPath.startsWith(targetDir)) {
                throw IllegalStateException("Zip entry outside target dir: ${entry.name}")
            }
            if (entry.isDirectory) {
                Files.createDirectories(entryPath)
            } else {
                Files.createDirectories(entryPath.parent)
                Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    return true
}

private fun copyResourceDirectory(resourceRoot: String, targetDir: Path): Boolean {
    val classLoader = Thread.currentThread().contextClassLoader ?: PathManager::class.java.classLoader
    val resourceUrl = classLoader.getResource(resourceRoot)
        ?: return false
    val uri = resourceUrl.toURI()
    return if (uri.scheme == "jar") {
        val fs = try {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        } catch (_: Exception) {
            FileSystems.getFileSystem(uri)
        }
        fs.use {
            val sourcePath = it.getPath("/$resourceRoot")
            copyRecursively(sourcePath, targetDir)
        }
        true
    } else {
        val sourcePath = Paths.get(uri)
        copyRecursively(sourcePath, targetDir)
        true
    }
}

private fun copyRecursively(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val relative = source.relativize(path).toString()
            val targetPath = target.resolve(relative)
            if (Files.isDirectory(path)) {
                Files.createDirectories(targetPath)
            } else {
                Files.createDirectories(targetPath.parent)
                Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun makeGradleWrapperExecutable(targetDir: Path) {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("win")) return
    val gradlew = targetDir.resolve("gradlew").toFile()
    if (gradlew.exists()) {
        gradlew.setExecutable(true)
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
// Deprecated
//    ${CYAN}target: $GREEN"TARGET"$RESET — target to jvm/linux/macos/windows(not supported yet)
//    ${CYAN}mode: $GREEN"MODE"$RESET     — debug/release only for native targets, use debug for faster compilation
//    Example: ${YEL}Project ${CYAN}target: $GREEN"linux" ${CYAN}mode: $GREEN"debug"$RESET (worked before, not now)

const val HELP = """
Usage:
    ${WHITE}run$RESET      — compile and run project from "main.niva" file
    ${WHITE}FILE$RESET     — compile and run single file
    ${WHITE}run FILE$RESET — compile and run project from FILE entry point

    ${WHITE}build$RESET — compile only(creates jar\binary in current folder)
        to rename binary use ${WHITE}niva --out-name=NAME build$RESET
    ${WHITE}distr$RESET — create easy to share jvm distribution

    ${WHITE}test$RESET      — run all tests
    ${WHITE}test NAME$RESET — run all tests that contains NAME(method)
    ${WHITE}test PKG$RESET  — run all tests from the PKG(filename)

    ${WHITE}dev$RESET — rerun on changed files or input, for faster iteration
    ${WHITE}info$RESET or ${WHITE}i$RESET — get info about packages
    ${WHITE}infoUserOnly$RESET or ${WHITE}iu$RESET — get info about user defined packages
    
    ${WHITE}graphviz$RESET — output graphviz representation of package dependencies
    ${WHITE}graphviz FILE$RESET — from FILE as root of the graph
    ${WHITE}graphviz FILE NUM$RESET — with NUM as max depth of the graph

    ${WHITE}--verbose$RESET — with verbose printing

    ${WHITE}--js$RESET — compile to js and run
    ${WHITE}--js --js-runtime=bun$RESET — use specific js runtime
    ${WHITE}--nativeRelease$RESET — native build with -O3
    ${WHITE}--nativeDebug$RESET — native build with -Ob

In code:
    > EXPR  — debug expr value from IDE
              run program at least once
    >? TYPE — print all info about TYPE

    > 1 inc
    >? Int

    mark method with @debug to debug every expr

Project configuration:
    Messages for ${YEL}Project$RESET:

    ${CYAN}package: $GREEN"PKG"$RESET   — set package for the definitions in code below
    ${CYAN}protocol: $GREEN"NAME"$RESET — set protocol for the definitions in code below
    ${CYAN}use: $GREEN"PKG"$RESET       — set default pkg, like using namespace in C#/Vala


Kotlin\Java interop:
    Messages for ${YEL}Bind$RESET:
    ${CYAN}package: $GREEN"PKG"$RESET  — bind package
    ${CYAN}content: $WHITE[CODE]$RESET — bindings

    Example:
    ${YEL}Bind ${CYAN}package: $GREEN"java.io" ${CYAN}content: $RESET[
        ${RED}type ${YEL}File ${CYAN}pathname: ${YEL}String
        ${YEL}File ${CYAN}exists ${RED}-> ${YEL}Boolean
        ${YEL}File ${CYAN}readText ${RED}-> ${YEL}String$RESET
    ]
    ${WHITE}file = ${YEL}File ${CYAN}pathname: $GREEN"path/to/file"
    ${WHITE}text = ${WHITE}file ${CYAN}readText$RESET

    Messages for ${YEL}Project$RESET:
    ${CYAN}loadPackages: $RESET{$GREEN"PKG1" "PKG2"$RESET} — load package from Maven Central
    ${CYAN}import: $GREEN"PATH_TO_PKG"$RESET — add direct import to generated code

"""



fun isCommandAvailable(command: String): Boolean =
    try {
        val process = ProcessBuilder(command, "--version")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }
