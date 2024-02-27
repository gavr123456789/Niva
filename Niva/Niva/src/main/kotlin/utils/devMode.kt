package main.utils

import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import main.frontend.meta.CompilerError

fun endOfSearch(): Nothing = throw (Exception("end of search"))

fun daemon(pm: PathManager, mainArg: MainArgument) = runBlocking {
    GlobalVariables.enableDemonMode()

    val scope = this
    val watcher: KfsDirectoryWatcher = KfsDirectoryWatcher(scope, dispatcher = Dispatchers.IO)
    watcher.add(pm.nivaRootFolder)
    println(pm.nivaRootFolder)
    var everySecond = true
    launch {
        watcher.onEventFlow.collect { event ->
            withContext(Dispatchers.IO) {
                if (event.path.endsWith(".niva") && event.event == KfsEvent.Modify && everySecond) {

                    runProcess("clear")

                    try {
                        compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH)
                    } catch (e: CompilerError) {
                        println(e.message)
                    } catch (e: Exception) {
                        if (e.message?.startsWith("end") == false) {
                            throw e
                        }
                    }

                } else {
                    // each even event, because change generates 2 events in a row
                    everySecond = !everySecond
                }
            }
        }
    }
}
