package utils

import main.languageServer.LS
import main.languageServer.OnCompletionException
import main.languageServer.onCompletion
import main.languageServer.resolveAllFirstTime
import main.languageServer.resolveNonIncremental
import main.languageServer.resolveIncremental
import java.io.File
import java.net.URI

fun testingLS() {
    val args = arrayOf("build","")
        val qqq =
     "file:///~/Documents/Fun/Kotlin/Niva/Niva/NivaInNiva/main.niva"
        try {
            val ls = LS()
            val resolver = ls.resolveAllFirstTime(qqq, true, null)
            val mainFile = File(URI(qqq))
            val mainSource = mainFile.readText()

            val resolver3 =  ls.resolveNonIncremental(qqq, mainSource)
            val resolver4 =  ls.resolveIncremental(qqq, mainSource + "\n123", changeLine = mainSource.split("\n").size)
    //        ls.resolveIncremental(qqq, fakeFileSourceGOOD)
            val q = ls.onCompletion(qqq, line = 9, character = 17)
            println(q)
        }
        catch (e: OnCompletionException) {
            println(e.scope)
        }
}
