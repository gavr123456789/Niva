@file:Suppress("unused")

package main

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.resolve
import main.frontend.meta.Token
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.EnumBranch
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.ErrorDomainDeclaration
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationBinary
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.TypeAliasDeclaration
import main.frontend.parser.types.ast.TypeDeclaration
import main.frontend.parser.types.ast.UnionBranchDeclaration
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.parser.types.ast.VarDeclaration
import main.utils.GlobalVariables
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.VerbosePrinter
import main.utils.compileProjFromFile
import main.utils.listFilesRecursively
import java.io.File
import java.net.URI
import java.util.SortedMap


typealias Line = Int
typealias Scope = Map<String, Type>

class OnCompletionException(val scope: Scope, val errorMessage: String? = null, val token: Token? = null) : Exception()

sealed interface LspResult {
    class NotFoundFile() : LspResult
    class ScopeSuggestion(val scope: Scope) : LspResult
    class Found(val x: Pair<Statement, Scope>) : LspResult
}


class LS(val info: ((String) -> Unit)? = null) {
    lateinit var resolver: Resolver

    /// file to line to set of statements of that line
    val megaStore: MegaStore = MegaStore(info)
    var completionFromScope: Scope = emptyMap()

    // since one file can contain many pkgs, we need file to declaration map
    val fileToDecl: MutableMap<String, MutableSet<Declaration>> = mutableMapOf()

    class MegaStore(val info: ((String) -> Unit)? = null) {
        // file absolute path to line to a pair of statement + scope of it's line
        val data: MutableMap<String, SortedMap<Line, MutableSet<Pair<Statement, Scope>>>> = mutableMapOf()


        fun addNew(s: Statement, scope: Scope) {
            val sFile = s.token.file.absolutePath
            val sLine = s.token.line

            val createSet = {
                mutableSetOf(Pair(s, scope))
            }

            val createLineToStatement = {
                sortedMapOf<Line, MutableSet<Pair<Statement, Scope>>>(sLine to createSet())
            }


            val file = data[sFile]
            val addToSet = { s: Statement, sLine: Int ->
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

            addToSet(s, sLine)

            if (s.token.isMultiline()) {
                val sas = (s.token.line..s.token.lineEnd).drop(1)
                sas.forEach {
                    addToSet(s, it)
                }
            }

        }


        // use scope if there is no expression on line
        fun find(path: String, line: Int, character: Int, scope: Scope): LspResult {
            fun <T> checkElementsFromEnd(set: Set<T>, returnLast: Boolean = true, check: (T, T) -> Boolean): T? {
                val list = set.toList()
                info?.invoke("-------\nfind, list = $list")
                for (i in list.size - 1 downTo 1) {
                    info?.invoke("i = $i")
                    if (check(list[i], list[i - 1])) {
                        if (returnLast)
                            return list[i]
                        else
                            return list[i - 1]
                    }
                }
                info?.invoke("-------")
                return null
            }

            // when we search on empty line, we are looking only for scope or messages for previous line

            val findStatementInLine: (MutableSet<Pair<Statement, Scope>>, Boolean) -> Pair<Statement, Scope>? =
                { set: MutableSet<Pair<Statement, Scope>>, onlyScope: Boolean ->
                    // if its last elem
                    val lastStatementOnTheLine = set.last().first
                    val lastTok = lastStatementOnTheLine.token
                    if (lastTok.relPos.end <= character || onlyScope) {
                        // it is completion for last
                        set.last()
                    } else {

                        val q = checkElementsFromEnd(set, true) { next, prev ->
                            val a = next.first.token.relPos.start > character
                            val b = prev.first.token.relPos.start <= character
                            a && b
                        }

                        q
                            // ?: lastTok.compileError("LSP: Cant find statement on line: $line path: $path, char: $character\n" + "statements are: ${set.joinToString { "start: " + it.first.token.relPos.start + " end: " + it.first.token.relPos.end }}")

                    }

                }

            // file
            val f = data[path]
            return if (f != null) {
                // line
                val l = f[line]
                if (l != null) {
                    val q = findStatementInLine(l, false)

                    if (q != null)
                        LspResult.Found(q)
                    else
                        LspResult.ScopeSuggestion(scope)



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
//    info?.invoke("fileToDecl = ${fileToDecl.map { it.key + it.value + "\n" }}")
//    info?.invoke("declsOfTheFile = $declsOfTheFile")

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
                                val protocolWithMethod =
                                    w?.protocols?.values?.find { it.keywordMsgs.contains(d.name) }
                                protocolWithMethod?.keywordMsgs?.remove(d.name)
                            }
                        }

                        is Type.InternalType -> {
                            typeDB.internalTypes[forType.name]?.let { usrLikeTypes ->
                                usrLikeTypes.protocols.values.find { it.keywordMsgs.contains(d.name) }
                                    ?.let { protocolWithMethod ->
                                        protocolWithMethod.keywordMsgs.remove(d.name)
                                    }
                            }
                        }

                        is Type.Lambda, is Type.NullableType, is Type.UnresolvedType -> TODO()
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
                            typeDB.internalTypes[forType.name]?.let { usrLikeTypes ->
                                usrLikeTypes.protocols.values.find { it.staticMsgs.contains(d.name) }?.let { prot ->
                                    prot.staticMsgs.remove(d.name)
                                }
                            }
                        }

                        is Type.Lambda, is Type.NullableType, is Type.UnresolvedType -> TODO()
                        null -> {}
                    }
                }

                else -> {
                    // something else
                }
            }
        }
        // remove type
        if (d is SomeTypeDeclaration) {
            pkgName = d.receiver!!.pkg
            val removeFromTypeDB = { typeName: String ->
                val t = typeDB.userTypes[typeName] //?: typeDB.lambdaTypes[typeName]
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
                val pkg2 = resolver.projects["common"]!!.packages[pkgName]
//                info?.invoke("removing ${d.typeName} from $pkg2 from ${pkg2?.types}")
                pkg2?.types?.remove(d.typeName)
            }


            when (d) {
                is TypeDeclaration, is UnionBranchDeclaration, is TypeAliasDeclaration, is EnumBranch, is ErrorDomainDeclaration ->
                    removeFromTypeDB(d.typeName)

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


fun LS.resolveAllWithChangedFile(pathToChangedFile: String, text: String) {
    val file = File(URI(pathToChangedFile))

    // let's assume user cant change packages names for now, so pkg name always == filename
    // remove everything that was declarated in this changed file

    removeDecl2(file)
    megaStore.data.remove(file.absolutePath)
    resolver.reset()

    // throws on
    resolver.resolve(file, VerbosePrinter(false), resolveOnlyOneFile = true, customMainSource = text)


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
    // Doesn't search inside folders, only goes outside
    fun findRoot(a: File, listOfNivaFiles: MutableSet<File>): Pair<File, MutableSet<File>> {
        val filesFromTheUpperDir = getNivaFilesInSameDirectory(a)
        listOfNivaFiles.addAll(filesFromTheUpperDir)

        if (filesFromTheUpperDir.count() == 0)
            throw Exception("There is no main.niva file")

        // find if there is main.niva
        val nivaMain = listOfNivaFiles.find { it.nameWithoutExtension == "main" }
        return if (nivaMain != null) {
            Pair(nivaMain, listOfNivaFiles)
        } else {
            findRoot(a.parentFile, listOfNivaFiles)
        }
    }

    val file = File(URI(pathToChangedFile))
    assert(file.exists())

    val collectFiles = {
        val set = listFilesRecursively(file.parentFile, "niva", "scala", "nivas").toSet()
        val main = set.find { it.nameWithoutExtension == "main" }
        if (main != null) {
            Pair(main, set)
        } else {
            val pair = findRoot(file, mutableSetOf())
            pair.also { it.second.addAll(set) }
        }
    }
    val (mainFile, allFiles) = collectFiles()//findRoot(file, mutableSetOf())
    info?.invoke("all files is ${allFiles.joinToString(", ") {it.name}}" )
    GlobalVariables.enableLspMode()

    megaStore.data.clear()


    val onEachStatementCall =
        { st: Statement, currentScope: Map<String, Type>?, previousScope: Map<String, Type>?, file: File ->

            val addStToMegaStore = { st: Statement ->
                megaStore.addNew(
                    st,
                    if (currentScope != null && previousScope != null)
                        currentScope + previousScope
                    else
                        mutableMapOf()
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
                    val setOfStatements = this.fileToDecl[file.absolutePath]
                    if (setOfStatements != null) {
                        setOfStatements.add(st)
                        Unit
                    } else {
                        fileToDecl[file.absolutePath] = mutableSetOf(st)
                    }
                    // add doc comments so u can ctrl click them
                    st.docComment?.let {
                        it.identifiers.forEach { addStToMegaStore(it) }
                    }
                    // add types of the decl as IdentExpr
                    if (st is MessageDeclaration) {
                        val realSt = when(st) {
                            is ConstructorDeclaration -> st.msgDeclaration
                            else -> st
                        }

                        // forType
                        realSt.forType?.let {
                            realSt.forTypeAst.toIdentifierExpr(it, true).also {
                                addStToMegaStore(it)
                            }
                        }

                        // return
                        realSt.returnTypeAST
                            ?.toIdentifierExpr(realSt.returnType!!, true)
                            ?.also {
                                addStToMegaStore(it)
                            }
                        // args
                        if (realSt is MessageDeclarationKeyword) {
                            realSt.args.forEachIndexed { i, arg ->
                                // for some reason arg types are null here, so I use typeDB
                                val type = ((realSt.messageData ?: st.messageData) as KeywordMsgMetaData).argTypes[i].type
                                arg.typeAST
                                    ?.toIdentifierExpr(type, true)
                                    ?.also {
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
        this.resolver = compileProjFromFile(pm, compileOnlyOneFile = false, onEachStatement = onEachStatementCall)
        this.completionFromScope = emptyMap()
        return resolver
    }
    catch (s: OnCompletionException) {
//        this.completionFromScope = s.scope
        val emptyResolver =
            Resolver.empty(otherFilesPaths = allFiles.toList(), onEachStatementCall, currentFile = mainFile)
        this.resolver = emptyResolver
        this.completionFromScope = s.scope

        info?.invoke("NOT RESOLVED OnCompletionException, error.scope = ${s.scope}, completionFromScope = $completionFromScope")
//        return emptyResolver
//        this.resolver = compileProjFromFile(pm, compileOnlyOneFile = false, onEachStatement = onEachStatementCall)
        return resolver
    }

//    catch (e: Throwable) {
//        val emptyResolver =
//            Resolver.empty(otherFilesPaths = allFiles.toList(), onEachStatementCall, currentFile = mainFile)
//        this.completionFromScope = mapOf()
//        this.resolver = emptyResolver
//        info?.invoke("NOT RESOLVED, $e")
////        if (info != null) {
////            info("3 resolveAll Throwable, megaStore.data = ${megaStore.data.keys}, e = ${e.message}")
////        }
//        return emptyResolver
//    }

}
