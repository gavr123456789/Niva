@file:Suppress("unused")

package main.languageServer

import frontend.resolver.*
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
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
    var pm: PathManager? = null

    //
    val nonIncrementalStore = mutableMapOf<String, List<Statement>>() // URI from LSP to AST

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
                        } else {
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

    val (mainAst) = getAstFromFiles(
        mainFileContent = text,
        otherFileContents = resolver.otherFilesPaths,
        mainFilePath = file.absolutePath,
        resolveOnlyOneFile = true
    )

    // throws on
    resolver.resolveWithBackTracking(
        mainAst,
        emptyList(),
        file.absolutePath,
        file.nameWithoutExtension,
        VerbosePrinter(false),
    )
}


fun getMainAstFromNIS(nonIncrementalStore: Map<String, List<Statement>>, mainUri: String): Pair<List<Statement>, List<Pair<String, List<Statement>>>> {
    val listOfStatements = mutableListOf<Pair<String, List<Statement>>>()
    var mainAst: List<Statement>? = null
    val mainUrlStr = File(mainUri).absolutePath//.toURI().toString()

    nonIncrementalStore.forEach { absolutePath, ast ->
        if (mainAst == null && absolutePath == mainUrlStr)
            mainAst = ast
        else {
            val pkgName = File(absolutePath).nameWithoutExtension
            listOfStatements.add(Pair(pkgName, ast))
        }

    }
    if (mainAst == null)
        createFakeToken().compileError("Bug: Can't find main in nonIncrementalStore ${nonIncrementalStore.keys}, main is $mainUrlStr")

    return Pair(mainAst, listOfStatements)
}


fun Statement.clearFromType() {
    when (this) {
        is VarDeclaration -> {
            this.value.clearFromType()
        }
        is Assign -> this.value.clearFromType()

        is ExtendDeclaration -> {
            this.messageDeclarations.forEach { t ->
                t.clearFromType()
            }
        }
        is ManyConstructorDecl -> {
            this.messageDeclarations.forEach { t ->
                t.clearFromType()
            }
        }
        is ConstructorDeclaration -> {
            this.forType = null
            this.returnType = null
            this.messageData = null
            this.stackOfPossibleErrors.clear()
            this.body.forEach { it.clearFromType() }
            this.msgDeclaration.clearFromType()
        }
        is MessageDeclaration -> {
            this.forType = null
            this.returnType = null
            this.messageData = null
            this.stackOfPossibleErrors.clear()
            this.body.forEach { it.clearFromType() }
        }
        is EnumBranch -> {
            this.receiver = null
        }
        is EnumDeclarationRoot -> {
            this.receiver = null
            this.branches.forEach { it.clearFromType() }
        }
        is ErrorDomainDeclaration -> {
            this.unionDeclaration.clearFromType()
            this.receiver = null
        }

        is TypeAliasDeclaration -> {
            this.receiver = null
            this.realType = null
        }
        is TypeDeclaration -> {
            this.receiver = null
        }
        is UnionBranchDeclaration -> {
            this.receiver = null
        }
        is UnionRootDeclaration -> {
            this.receiver = null
            this.branches.forEach { it.clearFromType() }
        }

        is DestructingAssign -> {
            this.value.clearFromType()
        }
        is ControlFlow.If -> {
            this.ifBranches.forEach {
                it.ifExpression.clearFromType()
                it.otherIfExpressions.forEach { it.clearFromType() }
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        it.thenDoExpression.clearFromType()
                    }
                    is IfBranch.IfBranchWithBody -> {
                        it.body.clearFromType()
                    }
                }
            }
            this.elseBranch?.forEach { it.clearFromType() }
            this.type = null
        }
        is ControlFlow.Switch -> {
            this.ifBranches.forEach {
                it.ifExpression.clearFromType()
                it.otherIfExpressions.forEach { it.clearFromType() }
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        it.thenDoExpression.clearFromType()
                    }
                    is IfBranch.IfBranchWithBody -> {
                        it.body.clearFromType()
                    }
                }
            }
            this.elseBranch?.forEach { it.clearFromType() }

            this.type = null
            this.switch.clearFromType()
        }
        is CodeBlock -> {
            this.type = null
            this.inputList.forEach { it.clearFromType() }
            this.statements.forEach { it.clearFromType() }
        }
        is CollectionAst -> {
            this.initElements.forEach { t -> t.clearFromType() }
            this.type = null
        }
        is DotReceiver -> {
            this.type = null
        }
        is ExpressionInBrackets -> {
            this.type = null
            this.expr.clearFromType()
        }
        is MapCollection -> {
            this.type = null
            this.initElements.forEach { it.first.clearFromType(); it.second.clearFromType() }
        }
        is BinaryMsg -> {
            this.receiver.clearFromType()
            this.type = null
            this.argument.clearFromType()
            this.unaryMsgsForArg.forEach { it.clearFromType() }
            this.unaryMsgsForReceiver.forEach { it.clearFromType() }

            this.msgMetaData = null
            this.declaration = null

        }
        is KeywordMsg -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
            this.args.forEach { it.keywordArg.clearFromType() }
        }
        is StaticBuilder -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
        }
        is UnaryMsg -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
        }
        is MessageSend -> {
            this.type = null
            this.receiver.clearFromType()
            this.messages.forEach { it.clearFromType() }
        }
        is MethodReference -> {
            this.type = null
            this.method = null
        }
        is IdentifierExpr -> this.type = null
        is LiteralExpression -> {
            this.type = null
        }
        is NeedInfo -> {
            this.expression?.clearFromType()
        }
        is ReturnStatement -> this.expression?.clearFromType()
        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType ->{}

    }
}

fun clearNonIncrementalStoreFromTypes(nonIncrementalStore: MutableMap<String, List<Statement>>) {
    nonIncrementalStore.values.forEach {
        it.forEach { statement ->
            statement.clearFromType()
        }
    }
}

fun LS.resolveNonIncremental(uriOfChangedFile: String, source: String): Resolver {
    megaStore.data.clear()
    fileToDecl.clear()

    clearNonIncrementalStoreFromTypes(nonIncrementalStore)
    //    0) clear from types
    //    1) lex parse new changed file
    //    2) replace its ast in the NIS
    //    3) resolve everything again

    val file = File(URI(uriOfChangedFile))
    val fileAbsolute = file.absolutePath
    val mainAst = getAst(source = source, file = file)
    nonIncrementalStore[fileAbsolute] = mainAst
    // resolve everything and return resolver
//    val resolveFromNIS = { nonIncrementalStore: Map<String, List<Statement>> ->
        val localpm = pm
        if (localpm != null) {
            resolver = compileProjFromFile(
                localpm,
                compileOnlyOneFile = false,
                resolveOnlyNoBackend = true,
                onEachStatement = ::onEachStatementCall,
                customAst = getMainAstFromNIS(nonIncrementalStore, (pm!!.pathToNivaMainFile)) // astOfTheMain, Ast of everything
            )
        } else throw Exception("Local pm == null")
    return resolver
//    }
//    resolveFromNIS(this.nonIncrementalStore)

//
//
//    removeDecl2(file)
//    megaStore.data.remove(file.absolutePath)
//    resolver.reset()
//
//    val (mainAst) = getAstFromFiles(
//        mainFileContent = text,
//        otherFileContents = resolver.otherFilesPaths,
//        mainFilePath = file.absolutePath,
//        resolveOnlyOneFile = true
//    )
//
//    // throws on
//    resolver.resolveWithBackTracking(
//        mainAst,
//        emptyList(),
//        file.absolutePath,
//        file.nameWithoutExtension,
//        VerbosePrinter(false),
//    )
}


// first time, all files reading
// if non-incremental, then we will fill the nonIncrementalStore store
fun LS.resolveAllFirstTime(pathToChangedFile: String, fillNonIncrementalStore: Boolean = false): Resolver {
    GlobalVariables.enableLspMode()
    megaStore.data.clear()

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
//    fileToDecl[mainFile.absolutePath] = mutableSetOf(createFakeDeclaration())

    info?.invoke("main file is $mainFile ") //all files is ${allFiles.joinToString(", ") { it.name }}
    allFiles.remove(mainFile)
    // Resolve
    val pm = PathManager(mainFile.path, MainArgument.LSP)
    this.pm = pm

    try {

        // custom ast
        val customAst = getAstFromFiles(
            mainFileContent = mainFile.readText(),
            otherFileContents = allFiles.toList(),
            mainFilePath = mainFile.absolutePath,
            resolveOnlyOneFile = false
        )

        if (fillNonIncrementalStore)
            fillNonIncrementalStore(customAst, mainFile)

        this.resolver = compileProjFromFile(
            pm,
            resolveOnlyNoBackend = true,
            compileOnlyOneFile = false,
            onEachStatement = ::onEachStatementCall,
            customAst = Pair(customAst.first, customAst.second)
        )
        // not sure why reset this?
        this.completionFromScope = emptyMap()
        return resolver
    } catch (s: OnCompletionException) {
        val emptyResolver =
            Resolver.empty(otherFilesPaths = allFiles.toList(), ::onEachStatementCall, currentFile = mainFile)
        this.resolver = emptyResolver
        this.completionFromScope = s.scope

        info?.invoke("NOT RESOLVED OnCompletionException, error.scope = ${s.scope}, completionFromScope = $completionFromScope , error message = ${s.errorMessage?.removeColors()}")
        return resolver
    }

}


fun LS.fillNonIncrementalStore(
    // main ast, other ast, otherFiles
    customAst: Triple<List<Statement>, List<Pair<String, List<Statement>>>, List<File>>,
    mainFile: File
) {
    val (mainAst, pkgToAst, otherFiles) = customAst
    // add main
//    val uri = mainFile.toURI().toString()
    val uri = mainFile.absolutePath
    nonIncrementalStore[uri] = mainAst

    // add othersAst
    pkgToAst.forEachIndexed { index, pair ->
        val file = otherFiles[index]
//        val uri = file.toURI().toString()
        val uri = file.absolutePath
        nonIncrementalStore[uri] = pair.second
    }
//    fileToDecl[mainFile.absolutePath] = mutableSetOf(createFakeDeclaration())

}


fun LS.onEachStatementCall(
    st: Statement,
    currentScope: Map<String, Type>?,
    previousScope: Map<String, Type>?,
    file2: File
) {
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
        }


        else -> {}
    }
}

