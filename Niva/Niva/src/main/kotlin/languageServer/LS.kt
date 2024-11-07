@file:Suppress("unused")

package main.languageServer

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.getAstFromFiles
import frontend.resolver.resolveWithBackTracking
import main.frontend.meta.Token
import main.frontend.meta.removeColors
import main.frontend.parser.types.ast.*
import main.utils.*
import java.io.File
import java.net.URI
import java.util.*

fun Statement.unpackMessage() = if (this is VarDeclaration) {
    val value = this.value
    if (value is MessageSend) {
        value.messages.last()
    } else this
} else this


typealias Line = Int
typealias Scope = Map<String, Type>

class OnCompletionException(val scope: Scope, val errorMessage: String? = null, val token: Token? = null) : Exception()

sealed interface LspResult {
    class NotFoundFile() : LspResult
    class ScopeSuggestion(val scope: Scope) : LspResult
    class Found(val statement: Statement, val needBraceWrap: Boolean) : LspResult
}

fun <T> MutableList<T>.addFirst(element: T) {
    this.add(0, element)
}

//class FoundResult(val statement: Statement, scope: Scope, needBraceWrap: Boolean)

class LS(val info: ((String) -> Unit)? = null) {
    lateinit var resolver: Resolver

    /// file to line to set of statements of that line
    val megaStore: MegaStore = MegaStore(info)
    //
    val nonIncrementalStore = mutableMapOf<String, String>()

    var completionFromScope: Scope = emptyMap()

    // since one file can contain many pkgs, we need file to declaration map
    val fileToDecl: MutableMap<String, MutableSet<Declaration>> = mutableMapOf()

    class MegaStore(val info: ((String) -> Unit)? = null) {
        // file absolute path to line to a pair of statement + scope of it's line
        val data: MutableMap<String, SortedMap<Line, MutableList<Pair<Statement, Scope>>>> = mutableMapOf()


        fun addNew(s: Statement, scope: Scope, prepend: Boolean) {
            val sFile = s.token.file.absolutePath
            val sLine = s.token.line

            val createList = {
                mutableListOf(Pair(s, scope))
            }

            val createLineToStatement = {
                sortedMapOf<Line, MutableList<Pair<Statement, Scope>>>(sLine to createList())
            }


            val file = data[sFile]
            val addToList = { st: Statement, stLine: Int ->
                // has such file
                if (file != null) {
                    val line = file[stLine]
                    // has such line
                    if (line != null) {
                        if (!prepend)
                            line.add(Pair(st, scope))
                        else
                            line.addFirst(Pair(st, scope))

                    } else {
                        val value = createList()
                        file[stLine] = value
                    }
                } else {
                    val value = createLineToStatement()
                    data[sFile] = value
                }
            }

            addToList(s, sLine)

            if (s.token.isMultiline()) {
                val sas = (s.token.line..s.token.lineEnd).drop(1)
                sas.forEach {
                    addToList(s, it)
                }
            }

        }


        // use scope if there is no expression on line
        fun find(path: String, line: Int, character: Int, scope: Scope): LspResult {
            fun <T> checkElementsFromEnd(set: List<T>, returnLast: Boolean = true, check: (T, T) -> Boolean): T? {
                val list = set
                info?.invoke("-------\nfind, list = $list")
                for (i in list.size - 1 downTo 1) {
                    info?.invoke("i = $i")
                    if (check(list[i], list[i - 1])) {
                        return if (returnLast) list[i]
                        else list[i - 1]
                    }
                }
                info?.invoke("-------")
                return null
            }

            // when we search on empty line, we are looking only for scope or messages for previous line
            val findStatementInLine: (MutableList<Pair<Statement, Scope>>) -> LspResult.Found? =
                { list: MutableList<Pair<Statement, Scope>> ->
                    // if its last elem
                    val lastStatementOnTheLine = list.last().first
                    val lastTok = lastStatementOnTheLine.token
                    // After Pipe NewLine Completion
                    if (lastTok.getLastLine() + 1 == line && lastStatementOnTheLine is Message && lastStatementOnTheLine.isPiped) {
                        LspResult.Found(list.last().first, false)
                    } else if (lastTok.relPos.end <= character) {
                        // it is completion for an arg of kw
                        val x = list.last().first
                        if (x.token.isMultiline() && x is KeywordMsg && list.count() > 1) {
                            LspResult.Found(list[list.count() - 2].first, true) // last but one
                        }
                        else {
                            LspResult.Found(list.last().first, false)
                        }
                    } else {

                        val q = checkElementsFromEnd(list, true) { next, prev ->
                            val a = next.first.token.relPos.start > character
                            val b = prev.first.token.relPos.start <= character
                            a && b
                        }

                        if (q == null)
                            null
                        else
                            LspResult.Found(q.first, false)

                        // ?: lastTok.compileError("LSP: Cant find statement on line: $line path: $path, char: $character\n" + "statements are: ${set.joinToString { "start: " + it.first.token.relPos.start + " end: " + it.first.token.relPos.end }}")

                    }

                }

            // file
            val f = data[path]
            return if (f != null) {
                fun getTheLineThroughPipe(): MutableList<Pair<Statement, Scope>>? {
                    val cursor = f[line]
                    if (cursor != null) return cursor

                    // check that last line is not ended with piped msg
                    val lastLineIndex = line - 1
                    val prevLineCursor = f[lastLineIndex]
                    if (prevLineCursor != null && prevLineCursor.isNotEmpty()) {
                        val lastExprOnTheLine = prevLineCursor.last().first
                        val unpackVarDecl = lastExprOnTheLine.unpackMessage()
                        if (unpackVarDecl is Message && unpackVarDecl.isPiped) {
                            return prevLineCursor
                        }
                    }
                    return null
                }
                // line
                val l = getTheLineThroughPipe()
                if (l != null) {
                    findStatementInLine(l) ?: LspResult.ScopeSuggestion(scope)
                } else {
                    // no such line so show scope
                    // run resolve with scope feature
                    LspResult.ScopeSuggestion(scope)
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

fun LS.removeDecl2(file: File) {

    info?.invoke("Current packages: ${resolver.projects["common"]!!.packages.keys}")


    // цель - удалить из typeDB все методы которые содержались в file
    // у нас есть файл ту декларации методов методы fileToDecl
    // находим в нем того который требуется удалять
    val declsOfTheFile = fileToDecl[file.absolutePath]

    val typeDB = resolver.typeDB
    var pkgName: String? = null
    declsOfTheFile?.forEach { d ->

        // remove message
        if (d is MessageDeclaration) {
            val forType = d.forType
            when (d) {
                is MessageDeclarationUnary -> {
                    when (forType) {
                        is Type.UserLike -> {
                            val usrLikeTypes = typeDB.userTypes[forType.name]
                            val w = usrLikeTypes?.find { it.pkg == forType.pkg }
                            val protocolWithMethod = w?.protocols?.values?.find { it.unaryMsgs.contains(d.name) }
                            protocolWithMethod?.unaryMsgs?.remove(d.name)
                        }

                        is Type.InternalType -> {
                            val usrLikeTypes = typeDB.internalTypes[forType.name]
                            val protocolWithMethod =
                                usrLikeTypes?.protocols?.values?.find { it.unaryMsgs.contains(d.name) }
                            protocolWithMethod?.unaryMsgs?.remove(d.name)
                        }

                        is Type.Lambda, is Type.NullableType, is Type.UnresolvedType -> TODO()
                        null -> {}
                    }
                }

                is MessageDeclarationBinary -> {
                    when (forType) {
                        is Type.UserLike -> {
                            val usrLikeTypes = typeDB.userTypes[forType.name]
                            val w = usrLikeTypes?.find { it.pkg == forType.pkg }
                            val protocolWithMethod = w?.protocols?.values?.find { it.binaryMsgs.contains(d.name) }
                            protocolWithMethod?.binaryMsgs?.remove(d.name)
                        }

                        is Type.InternalType -> {
                            val usrLikeTypes = typeDB.internalTypes[forType.name]!!
                            val protocolWithMethod =
                                usrLikeTypes.protocols.values.find { it.binaryMsgs.contains(d.name) }!!
                            protocolWithMethod.binaryMsgs.remove(d.name)
                        }

                        is Type.Lambda, is Type.NullableType, is Type.UnresolvedType -> TODO()
                        null -> {}
                    }
                }

                is MessageDeclarationKeyword -> {
                    when (forType) {
                        is Type.UserLike -> {
                            val usrLikeTypes = typeDB.userTypes[forType.name]
                            if (usrLikeTypes != null) {
                                info?.invoke("usrLikeTypes = $usrLikeTypes, forType.pkg = ${forType.pkg} ")
                                val w = usrLikeTypes.find { it.pkg == forType.pkg }
                                val protocolWithMethod = w?.protocols?.values?.find { it.keywordMsgs.contains(d.name) }
                                protocolWithMethod?.keywordMsgs?.remove(d.name)
                            }
                        }

                        is Type.InternalType -> {
                            typeDB.internalTypes[forType.name]?.let { usrLikeTypes ->
                                usrLikeTypes.protocols.values.find { it.keywordMsgs.contains(d.name) }?.keywordMsgs?.remove(
                                    d.name
                                )
                            }
                        }

                        is Type.Lambda -> {
                            // find where lambda type is and delete it in typedb
                            if (forType.isAlias) {
                                val aliasName = forType.alias!!
                                typeDB.lambdaTypes.remove(aliasName)
                            } else TODO()
                        }

                        is Type.NullableType, is Type.UnresolvedType -> TODO()

                        null -> {}
                    }
                }

                is ConstructorDeclaration -> {
                    when (forType) {
                        is Type.UserLike -> {
                            typeDB.userTypes[forType.name]?.let { usrLikeTypes ->
                                usrLikeTypes.find { it.pkg == forType.pkg }?.let { w ->
                                    val protocolWithMethod = w.protocols.values.find { it.staticMsgs.contains(d.name) }
                                    protocolWithMethod?.staticMsgs?.remove(d.name)
                                }
                            }
                        }

                        is Type.InternalType -> {
                            typeDB.internalTypes[forType.name]?.let { usrLikeType ->
                                usrLikeType.protocols.values.find { it.staticMsgs.contains(d.name) }?.staticMsgs?.remove(
                                    d.name
                                )
                            }
                        }

                        is Type.Lambda -> {
                            val alias = forType.alias
                            if (alias != null) {
                                typeDB.lambdaTypes[alias]?.let { lambdaType ->
                                    lambdaType.protocols.values.find { it.staticMsgs.contains(d.name) }?.staticMsgs?.remove(
                                        d.name
                                    )
                                }
                            }
                        }

                        is Type.NullableType, is Type.UnresolvedType -> TODO()
                        null -> {}
                    }
                }

                else -> {
                    // something else
                }
            }
        }

        // fill pkgName, when only builder in package -> pkg it not being deleted
        if (d is StaticBuilderDeclaration) {
            pkgName = d.messageData!!.pkg
        }

        // remove type
        if (d is SomeTypeDeclaration) {
            pkgName = d.receiver!!.pkg
            val removeFromTypeDB = { typeName: String ->
                val t = typeDB.userTypes[typeName]
                if (t != null) {
                    val iter = t.iterator()
                    while (iter.hasNext()) {
                        val c = iter.next()
                        if (c.pkg == pkgName) {
//                            info?.invoke("removing type typeDB.userTypes $typeName")
                            iter.remove()
                        }
                    }
                    if (t.isEmpty()) {
                        typeDB.userTypes.remove(typeName)
                    }
                } else {
                    // try lambda
                    val l = typeDB.lambdaTypes[typeName]
                    if (l != null) {
                        typeDB.lambdaTypes.remove(typeName)
                    }
                }


                // from pkg
                val pkg2 = resolver.projects[resolver.currentProjectName]!!.packages[pkgName]
//                info?.invoke("removing ${d.typeName} from $pkg2 from ${pkg2?.types}")
                pkg2?.types?.remove(d.typeName)
            }


            when (d) {
                is TypeDeclaration, is TypeAliasDeclaration, is UnionBranchDeclaration, is EnumBranch, is ErrorDomainDeclaration -> removeFromTypeDB(
                    d.typeName
                )

                is UnionRootDeclaration -> {
                    d.branches.forEach { removeFromTypeDB(it.typeName) }
                    removeFromTypeDB(d.typeName)
                }

                is EnumDeclarationRoot -> {
                    d.branches.forEach { removeFromTypeDB(it.typeName) }
                    removeFromTypeDB(d.typeName)
                }
            }

        }
    }
    // remove the whole package
    if (pkgName != null && pkgName != "core") {
        resolver.projects[resolver.currentProjectName]!!.packages.remove(pkgName)
        info?.invoke("The whole package removed: $pkgName")
    }

    fileToDecl.remove(file.absolutePath)
}




fun LS.resolveIncremental(pathToChangedFile: String, text: String) {
    val file = File(URI(pathToChangedFile))

    // let's assume user cant change packages names for now, so pkg name always == filename
    // remove everything that was declarated in this changed file

    removeDecl2(file)
    megaStore.data.remove(file.absolutePath)
    resolver.reset()

    val (mainAst, otherAst) = getAstFromFiles(
        mainFileContent = text,
        otherFileContents = resolver.otherFilesPaths,
        mainFilePath = file.absolutePath,
        resolveOnlyOneFile = true
    )

    // throws on
    resolver.resolveWithBackTracking(
        mainAst,
        otherAst,
        file.absolutePath,
        file.nameWithoutExtension,
        VerbosePrinter(false),
    )
}

// first time, all files reading
// if non-incremental, then we will fill the nonIncrementalStore store
fun LS.resolveAll(pathToChangedFile: String, incremental: Boolean = true): Resolver {

    fun getNivaFilesInSameDirectory(file: File): Set<File> {
        val directory = file.parentFile
        return if (directory.isDirectory) {
            val q = directory.listFiles()
            if (q != null) {
                q.asSequence().filter { it.extension == "niva" }.toSet() // || it.extension == "scala"
            } else TODO("Cant find files in the $directory")
        } else {
            emptySet()
        }
    }

    // returns path to main.niva and set of all files
    // Doesn't search inside folders, only goes outside
    fun findMainUpRecursively(a: File, listOfNivaFiles: MutableSet<File>): Pair<File, MutableSet<File>> {
        val filesFromTheUpperDir = getNivaFilesInSameDirectory(a)
        listOfNivaFiles.addAll(filesFromTheUpperDir)

        if (filesFromTheUpperDir.count() == 0) throw Exception("There is no main.niva file")

        // find if there is main.niva
        val nivaMain = listOfNivaFiles.find { it.nameWithoutExtension == "main" }
        return if (nivaMain != null) {
            Pair(nivaMain, listOfNivaFiles)
        } else {
            findMainUpRecursively(a.parentFile, listOfNivaFiles)
        }
    }

    val file = File(URI(pathToChangedFile))
    assert(file.exists())

    val collectFiles = {
        // get main file
        val pair = findMainUpRecursively(file, mutableSetOf())
        // listFilesDownUntilNivaIsFoundRecursively from main to get all files
        val set = listFilesDownUntilNivaIsFoundRecursively(pair.first.parentFile, "niva", "sas", "nivas")
        pair.also { it.second.addAll(set) }
    }
    val (mainFile, allFiles) = collectFiles()
    info?.invoke("main file is $mainFile ") //all files is ${allFiles.joinToString(", ") { it.name }}
    GlobalVariables.enableLspMode()

    megaStore.data.clear()


    val onEachStatementCall =
        { st: Statement, currentScope: Map<String, Type>?, previousScope: Map<String, Type>?, file2: File ->
            fun addStToMegaStore(s: Statement, prepend: Boolean = false) {
                megaStore.addNew(
                    s = s,
                    scope =
                        if (currentScope != null && previousScope != null)
                            currentScope + previousScope
                        else
                            mutableMapOf(),
                    prepend
                )
            }

            when (st) {
                is Expression, is VarDeclaration -> {
                    addStToMegaStore(st)
                }

                is DestructingAssign -> {
                    st.names.forEach {
                        addStToMegaStore(it)
                    }
                    addStToMegaStore(st.value)
                }

                is Declaration -> {
                    // fill fileToDecl
                    val setOfStatements = this.fileToDecl[file2.absolutePath]
                    if (setOfStatements != null) {
                        setOfStatements.add(st)
                    } else {
                        fileToDecl[file2.absolutePath] = mutableSetOf(st)
                    }
                    // add doc comments so u can ctrl click them
                    st.docComment?.let {
                        it.identifiers?.forEach { addStToMegaStore(it) }
                    }
                    // add types of the decl as IdentExpr
                    if (st is MessageDeclaration && st.returnType != null) {
                        val realSt = when (st) {
                            is ConstructorDeclaration -> st.msgDeclaration
                            else -> st
                        }

                        // forType
                        realSt.forType?.let {
                            realSt.forTypeAst.toIdentifierExpr(it, true).also {
                                // we prepend here because
                                // `Sas kek = this | match deep |`
                                // Sas is the last expression on the line, so it replace the whole line instead of
                                // only `this`
                                addStToMegaStore(it, prepend = true)
                            }
                        }

                        val returnType = st.returnType
                        if (returnType != null) {
                            realSt.returnTypeAST?.toIdentifierExpr(returnType, true)?.also {
                                addStToMegaStore(it, prepend = true)
                            }
                        }
                        // args
                        if (realSt is MessageDeclarationKeyword) {
                            realSt.args.forEachIndexed { i, arg ->
                                // for some reason arg types are null here, so I use typeDB
                                val type =
                                    ((realSt.messageData ?: st.messageData) as? KeywordMsgMetaData)?.argTypes[i]?.type
                                if (type != null) arg.typeAST?.toIdentifierExpr(type, true)?.also {
                                    addStToMegaStore(it)
                                }
                            }
                        }
                    }
                    // we dont need the msg declarations itself in mega store
//                    addStToMegaStore(st)
                }


                else -> {}
            }
        }

    // Resolve
    val pm = PathManager(mainFile.path, MainArgument.LSP)
    try {
        this.resolver = compileProjFromFile(
            pm, resolveOnly = true, compileOnlyOneFile = false, onEachStatement = onEachStatementCall
        )
        // not sure why reset this?
        this.completionFromScope = emptyMap()
        return resolver
    } catch (s: OnCompletionException) {
        val emptyResolver =
            Resolver.empty(otherFilesPaths = allFiles.toList(), onEachStatementCall, currentFile = mainFile)
        this.resolver = emptyResolver
        this.completionFromScope = s.scope

        info?.invoke("NOT RESOLVED OnCompletionException, error.scope = ${s.scope}, completionFromScope = $completionFromScope , error message = ${s.errorMessage?.removeColors()}")
        return resolver
    }

}