package main.utils

import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import main.frontend.meta.CompilerError


fun daemon(pm: PathManager, mainArg: MainArgument) = runBlocking {
    // compile one time for errors
//    compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH)

    val scope = this
    val watcher: KfsDirectoryWatcher = KfsDirectoryWatcher(scope, dispatcher = Dispatchers.IO)
    watcher.add(pm.nivaRootFolder)
    println(pm.nivaRootFolder)
    var everySecond = true
    launch {
        watcher.onEventFlow.collect { event ->
            // For example: JVM File implementation
            withContext(Dispatchers.IO) {

                if (event.path.endsWith(".niva") && event.event == KfsEvent.Modify) {

                    if (everySecond) {
//                        println(event)
                        ProcessBuilder().command("clear")
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start().waitFor()
                        try {
                            compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH)
                        } catch (e: CompilerError) {
                            println(e.message)
                        } catch (e: Exception) {
                            if (e.message?.startsWith("end") == false) {
                                throw e
                            }
                        }
                    }
                    // each second
                    everySecond = !everySecond
                }
            }
        }
    }
}
