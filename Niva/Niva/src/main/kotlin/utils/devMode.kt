package main.utils

import frontend.resolver.MessageMetadata
import frontend.resolver.Type
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import main.languageServer.OnCompletionException
import main.languageServer.Scope
import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.text.lowercase
import kotlin.text.startsWith

fun onCompletionExc(scope: Scope, errorMessage: String? = null, token: Token? = null): Nothing = throw (OnCompletionException(scope, errorMessage, token))

fun daemon(pm: PathManager, mainArg: MainArgument) = runBlocking {
    GlobalVariables.enableDemonMode()


    // read console commands
    suspend fun BufferedReader.readLineSuspending() =
        withContext(Dispatchers.IO) { readLine() }

    launch{
        val q = BufferedReader(InputStreamReader(System.`in`))
        var w = ""
        while (w != "e") {
            w = q.readLineSuspending()
            if (w == "c") {
                println("compiling")
            } else {
                println("unknown command")
            }
        }
    }

    val scope = this
    val watcher = KfsDirectoryWatcher(scope, dispatcher = Dispatchers.IO)
    watcher.add(pm.nivaRootFolder)
    println("watching " + pm.nivaRootFolder)
    var everySecond = false
    launch {
        var lock = true
        watcher.onEventFlow.collect { event ->
            withContext(Dispatchers.IO) {
                if (event.path.endsWith(".niva") && event.event == KfsEvent.Modify ) {
                    if (lock && everySecond) {
                        lock = false
                        runProcess("clear")
                        try {
                            compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH)
                        } catch (e: CompilerError) {
                            println(e.message)
                        } catch (e: OnCompletionException){
                            println(e.scope)
//                            throw e
                        } catch (e: Exception) {
                            if (e.message?.startsWith("end of search") == false) {
                                println("Exception: ${e.message}")

                                throw e
                            }
                        }
                        lock = true
                    }
                    everySecond = !everySecond //!localEverySecond
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
