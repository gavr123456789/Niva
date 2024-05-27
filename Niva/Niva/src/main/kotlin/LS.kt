@file:Suppress("unused")

package main

import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.resolve
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.VarDeclaration
import main.utils.GlobalVariables
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.VerbosePrinter
import main.utils.compileProjFromFile
import java.io.File
import java.net.URI
import java.util.SortedMap


typealias Line = Int
typealias Scope = Map<String, Type>

class OnCompletionException(val scope: Scope): Exception()

sealed interface LspResult {
    class NotFoundFile() : LspResult
    class NotFoundLine(val x: Pair<Statement?, Scope>) : LspResult
    class Found(val x: Pair<Statement, Scope>) : LspResult
}


class LS {
    lateinit var resolver: Resolver
    val megaStore: MegaStore = MegaStore()
    var completionFromScope: Scope = mapOf()

    class MegaStore() {
        val data: MutableMap<String, SortedMap<Line, MutableSet<Pair<Statement, Scope>>>> = mutableMapOf()
        fun addNew(s: Statement, scope: Scope) {
            val sFile = s.token.file.absolutePath
            val sLine = s.token.line

            val createSet = {
//                val realScope = if (s is MessageDeclaration && s.isSingleExpression)
                mutableSetOf(Pair(s, scope))
            }

            val createLineToStatement = {
                sortedMapOf<Line, MutableSet<Pair<Statement, Scope>>>(sLine to createSet())
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


        // use scope if there is no expression on line
        fun find(path: String, line: Int, character: Int, scope: Scope): LspResult {

            fun <T> checkElementsFromEnd(set: Set<T>, returnLast: Boolean = true, check: (T, T) -> Boolean): T? {
                val list = set.toList()
                for (i in list.size - 1 downTo 1) {
                    if (check(list[i], list[i - 1])) {
                        if (returnLast)
                            return list[i]
                        else
                            return list[i - 1]
                    }
                }
                return null
            }

            // when we search on empty line, we are looking only for scope or messages for previous line

            val findStatementInLine = { set: MutableSet<Pair<Statement, Scope>>, onlyScope: Boolean ->
                // if its last elem
                val lastStatementOnTheLine = set.last().first
                if (lastStatementOnTheLine.token.relPos.end <= character || onlyScope) {
                    // it is completion for last
                    set.last()
                } else {

                    val q = checkElementsFromEnd(set, true) { next, prev ->
                        val a = next.first.token.relPos.start > character
                        val b = prev.first.token.relPos.start <= character
                        a && b
                    }

                    q ?: throw Exception("Cant find statement on line: $line path: $path, char: $character\n" +
                            "statements are: ${set.joinToString { "start: " + it.first.token.relPos.start + " end: " + it.first.token.relPos.end }}"
                    )

                }

            }

            // file
            val f = data[path]
            return if (f != null) {
                // line
                val l = f[line]
                if (l != null) {
                    val q = findStatementInLine(l, false)

                    LspResult.Found(q)
                } else {
                    // no such line so show scope

                    // run resolve with scope feature
                    LspResult.NotFoundLine(Pair(null, scope))
                    ///

                    // is that new line after last
//                    val lastLine = f.keys.last()
//                    if (line > lastLine) {
//                        LspResult.NotFoundLine(findStatementInLine(f[lastLine]!!, true))
//                    }
//
//                    val lineBeforeRequested = checkElementsFromEnd(f.keys, false) { a, b ->
//                        b <= line && line <= a
//                    }
//                    if (lineBeforeRequested != null) {
//                        val p = LspResult.NotFoundLine(findStatementInLine(f[lineBeforeRequested]!!, true))
//                        p
//                    } else
//                        LspResult.NotFoundLine(Pair(null, mapOf()))

                }
            } else {
                return LspResult.NotFoundFile()
            }
        }
    }
}

// resolve all with lines to statements lists maps (Map(Line, Obj(List::Statements, scope)) )
fun LS.onCompletion(pathToChangedFile: String, line: Int, character: Int): LspResult {
    // We don't need to resolve anything on completion, it happens when code changes
    // find statement type

    val fileAbsolutePath = File(URI(pathToChangedFile)).absolutePath
    val a = megaStore.find(fileAbsolutePath, line + 1, character, completionFromScope) // vsc count lines from 0

    return a
}


fun LS.removeDeclarations(pkgName: String) {
    val pkg = resolver.projects["common"]!!.packages[pkgName]
    if (pkg != null) {
        val iter = resolver.typeDB.userTypes.iterator()

        while (iter.hasNext()) {
            val q = iter.next()
            val typeWithSameNameIter = q.value.iterator()
            while (typeWithSameNameIter.hasNext()) {
                val type = typeWithSameNameIter.next()
                if (type.pkg == pkgName)
                    typeWithSameNameIter.remove()
            }

            if (q.value.isEmpty()) {
                iter.remove()
            }
        }
        pkg.declarations.clear()
        pkg.imports.clear()
        resolver.projects["common"]!!.packages.remove(pkgName)
        resolver.statements = mutableListOf()
    }
}


fun LS.resolveAllWithChangedFile2(pathToChangedFile: String, text: String) {
    val file = File(URI(pathToChangedFile))
    val pkgName = file.nameWithoutExtension
    // let's assume user cant change packages names for now
    // remove everything that was declarated in this changed file
    removeDeclarations(pkgName)
    megaStore.data.remove(file.absolutePath)

    resolver.reset()
    resolver.resolve(file, VerbosePrinter(false), resolveOnlyOneFile = true, customMainSource = text)
}

fun LS.resolveAllWithChangedFile(pathToChangedFile: String, text: String) {
    val file = File(URI(pathToChangedFile))
    val pkgName = file.nameWithoutExtension
    // let's assume user cant change packages names for now
    // remove everything that was declarated in this changed file
    removeDeclarations(pkgName)
    megaStore.data.remove(file.absolutePath)

    resolver.reset()
    try {
        resolver.resolve(file, VerbosePrinter(false), resolveOnlyOneFile = true, customMainSource = text)

    } catch (s: OnCompletionException) {
        this.completionFromScope = s.scope
    } catch (e: Throwable) {

    }
}

fun LS.resolveAll(pathToChangedFile: String): Resolver {

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
        val filesFromTheUpperDir = getNivaFilesInSameDirectory(a)
        listOfNivaFiles.addAll(filesFromTheUpperDir)

        if (filesFromTheUpperDir.count() == 0)
            throw Exception("There is no main.niva file")

        // find if there is main.niva
        val nivaMain = listOfNivaFiles.find { it.nameWithoutExtension == "main" }
        if (nivaMain != null) {
            return Pair(nivaMain, listOfNivaFiles)
        } else {
            return findRoot(a.parentFile, listOfNivaFiles)
        }
    }

    val file = File(URI(pathToChangedFile))
    assert(file.exists())

    val (mainFile, allFiles) = findRoot(file, mutableSetOf())

    GlobalVariables.enableLspMode()

    megaStore.data.clear()


    val onEachStatement = { st: Statement, currentScope: Map<String, Type>?, previousScope: Map<String, Type>? ->
        when (st) {
            is Expression, is VarDeclaration -> {
                megaStore.addNew(
                    st,
                    if (currentScope != null && previousScope != null)
                        currentScope + previousScope
                    else
                        mutableMapOf()
                )
            }
            else -> {}
        }
    }

    // Resolve
    val pm = PathManager(mainFile.path, MainArgument.LSP)
    try {
        this.resolver = compileProjFromFile(pm, compileOnlyOneFile = false, onEachStatement = onEachStatement)
        this.completionFromScope = mapOf()
        return resolver
    } catch (s: OnCompletionException) {

        this.completionFromScope = s.scope
        val emptyResolver = Resolver.empty(otherFilesPaths = allFiles.toList())
        this.resolver = emptyResolver
        return emptyResolver
    }
    catch (_: Throwable) {
        val emptyResolver = Resolver.empty(otherFilesPaths = allFiles.toList())
        this.completionFromScope = mapOf()
        this.resolver = emptyResolver
        return emptyResolver
    }

}
