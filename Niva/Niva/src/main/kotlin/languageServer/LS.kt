@file:Suppress("unused")

package main.languageServer

import frontend.resolver.MessageMetadata
import frontend.resolver.Protocol
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.buildGlobalConstScopeFromFiles
import frontend.resolver.buildGlobalConstScopeFromStatements
import frontend.resolver.clearDependenciesFor
import frontend.resolver.enqueueDependents
import frontend.resolver.enqueueForReResolve
import frontend.resolver.getAst
import frontend.resolver.parseFilesToAST
import frontend.resolver.processPendingMessageReResolves
import frontend.resolver.resolveWithBackTracking
import frontend.resolver.unpackNull
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import languageServer.readFromJson
import languageServer.toIdentifierExpr
import main.codogen.BuildSystem
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.meta.createFakeToken2
import main.frontend.meta.removeColors
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.Assign
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.CollectionAst
import main.frontend.parser.types.ast.ControlFlow
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.DotReceiver
import main.frontend.parser.types.ast.EnumBranch
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.ErrorDomainDeclaration
import main.frontend.parser.types.ast.ExtendDeclaration
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.ExpressionInBrackets
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.MapCollection
import main.frontend.parser.types.ast.ManyConstructorDecl
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationBinary
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.parser.types.ast.MessageSend
import main.frontend.parser.types.ast.NeedInfo
import main.frontend.parser.types.ast.PairOfErrorAndMessage
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.frontend.parser.types.ast.MethodReference
import main.frontend.parser.types.ast.ReturnStatement
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.StaticBuilder
import main.frontend.parser.types.ast.StaticBuilderDeclaration
import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.TypeFieldAST
import main.frontend.parser.types.ast.TypeAliasDeclaration
import main.frontend.parser.types.ast.TypeDeclaration
import main.frontend.parser.types.ast.UnaryMsg
import main.frontend.parser.types.ast.UnionBranchDeclaration
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.parser.types.ast.VarDeclaration
import main.utils.GlobalVariables
import main.utils.MainArgument
import main.utils.PathManager
import main.utils.VerbosePrinter
import main.utils.compileProjFromFile
import main.utils.div
import main.utils.listFilesDownUntilNivaIsFoundRecursively
import java.io.File
import java.net.URI
import java.util.*


fun Statement.unpackMessage() = if (this is VarDeclaration) {
    val value = this.value
    if (value is MessageSend) {
        value.messages.last()
    } else this
} else this

fun Token.toPositionKey(): String = "${file.absolutePath}:${line}:${relPos.start}"

private fun collectMessageDeclarationsFromStatements(statements: List<Statement>): List<MessageDeclaration> {
    val result = mutableListOf<MessageDeclaration>()
    statements.forEach { st ->
        when (st) {
            is MessageDeclaration -> result.add(st)
            is ExtendDeclaration -> result.addAll(st.messageDeclarations)
            is ManyConstructorDecl -> result.addAll(st.messageDeclarations)
            else -> {}
        }
    }
    return result
}

private fun collectMessageDeclarationsFromDeclarations(decls: Collection<Declaration>): List<MessageDeclaration> {
    val result = mutableListOf<MessageDeclaration>()
    decls.forEach { d ->
        when (d) {
            is MessageDeclaration -> result.add(d)
            is ExtendDeclaration -> result.addAll(d.messageDeclarations)
            is ManyConstructorDecl -> result.addAll(d.messageDeclarations)
            else -> {}
        }
    }
    return result
}

private fun messageDeclSignature(md: MessageDeclaration): String {
    val realDecl = if (md is ConstructorDeclaration) md.msgDeclaration else md
    val receiverKey = when (val r = realDecl.forTypeAst) {
        is TypeAST.UserType -> if (r.names.isNotEmpty()) r.names.joinToString(".") + "." + r.name else r.name
        else -> r.name
    }
    val argsKey = when (realDecl) {
        is MessageDeclarationKeyword -> realDecl.args.joinToString(",") { it.name }
        is MessageDeclarationBinary -> realDecl.arg.name
        else -> ""
    }
    return "$receiverKey|${realDecl.getDeclType()}|${realDecl.name}|$argsKey"
}

private enum class ChangeLineKind {
    Declaration,
    MessageBody,
    None
}

private fun isLineInMessageHeader(md: MessageDeclaration, line: Int): Boolean {
    val realDecl = if (md is ConstructorDeclaration) md.msgDeclaration else md
    if (realDecl.token.line == line) return true
    if (realDecl.forTypeAst.token.line == line) return true
    if (realDecl.returnTypeAST?.token?.line == line) return true
    return when (realDecl) {
        is MessageDeclarationKeyword -> realDecl.args.any {
            it.tok.line == line || it.typeAST?.token?.line == line
        }
        is MessageDeclarationBinary -> realDecl.arg.tok.line == line || realDecl.arg.typeAST?.token?.line == line
        else -> false
    }
}

private fun isLineInTypeDeclaration(td: SomeTypeDeclaration, line: Int): Boolean {
    if (td.token.line == line) return true
    return when (td) {
        is TypeAliasDeclaration -> td.realTypeAST.token.line == line
        is UnionRootDeclaration -> {
            td.fields.any { it.token.line == line } ||
                td.branches.any { branch ->
                    branch.token.line == line || branch.fields.any { it.token.line == line }
                }
        }
        is EnumDeclarationRoot -> {
            td.fields.any { it.token.line == line } ||
                td.branches.any { branch ->
                    branch.token.line == line || branch.fieldsValues.any { it.token.line == line }
                }
        }
        is ErrorDomainDeclaration -> isLineInTypeDeclaration(td.unionDeclaration, line)
        else -> td.fields.any { it.token.line == line }
    }
}

private fun messageDeclBodyContainsLine(md: MessageDeclaration, line: Int): Boolean {
    val realDecl = if (md is ConstructorDeclaration) md.msgDeclaration else md
    if (realDecl.body.isEmpty()) return false
    var minLine = Int.MAX_VALUE
    var maxLine = Int.MIN_VALUE
    realDecl.body.forEach { st ->
        val tok = st.token
        val endLine = if (tok.isMultiline()) tok.lineEnd else tok.line
        if (tok.line < minLine) minLine = tok.line
        if (endLine > maxLine) maxLine = endLine
    }
    return line in minLine..maxLine
}

private fun collectLinesForStatements(statements: List<Statement>): Set<Int> {
    if (statements.isEmpty()) return emptySet()
    val lines = mutableSetOf<Int>()
    statements.forEach { st ->
        val tok = st.token
        val endLine = if (tok.isMultiline()) tok.lineEnd else tok.line
        for (line in tok.line..endLine) {
            lines.add(line)
        }
    }
    return lines
}

private fun collectFullLineRangeForStatements(statements: List<Statement>): Set<Int> {
    if (statements.isEmpty()) return emptySet()
    var minLine = Int.MAX_VALUE
    var maxLine = Int.MIN_VALUE
    statements.forEach { st ->
        val tok = st.token
        val endLine = if (tok.isMultiline()) tok.lineEnd else tok.line
        if (tok.line < minLine) minLine = tok.line
        if (endLine > maxLine) maxLine = endLine
    }
    if (minLine == Int.MAX_VALUE || maxLine == Int.MIN_VALUE) return emptySet()
    val lines = mutableSetOf<Int>()
    for (line in minLine..maxLine) {
        lines.add(line)
    }
    return lines
}

private fun findMessageDeclByBodyLine(
    statements: List<Statement>,
    line: Int
): MessageDeclaration? {
    val msgDecls = collectMessageDeclarationsFromStatements(statements)
    return msgDecls.firstOrNull { messageDeclBodyContainsLine(it, line) }
}

private fun classifyChangeLine(statements: List<Statement>, line: Int): Pair<ChangeLineKind, MessageDeclaration?> {
    val typeDeclHit = statements.filterIsInstance<SomeTypeDeclaration>().any { isLineInTypeDeclaration(it, line) }
    if (typeDeclHit) return ChangeLineKind.Declaration to null

    val msgDecls = collectMessageDeclarationsFromStatements(statements)
    if (msgDecls.any { isLineInMessageHeader(it, line) }) {
        return ChangeLineKind.Declaration to null
    }

    val bodyDecl = msgDecls.firstOrNull { messageDeclBodyContainsLine(it, line) }
    return if (bodyDecl != null) ChangeLineKind.MessageBody to bodyDecl else ChangeLineKind.None to null
}

private fun LS.buildGlobalConstScopeForFile(file: File, mainAst: List<Statement>): MutableMap<String, Type> {
    return if (pm != null && nonIncrementalStore.isNotEmpty()) {
        val localpm = pm ?: throw Exception("Local pm == null")
        val (allMainAst, otherAst) = getMainAstFromNIS(nonIncrementalStore, localpm.pathToNivaMainFile)
        val mainPkgName = File(localpm.pathToNivaMainFile).nameWithoutExtension
        resolver.buildGlobalConstScopeFromFiles(mainPkgName, allMainAst, otherAst)
    } else {
        val savedPkg = resolver.currentPackageName
        resolver.currentPackageName = file.nameWithoutExtension
        val scope = resolver.buildGlobalConstScopeFromStatements(mainAst)
        resolver.currentPackageName = savedPkg
        scope
    }
}

private fun LS.updateMessageBodyAndReResolve(
    file: File,
    mainAst: List<Statement>,
    newDecl: MessageDeclaration
): Boolean {
    val fileAbsolutePath = file.absolutePath
    val oldDecls = fileToDecl[fileAbsolutePath]?.toSet() ?: return false
    val oldMsgDecls = collectMessageDeclarationsFromDeclarations(oldDecls)
    val oldBySig = oldMsgDecls.associateBy { messageDeclSignature(it) }
    val oldDecl = oldBySig[messageDeclSignature(newDecl)] ?: return false

    val oldReal = if (oldDecl is ConstructorDeclaration) oldDecl.msgDeclaration else oldDecl
    val newReal = if (newDecl is ConstructorDeclaration) newDecl.msgDeclaration else newDecl
    val oldBodyLines = collectLinesForStatements(oldReal.body) + collectFullLineRangeForStatements(oldReal.body)
    oldReal.body.clear()
    oldReal.body.addAll(newReal.body)

    resolver.clearDependenciesFor(listOf(oldDecl))

    val globalConstScope = buildGlobalConstScopeForFile(file, mainAst)

    resolver.enqueueForReResolve(listOf(oldDecl))
    megaStore.removeLines(fileAbsolutePath, oldBodyLines)
    resolver.processPendingMessageReResolves(globalConstScope, callOnEachStatement = true)

    resolver.enqueueDependents(oldDecl)
    resolver.processPendingMessageReResolves(globalConstScope, callOnEachStatement = false)

    return true
}



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

fun LS.readDevDataFromFile(path: String, info: ((String) -> Unit)?) {
    val fromJson = readFromJson(path)
    fromJson?.data?.forEach { (fileName, value) ->
        val file = File(fileName)
        value.forEach { (lineNum, values) ->
            values.forEach {
                val ident = it.toIdentifierExpr(file, lineNum)
                megaStore.addNew(
                    s = ident,
                    scope = mapOf(),
                    prepend = true
                )
            }
        }
    }
}

const val DEV_MODE_FILE_NAME = "devModeData.json"

class LS(val info: ((String) -> Unit)? = null) {
    lateinit var resolver: Resolver

    /// file to line to set of statements of that line
    val megaStore: MegaStore = MegaStore(info)
    var pm: PathManager? = null

    // from the variable usage identifier to its declaration token
    // keys: "file:line:char" values: VarDeclaration tokens
    val varUsageToDeclaration: MutableMap<String, Token> = mutableMapOf()

    // from variable name + file to its declaration token
    // keys: "file:varName" values: VarDeclaration tokens
    val varNameToDeclarationToken: MutableMap<String, Token> = mutableMapOf()

    // from message declaration token position to its usage tokens
    // keys: "file:line:char" values: usage tokens keyed by usage position
    val messageDeclarationUsages: MutableMap<String, MutableMap<String, Token>> = mutableMapOf()

    // from keyword declaration arg token position to its usage tokens
    // keys: "file:line:char" values: usage tokens keyed by usage position
    val keywordDeclarationUsages: MutableMap<String, MutableMap<String, Token>> = mutableMapOf()


    fun registerMessageUsage(declarationToken: Token, usageToken: Token) {
        if (!GlobalVariables.isLspMode) return
        val declarationKey = declarationToken.toPositionKey()
        val usageKey = usageToken.toPositionKey()
        val usages = messageDeclarationUsages.getOrPut(declarationKey) { mutableMapOf() }
        if (!usages.containsKey(usageKey)) {
            usages[usageKey] = usageToken
        }
    }

    fun runDevModeWatching(scope: CoroutineScope, info: ((String) -> Unit)?) {
        val pm = pm ?: return
        val watchDirPath = pm.nivaRootFolder
        val jsonDevFilePath = watchDirPath / DEV_MODE_FILE_NAME
        readDevDataFromFile(jsonDevFilePath, info)

        scope.launch(Dispatchers.IO) {
            info?.invoke("watch started")
            val watcher = KfsDirectoryWatcher(this, dispatcher = Dispatchers.IO)
            watcher.add(watchDirPath)
            watcher.onEventFlow.collect { event ->
//                info?.invoke("watching, got event: " + event.toString())
                if (event.path.endsWith("json") && (event.event == KfsEvent.Modify || event.event == KfsEvent.Create)) {
                    readDevDataFromFile(jsonDevFilePath, info)
                }
            }
        }
    }

    val nonIncrementalStore = mutableMapOf<String, List<Statement>>() // URI from LSP to AST

    var completionFromScope: Scope = emptyMap()

    // since one file can contain many pkgs, we need file to declaration map
    val fileToDecl: MutableMap<String, MutableSet<Declaration>> = mutableMapOf()

    fun debugCountsLine(): String {
        val megaEntries = megaStore.data.values.sumOf { it.values.sumOf { line -> line.size } }
        val megaUniqueStatements = run {
            val seen = IdentityHashMap<Statement, Boolean>()
            megaStore.data.values.forEach { lineMap ->
                lineMap.values.forEach { list ->
                    list.forEach { pair -> seen[pair.first] = true }
                }
            }
            seen.size
        }



        return "LS counts: " +
            "megaStore.entries=$megaEntries, " +
            "megaStore.uniqueStatements=$megaUniqueStatements \n--------"
    }

    class MegaStore(val info: ((String) -> Unit)? = null) {
        // file absolute path to line to a pair of statement + scope of it's line
        val data: MutableMap<String, SortedMap<Line, MutableList<Pair<Statement, Scope>>>> = mutableMapOf()

        fun removeLines(path: String, lines: Set<Line>) {
            if (lines.isEmpty()) return
            val file = data[path] ?: return
            lines.forEach { file.remove(it) }
            if (file.isEmpty()) {
                data.remove(path)
            }
        }

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
                for (i in set.size - 1 downTo 1) {
                    if (check(set[i], set[i - 1])) {
                        return if (returnLast) set[i]
                        else set[i - 1]
                    }
                }
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
                LspResult.NotFoundFile()
            }
        }

        fun findReceiverBeforePartial(path: String, line: Int, wordStart: Int): LspResult.Found? {
            val statements = data[path]?.get(line) ?: return null
            statements.asReversed().forEach { (statement, _) ->
                val expression = if (statement is VarDeclaration) statement.value else statement
                if (expression is Message) {
                    if (expression.token.relPos.start <= wordStart && expression.receiver.token.relPos.end <= wordStart) {
                        return LspResult.Found(expression.receiver, false)
                    }
                    return@forEach
                }
                if (expression is MessageSend) {
                    if (expression.token.relPos.start > wordStart) return@forEach

                    val completedMessage = expression.messages.lastOrNull { it.token.relPos.end <= wordStart }
                    if (completedMessage != null) {
                        return LspResult.Found(completedMessage, false)
                    }
                    if (expression.receiver.token.relPos.end <= wordStart) {
                        return LspResult.Found(expression.receiver, false)
                    }
                }
            }
            return null
        }

        fun findTypeForNameBefore(path: String, line: Int, name: String): Type? {
            val lines = data[path] ?: return null
            lines.headMap(line + 1).toList().asReversed().forEach { (_, statements) ->
                statements.asReversed().forEach { (statement, scope) ->
                    scope[name]?.let { return it }
                    if (statement is VarDeclaration && statement.name == name) {
                        statement.value.type?.let { return it }
                    }
                }
            }
            return null
        }
    }
}

private data class LspStateSnapshot(
    val resolver: Resolver?,
    val megaStoreData: MutableMap<String, SortedMap<Line, MutableList<Pair<Statement, Scope>>>>,
    val fileToDecl: MutableMap<String, MutableSet<Declaration>>,
    val varUsageToDeclaration: MutableMap<String, Token>,
    val varNameToDeclarationToken: MutableMap<String, Token>,
    val messageDeclarationUsages: MutableMap<String, MutableMap<String, Token>>,
    val keywordDeclarationUsages: MutableMap<String, MutableMap<String, Token>>,
    val astResolutionSnapshot: AstResolutionSnapshot,
    val nonIncrementalStore: MutableMap<String, List<Statement>>? = null
)

private data class MessageState(
    val type: Type?,
    val declaration: MessageDeclaration?,
    val metadata: MessageMetadata?
)

private data class MessageDeclarationState(
    val forType: Type?,
    val returnType: Type?,
    val messageData: MessageMetadata?,
    val possibleErrors: List<PairOfErrorAndMessage>
)

private data class SomeTypeDeclarationState(
    val receiver: Type?,
    val realType: Type?
)

private data class AstResolutionSnapshot(
    val expressionTypes: IdentityHashMap<Expression, Type?> = IdentityHashMap(),
    val messageStates: IdentityHashMap<Message, MessageState> = IdentityHashMap(),
    val methodReferences: IdentityHashMap<MethodReference, MessageMetadata?> = IdentityHashMap(),
    val messageDeclarations: IdentityHashMap<MessageDeclaration, MessageDeclarationState> = IdentityHashMap(),
    val typeDeclarations: IdentityHashMap<SomeTypeDeclaration, SomeTypeDeclarationState> = IdentityHashMap()
) {
    fun restore() {
        expressionTypes.forEach { (expr, type) -> expr.type = type }
        messageStates.forEach { (msg, state) ->
            msg.type = state.type
            msg.declaration = state.declaration
            msg.msgMetaData = state.metadata
        }
        methodReferences.forEach { (ref, method) -> ref.method = method }
        messageDeclarations.forEach { (decl, state) ->
            decl.forType = state.forType
            decl.returnType = state.returnType
            decl.messageData = state.messageData
            decl.stackOfPossibleErrors.clear()
            decl.stackOfPossibleErrors.addAll(state.possibleErrors)
        }
        typeDeclarations.forEach { (decl, state) ->
            decl.receiver = state.receiver
            if (decl is TypeAliasDeclaration) {
                decl.realType = state.realType
            }
        }
    }
}

private fun collectAstResolutionSnapshot(statements: Collection<Statement>): AstResolutionSnapshot {
    val snapshot = AstResolutionSnapshot()
    val visited = Collections.newSetFromMap(IdentityHashMap<Statement, Boolean>())

    fun visitExpression(expr: Expression) {
        snapshot.expressionTypes[expr] = expr.type
        if (expr is Message) {
            snapshot.messageStates[expr] = MessageState(expr.type, expr.declaration, expr.msgMetaData)
        }
        if (expr is MethodReference) {
            snapshot.methodReferences[expr] = expr.method
        }
    }

    fun visitStatement(statement: Statement?) {
        if (statement == null || !visited.add(statement)) return

        if (statement is Expression) {
            visitExpression(statement)
        }

        when (statement) {
            is VarDeclaration -> visitStatement(statement.value)
            is Assign -> visitStatement(statement.value)
            is ExtendDeclaration -> statement.messageDeclarations.forEach { visitStatement(it) }
            is ManyConstructorDecl -> statement.messageDeclarations.forEach { visitStatement(it) }
            is ConstructorDeclaration -> {
                snapshot.messageDeclarations[statement] = MessageDeclarationState(
                    statement.forType,
                    statement.returnType,
                    statement.messageData,
                    statement.stackOfPossibleErrors.toList()
                )
                statement.body.forEach { visitStatement(it) }
                visitStatement(statement.msgDeclaration)
            }
            is MessageDeclaration -> {
                snapshot.messageDeclarations[statement] = MessageDeclarationState(
                    statement.forType,
                    statement.returnType,
                    statement.messageData,
                    statement.stackOfPossibleErrors.toList()
                )
                statement.body.forEach { visitStatement(it) }
            }
            is SomeTypeDeclaration -> {
                snapshot.typeDeclarations[statement] = SomeTypeDeclarationState(
                    statement.receiver,
                    (statement as? TypeAliasDeclaration)?.realType
                )
                when (statement) {
                    is EnumDeclarationRoot -> statement.branches.forEach { visitStatement(it) }
                    is ErrorDomainDeclaration -> visitStatement(statement.unionDeclaration)
                    is UnionRootDeclaration -> statement.branches.forEach { visitStatement(it) }
                    else -> {}
                }
            }
            is DestructingAssign -> {
                statement.names.forEach { visitStatement(it) }
                visitStatement(statement.value)
            }
            is ControlFlow.If -> {
                statement.ifBranches.forEach {
                    visitStatement(it.ifExpression)
                    it.otherIfExpressions.forEach { other -> visitStatement(other) }
                    when (it) {
                        is main.frontend.parser.types.ast.IfBranch.IfBranchSingleExpr -> visitStatement(it.thenDoExpression)
                        is main.frontend.parser.types.ast.IfBranch.IfBranchWithBody -> visitStatement(it.body)
                    }
                }
                statement.elseBranch?.forEach { visitStatement(it) }
            }
            is ControlFlow.Switch -> {
                visitStatement(statement.switch)
                statement.ifBranches.forEach {
                    visitStatement(it.ifExpression)
                    it.otherIfExpressions.forEach { other -> visitStatement(other) }
                    when (it) {
                        is main.frontend.parser.types.ast.IfBranch.IfBranchSingleExpr -> visitStatement(it.thenDoExpression)
                        is main.frontend.parser.types.ast.IfBranch.IfBranchWithBody -> visitStatement(it.body)
                    }
                }
                statement.elseBranch?.forEach { visitStatement(it) }
            }
            is CodeBlock -> {
                statement.inputList.forEach { visitStatement(it) }
                statement.statements.forEach { visitStatement(it) }
            }
            is CollectionAst -> statement.initElements.forEach { visitStatement(it) }
            is ExpressionInBrackets -> visitStatement(statement.expr)
            is MapCollection -> statement.initElements.forEach { (key, value) ->
                visitStatement(key)
                visitStatement(value)
            }
            is BinaryMsg -> {
                visitStatement(statement.receiver)
                visitStatement(statement.argument)
                statement.unaryMsgsForArg.forEach { visitStatement(it) }
                statement.unaryMsgsForReceiver.forEach { visitStatement(it) }
            }
            is KeywordMsg -> {
                visitStatement(statement.receiver)
                statement.args.forEach { visitStatement(it.keywordArg) }
            }
            is StaticBuilder -> visitStatement(statement.receiver)
            is UnaryMsg -> visitStatement(statement.receiver)
            is MessageSend -> {
                visitStatement(statement.receiver)
                statement.messages.forEach { visitStatement(it) }
            }
            is NeedInfo -> visitStatement(statement.expression)
            is ReturnStatement -> visitStatement(statement.expression)
            is DotReceiver, is IdentifierExpr, is LiteralExpression, is MethodReference -> {}
            is TypeAST.InternalType, is TypeAST.Lambda, is TypeAST.UserType -> {}
        }
    }

    statements.forEach { visitStatement(it) }
    return snapshot
}

private fun LS.collectStatementsForAstResolutionSnapshot(includeNonIncrementalStore: Boolean): List<Statement> {
    val statements = mutableListOf<Statement>()
    megaStore.data.values.forEach { lineMap ->
        lineMap.values.forEach { lineStatements ->
            lineStatements.forEach { statements.add(it.first) }
        }
    }
    fileToDecl.values.forEach { statements.addAll(it) }
    if (includeNonIncrementalStore) {
        nonIncrementalStore.values.forEach { statements.addAll(it) }
    }
    return statements
}

private fun LS.snapshotLspState(includeNonIncrementalStore: Boolean = false): LspStateSnapshot =
    LspStateSnapshot(
        resolver = runCatching { resolver }.getOrNull(),
        megaStoreData = megaStore.data.toMutableMap(),
        fileToDecl = fileToDecl.toMutableMap(),
        varUsageToDeclaration = varUsageToDeclaration.toMutableMap(),
        varNameToDeclarationToken = varNameToDeclarationToken.toMutableMap(),
        messageDeclarationUsages = messageDeclarationUsages.toMutableMap(),
        keywordDeclarationUsages = keywordDeclarationUsages.toMutableMap(),
        astResolutionSnapshot = collectAstResolutionSnapshot(collectStatementsForAstResolutionSnapshot(includeNonIncrementalStore)),
        nonIncrementalStore = if (includeNonIncrementalStore) nonIncrementalStore.toMutableMap() else null
    )

private fun LS.clearLspIndexes() {
    megaStore.data.clear()
    fileToDecl.clear()
    varUsageToDeclaration.clear()
    varNameToDeclarationToken.clear()
    messageDeclarationUsages.clear()
    keywordDeclarationUsages.clear()
}

private fun <K, V> MutableMap<K, V>.replaceWith(other: Map<K, V>) {
    clear()
    putAll(other)
}

private fun LS.restoreLspState(snapshot: LspStateSnapshot) {
    snapshot.astResolutionSnapshot.restore()
    val previousResolver = snapshot.resolver
    if (previousResolver != null) {
        resolver = previousResolver
    }
    megaStore.data.replaceWith(snapshot.megaStoreData)
    fileToDecl.replaceWith(snapshot.fileToDecl)
    varUsageToDeclaration.replaceWith(snapshot.varUsageToDeclaration)
    varNameToDeclarationToken.replaceWith(snapshot.varNameToDeclarationToken)
    messageDeclarationUsages.replaceWith(snapshot.messageDeclarationUsages)
    keywordDeclarationUsages.replaceWith(snapshot.keywordDeclarationUsages)

    val previousNonIncrementalStore = snapshot.nonIncrementalStore
    if (previousNonIncrementalStore != null) {
        nonIncrementalStore.replaceWith(previousNonIncrementalStore)
    }
}

private fun LS.replaceLspStateFrom(other: LS) {
    resolver = other.resolver
    pm = other.pm
    megaStore.data.replaceWith(other.megaStore.data)
    fileToDecl.replaceWith(other.fileToDecl)
    varUsageToDeclaration.replaceWith(other.varUsageToDeclaration)
    varNameToDeclarationToken.replaceWith(other.varNameToDeclarationToken)
    messageDeclarationUsages.replaceWith(other.messageDeclarationUsages)
    keywordDeclarationUsages.replaceWith(other.keywordDeclarationUsages)
    nonIncrementalStore.replaceWith(other.nonIncrementalStore)
    completionFromScope = other.completionFromScope
}

private fun LS.resolveFreshInScratch(uriOfChangedFile: String, source: String) {
    val changedFile = File(URI(uriOfChangedFile))
    val (mainFile, allOtherFiles2) = readAllFilesFromDisc(changedFile, uriOfChangedFile, source)
    val allFiles = allOtherFiles2.sortedBy { file -> file.name }.toMutableList()
    val scratch = LS(info)
    val scratchPm = PathManager(mainFile.absolutePath, MainArgument.LSP, null)
    scratch.pm = scratchPm

    val customAst = parseFilesToAST(
        mainFileContent =
            if (mainFile.absolutePath == changedFile.absolutePath)
                source
            else mainFile.readText(),
        otherFileContents = allFiles.toList(),
        mainFilePath = mainFile.absolutePath,
        resolveOnlyOneFile = false,
        pathToChangedFile = changedFile,
        changedFileContent = source
    )

    scratch.resolver = compileProjFromFile(
        scratchPm,
        dontRunCodegen = true,
        compileOnlyOneFile = false,
        onEachStatement = { st, currentScope, previousScope, file ->
            scratch.onEachStatementCall(st, currentScope, previousScope, file)
        },
        customAst = Pair(customAst.first, customAst.second),
        buildSystem = BuildSystem.Amper,
        previousFilePath = allFiles
    )
    scratch.fillNonIncrementalStore(customAst, mainFile)
    scratch.completionFromScope = emptyMap()
    replaceLspStateFrom(scratch)
}

// resolve all with lines to statements lists maps (Map(Line, Obj(List::Statements, scope)) )
//private fun findCurrentWordStart(sourceText: String?, line: Int, character: Int): Int? {
//    if (sourceText == null) return null
//    val sourceLine = sourceText.split('\n').getOrNull(line) ?: return null
//    val cursor = character.coerceIn(0, sourceLine.length)
//    var start = cursor
//    while (start > 0 && sourceLine[start - 1].isNivaCompletionWordPart()) {
//        start--
//    }
//    return start.takeIf { it < cursor }
//}

private fun findCurrentWordStart(
    sourceText: String?,
    line: Int,
    character: Int
): Int? {
    if (sourceText == null) return null

    var lineStart = 0
    repeat(line) {
        lineStart = sourceText.indexOf('\n', lineStart)
        if (lineStart == -1) return null
        lineStart++
    }

    val cursor = (lineStart + character)
        .coerceAtMost(sourceText.length)

    var start = cursor
    while (start > lineStart &&
        sourceText[start - 1].isNivaCompletionWordPart()
    ) {
        start--
    }

    return start.takeIf { it < cursor }
}

private fun Char.isNivaCompletionWordPart(): Boolean =
    isLetterOrDigit() || this == '_' || this == '.'

//private fun findReceiverNameBeforePartial(sourceText: String?, line: Int, wordStart: Int): Pair<String, Int>? {
//    if (sourceText == null) return null
//    val sourceLine = sourceText.split('\n').getOrNull(line) ?: return null
//    var end = wordStart
//    while (end > 0 && sourceLine[end - 1].isWhitespace()) {
//        end--
//    }
//    var start = end
//    while (start > 0 && sourceLine[start - 1].isNivaCompletionWordPart()) {
//        start--
//    }
//    if (start == end) return null
//    return sourceLine.substring(start, end) to start
//}

private fun findReceiverNameBeforePartial(
    sourceText: String?,
    line: Int,
    wordStart: Int
): Pair<String, Int>? {
    if (sourceText == null) return null

    var lineStart = 0
    repeat(line) {
        val nl = sourceText.indexOf('\n', lineStart)
        if (nl == -1) return null
        lineStart = nl + 1
    }

    val lineEnd = sourceText.indexOf('\n', lineStart)
        .takeIf { it != -1 }
        ?: sourceText.length

    val cursor = wordStart.coerceIn(0, lineEnd - lineStart)

    var end = lineStart + cursor
    while (end > lineStart && sourceText[end - 1].isWhitespace()) {
        end--
    }

    var start = end
    while (start > lineStart &&
        sourceText[start - 1].isNivaCompletionWordPart()
    ) {
        start--
    }

    if (start == end) return null

    return sourceText.substring(start, end) to (start - lineStart)
}

private fun LspResult.Found.expressionType(): Type? {
    val expr = if (statement is VarDeclaration) statement.value else statement
    return (expr as? Expression)?.type
}

fun LS.onCompletion(pathToChangedFile: String, line: Int, character: Int, sourceText: String? = null): LspResult {
    // We don't need to resolve anything on completion, it happens when code changes
    // find statement type

    val fileAbsolutePath = File(URI(pathToChangedFile)).absolutePath
    val a = megaStore.find(fileAbsolutePath, line + 1, character, completionFromScope) // vsc count lines from 0
    if (a !is LspResult.Found || a.expressionType() == null) {
        val wordStart = findCurrentWordStart(sourceText, line, character)
        if (wordStart != null) {
            val beforePartial = megaStore.find(fileAbsolutePath, line + 1, wordStart, completionFromScope)
            if (beforePartial is LspResult.Found) {
                return beforePartial
            }
            val receiverBeforePartial = megaStore.findReceiverBeforePartial(fileAbsolutePath, line + 1, wordStart)
            if (receiverBeforePartial != null) {
                return receiverBeforePartial
            }
            val receiverName = findReceiverNameBeforePartial(sourceText, line, wordStart)
            if (receiverName != null) {
                val (name, start) = receiverName
                val type = megaStore.findTypeForNameBefore(fileAbsolutePath, line + 1, name)
                if (type != null) {
                    val token = createFakeToken2(name, line + 1, start, start + name.length, File(fileAbsolutePath))
                    return LspResult.Found(IdentifierExpr(name, token = token).also { it.type = type }, false)
                }
            }
        }
    }

    return a
}

fun LS.removeDecl2(file: File) {
    // цель - удалить из typeDB все методы которые содержались в file
    // у нас есть файл ту декларации методов методы fileToDecl
    // находим в нем того который требуется удалять
    val declsOfTheFile = fileToDecl[file.absolutePath]
    val fileAbsolutePath = file.absolutePath

    val typeDB = resolver.typeDB
    var pkgName: String? = null
    fun canonicalizeType(type: Type?): Type? {
        val direct = type?.unpackNull()
        return when (direct) {
            is Type.UserLike -> typeDB.userTypes[direct.name]?.find { it.pkg == direct.pkg } ?: direct
            is Type.InternalType -> typeDB.internalTypes[direct.name] ?: direct
            is Type.Lambda -> {
                val alias = direct.alias
                if (alias != null) typeDB.lambdaTypes[alias] ?: direct else direct
            }
            else -> direct
        }
    }

    declsOfTheFile?.forEach { d ->

        // remove message
        if (d is MessageDeclaration) {
            val forType = canonicalizeType(d.forType)
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
                                //info?.invoke("usrLikeTypes = $usrLikeTypes, forType.pkg = ${forType.pkg} ")
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
        //info?.invoke("The whole package removed: $pkgName")
    }
    // fallback: remove any methods declared in this file from all protocols
    fun shouldRemove(meta: MessageMetadata?): Boolean =
        meta?.declaration?.token?.file?.absolutePath == fileAbsolutePath

    fun pruneProtocol(protocol: Protocol) {
        protocol.unaryMsgs.entries.removeIf { shouldRemove(it.value) }
        protocol.binaryMsgs.entries.removeIf { shouldRemove(it.value) }
        protocol.keywordMsgs.entries.removeIf { shouldRemove(it.value) }
        protocol.builders.entries.removeIf { shouldRemove(it.value) }
        protocol.staticMsgs.entries.removeIf { shouldRemove(it.value) }
    }

    typeDB.userTypes.values.forEach { list ->
        list.forEach { type ->
            type.protocols.values.forEach { pruneProtocol(it) }
        }
    }
    typeDB.internalTypes.values.forEach { type ->
        type.protocols.values.forEach { pruneProtocol(it) }
    }
    typeDB.lambdaTypes.values.forEach { type ->
        type.protocols.values.forEach { pruneProtocol(it) }
    }

    fileToDecl.remove(file.absolutePath)
}


fun LS.resolveIncremental(pathToChangedFile: String, text: String, changeLine: Int? = null) {
    val previousLspState = snapshotLspState(includeNonIncrementalStore = true)
    try {
        val file = File(URI(pathToChangedFile))
        val fileAbsolutePath = file.absolutePath
        val oldTypeDeclarations = nonIncrementalStore[fileAbsolutePath]?.typeDeclarationSignatures() ?: emptySet()
        var parsedChangedFileAst: List<Statement>? = null

        fun parseChangedFileAst(): List<Statement> {
            val alreadyParsed = parsedChangedFileAst
            if (alreadyParsed != null) return alreadyParsed
            val (mainAst) = parseFilesToAST(
                mainFileContent = text,
                otherFileContents = resolver.otherFilesPaths,
                mainFilePath = file.absolutePath,
                resolveOnlyOneFile = true
            )
            parsedChangedFileAst = mainAst
            return mainAst
        }

        fun typeDeclarationsChanged(mainAst: List<Statement>): Boolean {
            val newTypeDeclarations = mainAst.typeDeclarationSignatures()
            return oldTypeDeclarations != newTypeDeclarations
        }

        val changeLine1Based = changeLine?.plus(1)
        if (changeLine1Based != null) {
            val probeAst = parseChangedFileAst()

            if (typeDeclarationsChanged(probeAst)) {
                resolveNonIncremental(pathToChangedFile, text, forceFull = true)
                return
            }
            val (kind, newDecl) = classifyChangeLine(probeAst, changeLine1Based)
            if (kind == ChangeLineKind.Declaration) {
                resolveNonIncremental(pathToChangedFile, text, forceFull = true)
                return
            }
            if (kind == ChangeLineKind.MessageBody && newDecl != null) {
                val handled = updateMessageBodyAndReResolve(file, probeAst, newDecl)
                if (handled) {
                    completionFromScope = emptyMap()
                    return
                }
            }
            if (kind == ChangeLineKind.None) {
                resolveFreshInScratch(pathToChangedFile, text)
                return
            }
            // fall through to full incremental if we couldn't handle it
        }

        val mainAst = parseChangedFileAst()
        if (typeDeclarationsChanged(mainAst)) {
            resolveNonIncremental(pathToChangedFile, text, forceFull = true)
            return
        }

        // let's assume user cant change packages names for now, so pkg name always == filename
        // remove everything that was declarated in this changed file
        val oldDecls = fileToDecl[fileAbsolutePath]?.toSet() ?: emptySet()
        val oldMsgDecls = collectMessageDeclarationsFromDeclarations(oldDecls)
        val oldDepsBySig = mutableMapOf<String, MutableSet<MessageDeclaration>>()
        val affectedCallers = mutableSetOf<MessageDeclaration>()
        oldMsgDecls.forEach { old ->
            resolver.msgDependents[old]?.let { deps ->
                oldDepsBySig[messageDeclSignature(old)] = deps
                affectedCallers.addAll(deps)
            }
        }
        val affectedIter = affectedCallers.iterator()
        while (affectedIter.hasNext()) {
            val next = affectedIter.next()
            if (next.token.file.absolutePath == fileAbsolutePath) {
                affectedIter.remove()
            }
        }
        resolver.clearDependenciesFor(oldMsgDecls)

        removeDecl2(file)
        megaStore.data.remove(fileAbsolutePath)
        resolver.reset()

        nonIncrementalStore[fileAbsolutePath] = mainAst

        val newMsgDecls = collectMessageDeclarationsFromStatements(mainAst)
        val sigToNew = newMsgDecls.associateBy { messageDeclSignature(it) }
        oldDepsBySig.forEach { (sig, deps) ->
            val newDecl = sigToNew[sig]
            if (newDecl != null) {
                resolver.msgDependents[newDecl] = deps
            }
        }

        val globalConstScope = buildGlobalConstScopeForFile(file, mainAst)

        // throws on
        resolver.resolveWithBackTracking(
            mainAst,
            emptyList(),
            file.absolutePath,
            file.nameWithoutExtension,
            VerbosePrinter(false),
            globalConstScopeOverride = globalConstScope,
        )

        if (affectedCallers.isNotEmpty()) {
            affectedCallers.forEach { it.clearFromType() }
            resolver.enqueueForReResolve(affectedCallers)
            resolver.processPendingMessageReResolves(globalConstScope, callOnEachStatement = false)
        }
        completionFromScope = emptyMap()
    } catch (e: Throwable) {
        // fallback to full resolve to recover a consistent state
        restoreLspState(previousLspState)
        resolveNonIncremental(pathToChangedFile, text, forceFull = true)
    }
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

private fun buildOrderedAstFromStore(
    nonIncrementalStore: MutableMap<String, List<Statement>>,
    mainFile: File,
    otherFiles: List<File>,
    changedFile: File,
    changedFileContent: String
): Pair<List<Statement>, List<Pair<String, List<Statement>>>> {
    fun astFor(file: File): List<Statement> {
        val absolutePath = file.absolutePath
        nonIncrementalStore[absolutePath]?.let { return it }

        val source =
            if (absolutePath == changedFile.absolutePath) changedFileContent
            else file.readText()
        val ast = getAst(source = source, file = file)
        nonIncrementalStore[absolutePath] = ast
        return ast
    }

    val mainAst = astFor(mainFile)
    val mainFileAbsolutePath = mainFile.absolutePath
    val otherAst = otherFiles
        .asSequence()
        .distinctBy { it.absolutePath }
        .filter { it.absolutePath != mainFileAbsolutePath }
        .map { file -> file.nameWithoutExtension to astFor(file) }
        .toList()

    return mainAst to otherAst
}

private fun hasTypeDeclarations(statements: List<Statement>): Boolean {
    return statements.any {
        it is TypeDeclaration ||
            it is TypeAliasDeclaration ||
            it is UnionRootDeclaration ||
            it is EnumDeclarationRoot ||
            it is ErrorDomainDeclaration
    }
}

private fun List<Statement>.typeDeclarationSignatures(): Set<String> {
    fun TypeAST.key(): String {
        val nullable = if (isNullable) "?" else ""
        val mutable = if (isMutable) "mut " else ""
        val errorsKey = errors?.joinToString(prefix = "!", separator = "|") ?: ""
        return when (this) {
            is TypeAST.UserType -> {
                val args = typeArgumentList
                    .map { it.key() }
                    .sorted()
                    .joinToString(prefix = "(", postfix = ")")
                "$mutable${names.joinToString(".")}$args$nullable$errorsKey"
            }
            is TypeAST.InternalType -> "$mutable$name$nullable$errorsKey"
            is TypeAST.Lambda -> {
                val receiver = extensionOfType?.key()?.let { "$it." } ?: ""
                val args = inputTypesList.joinToString(",") { it.key() }
                "$mutable$receiver[$args->${returnType.key()}]$nullable$errorsKey"
            }
        }
    }

    fun List<TypeFieldAST>.key(): String =
        joinToString(prefix = "(", postfix = ")") { "${it.name}:${it.typeAST?.key() ?: ""}" }

    fun SomeTypeDeclaration.baseKey(kind: String): String {
        val generics = genericFields.sorted().joinToString(prefix = "<", postfix = ">")
        return "$kind:$typeName$generics:${fields.key()}"
    }

    val result = mutableSetOf<String>()
    this.forEach { statement ->
        when (statement) {
            is TypeDeclaration -> result.add(statement.baseKey("type"))
            is TypeAliasDeclaration -> result.add("${statement.baseKey("alias")}:${statement.realTypeAST.key()}")
            is ErrorDomainDeclaration -> result.add(statement.unionDeclaration.baseKey("error"))
            is UnionRootDeclaration -> {
                result.add(statement.baseKey("union"))
                statement.branches.forEach { result.add(it.baseKey("unionBranch")) }
            }
            is EnumDeclarationRoot -> {
                result.add(statement.baseKey("enum"))
                statement.branches.forEach {
                    val values = it.fieldsValues.joinToString(prefix = "(", postfix = ")") { field ->
                        "${field.name}:${field.value}"
                    }
                    result.add("${it.baseKey("enumBranch")}:$values")
                }
            }
            else -> {}
        }
    }
    return result
}


fun LS.resolveNonIncremental(uriOfChangedFile: String, source: String, forceFull: Boolean = false): Resolver {
    if (pm == null) {
        return resolveAllFirstTime(uriOfChangedFile, fillNonIncrementalStore = true, changedFileContent = source)
    }

    val previousLspState = snapshotLspState(includeNonIncrementalStore = true)

    try {
        val isMainFileRecompiling = uriOfChangedFile.endsWith("main.niva")
        val file = File(URI(uriOfChangedFile))
        val fileAbsolute = file.absolutePath
        val oldHadTypeDeclarations = hasTypeDeclarations(nonIncrementalStore[fileAbsolute] ?: emptyList())
        val mainAst = getAst(source = source, file = file)

        clearLspIndexes()

        clearNonIncrementalStoreFromTypes(nonIncrementalStore)
        //    0) clear AST from types
        //    1) lex parse new changed file
        //    2) replace its ast in the NIS
        //    3) resolve everything again

        // if there are no type declarations, use incremental resolve for this file only
        if (!forceFull && !oldHadTypeDeclarations && !hasTypeDeclarations(mainAst)) {
            nonIncrementalStore[fileAbsolute] = mainAst
            resolveIncremental(uriOfChangedFile, source)
            return resolver
        }

        val newNonIncrementalStore = nonIncrementalStore.toMutableMap()
        newNonIncrementalStore[fileAbsolute] = mainAst
        // resolve everything and return resolver
        val localpm = pm
        if (localpm != null) {
            // adding the current file, if its a new one
            // would be a better solution to do this only on open file
            val previousFilePath = if (isMainFileRecompiling)
                resolver.otherFilesPaths
            else (resolver.otherFilesPaths + file)
                .distinctBy { it.absolutePath }
                .toMutableList()

            val customAst = buildOrderedAstFromStore(
                newNonIncrementalStore,
                File(localpm.pathToNivaMainFile),
                previousFilePath,
                file,
                source
            )

            resolver = compileProjFromFile(
                localpm,
                compileOnlyOneFile = false,
                dontRunCodegen = true,
                onEachStatement = ::onEachStatementCall,
                customAst = customAst, // astOfTheMain, Ast of everything
                buildSystem = BuildSystem.Amper,// it doesnt matter, since we dont generate the code
                previousFilePath = previousFilePath
            )
            nonIncrementalStore.clear()
            nonIncrementalStore.putAll(newNonIncrementalStore)
            completionFromScope = emptyMap()
        } else throw Exception("Local pm == null")
        return resolver
    } catch (e: Throwable) {
        restoreLspState(previousLspState)
        throw e
    }
}

// if we have changed content then replace the read from disc with it, to not to read the old one
fun readAllFilesFromDisc(file: File, pathToChangedFile: String, mainContent: String?): Pair<File, MutableSet<File>> {
    fun getNivaFilesInSameDirectory(file: File): Set<File> {
        val directory: File? = file.parentFile
        return if (directory?.isDirectory == true) {
            val q = directory.listFiles()
            q?.asSequence()?.filter { it.extension == "niva" }?.toSet() // || it.extension == "scala"
                ?: emptySet() //TO DO("Cant find files in the $directory")
        } else {
            emptySet()
        }
    }



    // returns path to main.niva and set of all files
    // Doesn't search inside folders, only goes outside
    fun findMainUpRecursively(a: File, listOfNivaFiles: MutableSet<File>): Pair<File, MutableSet<File>> {
        var current: File? = a
        var depth = 0
        val filesInStartDir = getNivaFilesInSameDirectory(a)
        val fallbackInStartDir = filesInStartDir.firstOrNull()

        while (current != null && depth < 5) {
            val filesFromTheUpperDir = getNivaFilesInSameDirectory(current)
            listOfNivaFiles.addAll(filesFromTheUpperDir)

            // find if there is main.niva
            val nivaMain = listOfNivaFiles.find { it.nameWithoutExtension == "main" }
            if (nivaMain != null) {
                return Pair(nivaMain, listOfNivaFiles)
            }

            val next = current.parentFile ?: break
            current = next
            depth++
        }

        val fallback = fallbackInStartDir ?: if (a.extension == "niva") a else null
        return Pair(fallback ?: a, listOfNivaFiles)
    }

    val collectFiles = {
        // get main file
        val pair = findMainUpRecursively(file, mutableSetOf())
        // listFilesDownUntilNivaIsFoundRecursively from main to get all files
        val set = listFilesDownUntilNivaIsFoundRecursively(pair.first.parentFile, "niva")
        pair.also {
            it.second.addAll(set)
            it.second.remove(it.first) // remove main from other files
        }
    }
    return collectFiles()
}
// first time, all files reading
// if non-incremental, then we will fill the nonIncrementalStore store
fun LS.resolveAllFirstTime(
    pathToChangedFileURI: String,
    fillNonIncrementalStore: Boolean = false,
    changedFileContent: String? // is null when we re-resolve everything on file closed
): Resolver {
    //info?.invoke("LSP resolveAllFirstTime: start uri=$pathToChangedFileURI textLen=${changedFileContent?.length ?: -1}")
    GlobalVariables.enableLspMode()
    val previousLspState = snapshotLspState()
    clearLspIndexes()
//    info?.invoke("pathToChangedFileURI = $pathToChangedFileURI")

    val changedFile = File(URI(pathToChangedFileURI))
    assert(changedFile.exists())
    val (mainFile, allOtherFiles2) = readAllFilesFromDisc(changedFile, pathToChangedFileURI, changedFileContent)
    val allFiles = allOtherFiles2.sortedBy { file -> file.name }.toMutableList()
    //info?.invoke("LSP resolveAllFirstTime: main=${mainFile.absolutePath} others=${allFiles.size}")

    // Resolve
    // buildSystem doesn't matter here
    val pm = PathManager(mainFile.absolutePath, MainArgument.LSP, null)
    this.pm = pm

    try {
        // custom ast
        val customAst = parseFilesToAST(
            mainFileContent =
                if (mainFile.absolutePath == changedFile.absolutePath && changedFileContent != null)
                    changedFileContent
                else { mainFile.readText() },
            otherFileContents = allFiles.toList(),
            mainFilePath = mainFile.absolutePath,
            resolveOnlyOneFile = false,
            pathToChangedFile = changedFile,
            changedFileContent = changedFileContent
        )

        val newNonIncrementalStore = if (fillNonIncrementalStore) {
            buildNonIncrementalStore(customAst, mainFile)
        } else {
            null
        }
        this.resolver = compileProjFromFile(
            pm,
            dontRunCodegen = true,
            compileOnlyOneFile = false,
            onEachStatement = ::onEachStatementCall,
            customAst = Pair(customAst.first, customAst.second),
            buildSystem = BuildSystem.Amper, // doesnt matter since we dont generate code
            previousFilePath = allFiles
        )
        if (newNonIncrementalStore != null) {
            nonIncrementalStore.clear()
            nonIncrementalStore.putAll(newNonIncrementalStore)
        }
        // not sure why reset this?
        this.completionFromScope = emptyMap()
        return resolver
    }
    catch (s: OnCompletionException) {
        restoreLspState(previousLspState)
        this.resolver = Resolver.empty(otherFilesPaths = allFiles, ::onEachStatementCall, currentFile = mainFile)
        this.completionFromScope = s.scope
        if (s.token != null && s.errorMessage != null) {
            s.token.compileError(s.errorMessage)
        }
        return resolver
    }
    catch (e: Throwable) {
        restoreLspState(previousLspState)
        info?.invoke("LSP resolveAllFirstTime: error ${e::class.simpleName} ${e.message?.removeColors()}")
        this.resolver = Resolver.empty(otherFilesPaths = allFiles, ::onEachStatementCall, currentFile = mainFile)
        this.completionFromScope = emptyMap()
        throw e
    }

}


fun buildNonIncrementalStore(
    // main ast, other ast, otherFiles
    customAst: Triple<List<Statement>, List<Pair<String, List<Statement>>>, List<File>>,
    mainFile: File
): MutableMap<String, List<Statement>> {
    val (mainAst, pkgToAst, otherFiles) = customAst
    val store = mutableMapOf<String, List<Statement>>()
    store[mainFile.absolutePath] = mainAst

    // add othersAst
    pkgToAst.forEachIndexed { index, pair ->
        val file = otherFiles[index]
        store[file.absolutePath] = pair.second
    }
    return store
}

fun LS.fillNonIncrementalStore(
    // main ast, other ast, otherFiles
    customAst: Triple<List<Statement>, List<Pair<String, List<Statement>>>, List<File>>,
    mainFile: File
) {
    nonIncrementalStore.clear()
    nonIncrementalStore.putAll(buildNonIncrementalStore(customAst, mainFile))
//    fileToDecl[mainFile.absolutePath] = mutableSetOf(createFakeDeclaration())

}
