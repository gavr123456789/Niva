package main

import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.Statement
import main.utils.GlobalVariables
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.compileProjFromFile
import java.io.File
import java.net.URI


typealias Line = Int
typealias Scope = Map<String, Type>


sealed interface LspResult {
    class NotFoundFile() : LspResult
    class NotFoundLine() : LspResult
    class Found(val x: Pair<Statement, Scope>) : LspResult
}


class LS {
    lateinit var resolver: Resolver
    val megaStore: MegaStore = MegaStore()

    class MegaStore() {
        val data: MutableMap<String, MutableMap<Line, MutableSet<Pair<Statement, Scope>>>> = mutableMapOf()
        fun addNew(s: Statement, scope: Scope) {
            val sFile = s.token.file.absolutePath
            val sLine = s.token.line

            val createSet = {
                mutableSetOf(Pair(s, scope))
            }

            val createLineToStatement = {
                mutableMapOf<Line, MutableSet<Pair<Statement, Scope>>>(sLine to createSet())
            }


            val file = data[sFile]
            // has such file
            if (file != null) {
                val line = file[sLine]
                // has such line
                if (line != null) {
                    line.add(Pair(s, scope))
                } else {
                    val value = createSet()
                    file[sLine] = value
                }
            } else {
                val value = createLineToStatement()
                data[sFile] = value
            }

        }


        fun find(path: String, line: Int, character: Int): LspResult {

            fun <T> checkElementsFromEnd(set: Set<T>, check: (T, T) -> Boolean): T? {
                val list = set.toList()
                for (i in list.size - 1 downTo 1) {
                    if (check(list[i], list[i - 1])) {
                        return list[i]
                    }
                }
                return null
            }

            val findStatementInLine = { set: MutableSet<Pair<Statement, Scope>> ->
                // if its last elem
                val lastStatementOnTheLine = set.last().first
                if (lastStatementOnTheLine.token.relPos.end < character) {
                    // it is completion for last
                    set.last()
                } else {

                    val q = checkElementsFromEnd(set) { next, prev ->
                        val first = next.first.token.relPos.start >= character
                        val second = prev.first.token.relPos.start < character
                        first && second
                    }

                    q ?: throw Exception("Cant find stаtement in file $path, line: $line char: $character")

                }

            }

            // file
            val f = data[path]
            if (f != null) {
                // line
                val l = f[line]
                if (l != null) {
                    val q = findStatementInLine(l)

                    return LspResult.Found(q)
                } else {
                    return LspResult.NotFoundLine()
                }
            } else {
                return LspResult.NotFoundFile()
            }
        }
    }
}


//fun LS.lspServerInit(pathToChangedFileUri: String): TypeDB? {
//
//    val resolver = resolveAll(pathToChangedFileUri)
//
//    if (resolver != null) {
//        this.resolver = resolver
//        return resolver.typeDB
//    }
//    return null
//}

// resolve all with lines to statements lists maps (Map(Line, Obj(List::Statements, scope)) )
fun LS.onCompletion(pathToChangedFile: String, line: Int, character: Int): LspResult {
    // We don't need to resolve anything on completion, it happens when code changes
//    resolveAll(pathToChangedFile)
    // find statement type
    val fileAbsolutePath = File(URI(pathToChangedFile)).absolutePath
    val a = megaStore.find(fileAbsolutePath, line + 1, character) // vsc count lines from 0
//    println(a)
    return a
}

fun LS.resolveAll(pathToChangedFile: String): Resolver? {

    fun getNivaFilesInSameDirectory(file: File): Set<File> {
        val directory = file.parentFile
        if (directory.isDirectory) {
            val q = directory.listFiles()
            return q.asSequence().filter { it.extension == "niva" || it.extension == "scala" }.toSet()
        } else {
            return emptySet()
        }
    }

    // returns path to main.niva and set of all files
    fun findRoot(a: File, listOfNivaFiles: MutableSet<File>): Pair<File, MutableSet<File>> {
        listOfNivaFiles.addAll(getNivaFilesInSameDirectory(a))

        if (listOfNivaFiles.count() == 0) throw Exception("There is no main.niva file")

        // find if there is main.niva
        val nivaMain = listOfNivaFiles.find { it.nameWithoutExtension == "main" }
        if (nivaMain != null) {
            return Pair(nivaMain, listOfNivaFiles)
        } else {
            return findRoot(a, listOfNivaFiles)
        }
    }

    val file = File(URI(pathToChangedFile))
    assert(file.exists())

    val (mainFile, allFiles) = findRoot(file, mutableSetOf())

    GlobalVariables.enableLspMode()

    val onEachStatement = { st: Statement, currentScope: Map<String, Type>?, prScope: Map<String, Type>? ->
        if (st is Expression) {
//            val printExpression = {
//                val scope = { cs: Map<String, Type>? ->
//                    if (cs != null) {
//                        cs.map { it.key + ": " + it.value }
//
//                    } else ""
//                }
//                val cur = scope(currentScope)
//                val prev = scope(prScope)
//
//                println("${st.token.file.name} line: ${st.token.line} start: ${st.token.relPos.start} cur: $cur prev: $prev type: ${st.type}")
//            }


            megaStore.addNew(
                st, if (currentScope != null && prScope != null) currentScope + prScope else mutableMapOf()
            )
        }
    }

    // Resolve
    val pm = PathManager(mainFile.path, MainArgument.LSP)
    try {
        this.resolver = compileProjFromFile(pm, compileOnlyOneFile = false, onEachStatement = onEachStatement)
        return resolver
    } catch (_: Throwable) {
        return null
    }

}
