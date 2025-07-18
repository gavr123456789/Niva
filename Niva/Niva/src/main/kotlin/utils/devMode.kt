package main.utils

import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import main.Option
import main.codogen.BuildSystem
import main.languageServer.OnCompletionException
import main.languageServer.Scope
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.text.lowercase
import kotlin.text.startsWith
import kotlin.time.measureTime

fun onCompletionExc(scope: Scope, errorMessage: String? = null, token: Token? = null): Nothing = throw (OnCompletionException(scope, errorMessage, token))



const val DEV_MODE_INSTRUCTIONS = """
c then 2 enters - compile
w - compile on every file change
e - exit
"""

fun daemon(pm: PathManager, mainArg: MainArgument, am: ArgsManager) = runBlocking {
    GlobalVariables.enableDemonMode()

    suspend fun watchFolderAndEmitKt() {
        val scope = this
        var gradleWatcherIsAlreadyRunning = false
        val watcher = KfsDirectoryWatcher(scope, dispatcher = Dispatchers.IO)
        watcher.add(pm.nivaRootFolder)
        println("watching " + pm.nivaRootFolder)
        launch {
            watcher.onEventFlow.collect { event ->
                withContext(Dispatchers.IO) {
                    if (event.path.endsWith(".niva") && event.event == KfsEvent.Modify) {
                        try {
                            runProcess("clear")
                            val resolver = compileProjFromFile(
                                pm,
                                dontRunCodegen = false,
                                compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH,
                                tests = mainArg == MainArgument.TEST,
                                verbose = am.verbose,
                                buildSystem = am.buildSystem
                            )

                            if (!gradleWatcherIsAlreadyRunning) {
                                val compiler = CompilerRunner(
                                    pm.pathToInfroProject,
                                    File("inline_repl.txt").absoluteFile,
                                    resolver.compilationTarget,
                                    resolver.compilationMode,
                                    pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
                                    resolver,
                                    watch = true
                                )
                                launch {
                                    compiler.runGradleAmperBuildCommand()
//                                        println("WONT SEE THIS printAfter runGradleAmperBuildCommand")
                                }
                                gradleWatcherIsAlreadyRunning = true
                            }
                        } catch (e: CompilerError) {
                            println(e.message)
                        } catch (e: OnCompletionException) {
                            println(e.scope)
                        } catch (e: Exception) {
                            if (e.message?.startsWith("end of search") == false) {
                                println("Exception: ${e.message}")
                                throw e
                            }
                        }
                    }
                }
            }
        }
    }
    var watcherIsRunning = false
    val compileWithErrorPrinting = {
        try {
            val resolver = compileProjFromFile(
                pm,
                dontRunCodegen = false,
                compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH,
                tests = mainArg == MainArgument.TEST,
                verbose = am.verbose,
                buildSystem = am.buildSystem
            )

            val compiler = CompilerRunner(
                pm.pathToInfroProject,
                File("inline_repl.txt").absoluteFile,
                resolver.compilationTarget,
                resolver.compilationMode,
                pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
                resolver,
                watch = true
            )
            println("compiled")
            if (!watcherIsRunning) {
                watcherIsRunning = true
                launch {
                    when (am.buildSystem) {
                        BuildSystem.Amper -> compiler.runGradleAmperBuildCommand()
                        BuildSystem.Mill -> compiler.runMill(Option.RUN, am.outputRename)
                        BuildSystem.Gradle -> {TODO("No dev mode for gradle, its old, use mill or amper")}
                    }


                }
            }

        }   catch (e: CompilerError) {
            println(e.message)
        }

    }

    // read console commands
    suspend fun BufferedReader.readLineSuspending() =
        withContext(Dispatchers.IO) { readLine() }


    launch{
        val q = BufferedReader(InputStreamReader(System.`in`))
        var input = ""
        println(DEV_MODE_INSTRUCTIONS)
        while (input != "e") {
            input = q.readLineSuspending()
            runProcess("clear")
            when (input) {
                "c", "" -> {
                    val x = measureTime{
                        compileWithErrorPrinting()
                    }
                    println("done in $x")
                }
                "w" -> {
                    watchFolderAndEmitKt()
                }
                else -> {
                    print("unknown command")
                    println(DEV_MODE_INSTRUCTIONS)
                }
            }
        }
    }

}

fun findSimilarAndPrint(to: String, forType: Type) {
    val (_, b) = findSimilar(to, forType)
    println(b)
}
fun findSimilar(to: String, forType: Type): Pair<List<String>, String> {
    var foundCounter = 1
    val b = StringBuilder()

    val result = mutableListOf<String>()
    fun find(it: MessageMetadata){
        if (it.name.lowercase().startsWith(to)) {
            b.appendLine("$foundCounter\t$it")
            result.add(it.toString())
            foundCounter++
        }
    }

    // search for methods
    fun findRecursive(forType: Type) {
        val p = forType.parent
        forType.protocols.values.forEach { protocol ->
            protocol.unaryMsgs.values.forEach {
                find(it)
            }
            protocol.keywordMsgs.values.forEach {
                find(it)
            }
        }
        if (p != null) {
            findRecursive(p)
        }
    }

    findRecursive(forType)

    // search for fields
    if (forType is Type.UserLike) {
        forType.fields.forEach {
            if (it.name.lowercase().startsWith(to)) {
                b.appendLine("$foundCounter\tfield $it")
                result.add(it.toString())
                foundCounter++
            }
        }
    }
    if (foundCounter == 1) {
        b.appendLine("No results for type $forType starting with $to")
        b.appendLine("Known methods:")
        forType.protocols.values.forEach {
            b.appendLine(it.name)
            b.appendLine("\tunary:")
            b.appendLine("\t\t" + it.unaryMsgs.values.joinToString("\n\t\t"))
            b.appendLine("\tbinary:")
            b.appendLine("\t\t" + it.binaryMsgs.values.joinToString("\n\t\t"))
            b.appendLine("\tkeyword:")
            b.appendLine("\t\t" + it.keywordMsgs.values.joinToString("\n\t\t"))
        }
    }

    return Pair(result, b.toString())
}
