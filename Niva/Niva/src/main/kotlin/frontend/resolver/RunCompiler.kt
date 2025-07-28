package frontend.resolver

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import main.*
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*
import main.frontend.typer.resolveDeclarations
import main.frontend.typer.resolveDeclarationsOnly
import main.utils.*
import java.io.File
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.markNow

const val MAIN_PKG_NAME = "mainNiva"


private fun Resolver.fillFieldsWithResolvedTypes () {
    if (typeDB.unresolvedFields.isNotEmpty()) { // && i != 0
        val mapIterator = typeDB.unresolvedFields.iterator()
        while (mapIterator.hasNext()) {
            val (name, fieldSet) = mapIterator.next()

            val fieldIter = fieldSet.iterator()
            while (fieldIter.hasNext()) {
                val field = fieldIter.next()


                val resolvedFromDifferentFileType = getAnyType(
                    name,
                    mutableMapOf(),
                    mutableMapOf(),
                    null,
                    field.ast.token
                )

                if (resolvedFromDifferentFileType != null) {
                    val resolveAndRemoveField = { field2: FieldNameAndParent ->

                        val fieldToRemove = field2.parent.fields.first { it.name == field2.fieldName }
                        val ast = field2.parent.typeDeclaration!!.fields.first { it.name == fieldToRemove.name }.typeAST!!
                        val resolvedType = ast.toType(typeDB, typeTable)

                        // remove field with placeholder, and replace type to real type inside placeholder
                        // because we still need to generate correct types, and they are generated from Declarations(with placeholders in Fields)
                        // UPD not sure why before we were removing the fields, but looks like it works just with replacing the type only
                        field2.typeDeclaration.fields.first { it.name == field2.fieldName }.type = resolvedType
                        fieldToRemove.type = resolvedType

                        val unionRoot = field2.parent
                        if (unionRoot is Type.UnionRootType) {
                            unionRoot.branches.forEach { b ->
                                b.fields.find {it.name == field2.fieldName}?.let {
                                    it.type = resolvedType
                                }
                            }
                        }
                    }
                    resolveAndRemoveField(field)

                    // resolve types in branches, if this is union root with common field


                    fieldIter.remove()
                }
                if (fieldSet.isEmpty())
                    mapIterator.remove()
            }

        }
    }
}

fun Resolver.resolveWithBackTracking(
    mainAST: List<Statement>,
    otherASTs: List<Pair<String, List<Statement>>>,
    mainFilePath: String,
    mainFileNameWithoutExtension: String,
    verbosePrinter: VerbosePrinter,
) {

//    verbosePrinter.print {
//        "Files to compile: ${otherFilesPaths.count() + 1}\n\t${mainFilePath}" +
//                (if (otherFilesPaths.isNotEmpty()) "\n\t" else "") +
//                otherFilesPaths.joinToString("\n\t") { it.path }
//    }
    /// resolve all declarations
    statements = mainAST.toMutableList()


    // create main package
    val fakeTok = createFakeToken()
    changePackage(mainFileNameWithoutExtension, fakeTok, isMainFile = true)
    currentResolvingFileName = File(mainFilePath)



    val resolveDeclarationsOnlyMark = markNow()

    resolveDeclarationsOnly(statements)
    fillFieldsWithResolvedTypes()

    otherASTs.forEachIndexed { i, it ->
        currentResolvingFileName = otherFilesPaths[i]
        // create package
        changePackage(it.first, fakeTok)
        statements = it.second.toMutableList()
        resolveDeclarationsOnly(it.second)
        fillFieldsWithResolvedTypes()
    }


    verbosePrinter.print { "Resolving: declarations in ${resolveDeclarationsOnlyMark.getMs()} ms" }
    val resolveUnresolvedDeclarationsOnlyMark = markNow()


    /// verbosePrinter
    val isThereUnresolvedMsgDecls = unResolvedMessageDeclarations.isNotEmpty() || unResolvedTypeDeclarations.isNotEmpty()
    verbosePrinter.print {
        if (unResolvedMessageDeclarations.isNotEmpty()) {
            "Resolving: unresolved from first pass MessageDeclarations:\n\t${
                unResolvedMessageDeclarations.values.flatten().joinToString("\n\t") { it.name }}"
        } else ""
    }

    verbosePrinter.print {
        if (unResolvedTypeDeclarations.isNotEmpty()) {
            "Resolving: unresolved from first pass TypeDeclarations:\n\t${
                unResolvedTypeDeclarations.values.flatten().joinToString("\n\t") { it.typeName }}"
        } else ""
    }

    /// Resolving unresolved types N times, because in the worst scenario, each next can depend on previous
    val resolveUnresolvedTypes = {
        val iterator = unResolvedTypeDeclarations.iterator()
        while (iterator.hasNext()) {
            val (t, u) = iterator.next()
            changePackage(t, fakeTok)
            resolveDeclarationsOnly(u.toMutableList())
            if (u.isEmpty()) {
                iterator.remove()
            }
        }

    }

    var c = unResolvedTypeDeclarations.flatMap { it.value }.count()
    1
    while (c-- > 0 && unResolvedTypeDeclarations.isNotEmpty()) {
        resolveUnresolvedTypes()
    }

    fillFieldsWithResolvedTypes()

    if (typeDB.unresolvedFields.isNotEmpty()) {
        typeDB.unresolvedFields.values.first().first().typeDeclaration.token.compileError(buildString {
            append("These types remained unrecognized after checking all files: \n")
            typeDB.unresolvedFields.forEach { (a, g) ->
                g.forEach { b ->
                    // "| 11 name:  is unresolved"
                    append(b.ast.token.line, "| ")
                    append(
                        WHITE, b.fieldName, ": ", YEL, a, RESET, " in declaration of ", YEL, b.parent.pkg, ".",
                        b.parent.name, RESET, "\n"
                    )
                }

            }
        })
    }
    ///

    unResolvedTypeDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Unresolved type declarations: $unResolvedTypeDeclarations")
        }
    }

    // unresolved methods that contains unresolved types in args, receiver or return
    unResolvedMessageDeclarations.forEach { (pkgName, unresolvedDecl) ->
        changePackage(pkgName, fakeTok)
        resolveDeclarationsOnly(unresolvedDecl.toMutableList())
    }
    // second time resolve single expressions, to add them to DB
    unResolvedSingleExprMessageDeclarations.forEach { (pkgName, unresolvedDecl) ->
        changePackage(pkgName, fakeTok)
        unresolvedDecl.forEach {
            val (protocol, it) = it
            changeProtocol(protocol)
            resolveDeclarations(it, mutableMapOf(), resolveBody = true)
        }
    }
    unResolvedSingleExprMessageDeclarations.clear()

    unResolvedMessageDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            // find out what exactly unresolved, receiver, one of the args, or return type
            if (decl.forType == null) {
                (decl.forTypeAst.token).compileError("Can't find the receiver type $YEL${decl.forTypeAst}$RESET of the method declaration: `${CYAN}${decl}${RESET}`")
            }
            if (decl.returnType == null) {
                (decl.returnTypeAST?.token ?: decl.token).compileError("Can't find the return type $YEL${decl.returnTypeAST ?: ""}$RESET of the method declaration: `${CYAN}${decl}${RESET}`")
            }
            if (decl is MessageDeclarationKeyword) {
                decl.args.find { it.type == null }?.also {
                    it.tok.compileError("Can't find the arg's type $YEL${it.typeAST ?: ""}$RESET of the method declaration: `${CYAN}${decl}${RESET}`")
                }
            }

            decl.token.compileError("Method `${CYAN}${decl}${RESET}` for unresolved type: `${YEL}${decl.forTypeAst.name}${RESET}`")
        }
    }
    unResolvedMessageDeclarations.clear()

    unresolvedDocComments.forEach { b ->
        resolveSingle(b, mutableMapOf())
    }
    unresolvedDocComments.clear()
    /// end of resolve all declarations

    verbosePrinter.print {
        if (isThereUnresolvedMsgDecls)
            "Resolving: all unresolved declarations resolved in ${resolveUnresolvedDeclarationsOnlyMark.getMs()} ms"
        else ""
    }

    allDeclarationResolvedAlready = true


    // here we are resolving all statements(in bodies), not only declarations

    currentPackageName = mainFileNameWithoutExtension

    val resolveExpressionsMark = markNow()

    resolvingMainFile = true
    resolve(mainAST, mutableMapOf())// createArgsFromMain()
    resolvingMainFile = false

    otherASTs.forEach {
        currentPackageName = it.first
        statements = it.second.toMutableList()
        resolve(it.second, mutableMapOf())
    }

    verbosePrinter.print { "Resolving: expressions in ${resolveExpressionsMark.getMs()} ms" }
    verbosePrinter.print { "Resolving took: ${resolveDeclarationsOnlyMark.getMs()} ms" }

    // need to add all imports from mainFile pkg to mainNiva pkg
    val currentProject = projects[currentProjectName]!!
    val mainNivaPkg = currentProject.packages[MAIN_PKG_NAME]!!
    val mainFilePkg = currentProject.packages[mainFileNameWithoutExtension]!!
    mainNivaPkg.imports += mainFilePkg.imports
    mainNivaPkg.concreteImports += mainFilePkg.concreteImports
}


fun Resolver.printInfoFromCode() {
    infoTypesToPrint.forEach { x, y ->
        if (y) {
            println(x)
        } else {
            val content = x.infoPrint()
            println(content)
        }
    }
}

/// create `args` variable on top level of type List::String
//fun Resolver.createArgsFromMain(): MutableMap<String, Type> {
//    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
//    val listType = this.typeDB.userTypes["List"]!!.first()
//    val listOfString = createTypeListOfType("List", stringType, listType as Type.UserType, "List")
//    return mutableMapOf("args" to listOfString)
//}

fun TimeSource.Monotonic.ValueTimeMark.getMs() = this.elapsedNow().inWholeMilliseconds.toString()


fun getAst(source: String, file: File): List<Statement> {
    val tokens = lex(source, file)
    val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
    val ast = parser.statements()
    return ast
}

// third param is list of file paths
@OptIn(DelicateCoroutinesApi::class)
fun parseFilesToAST(
    mainFileContent: String,
    otherFileContents: List<File>,
    mainFilePath: String,
    resolveOnlyOneFile: Boolean,
    pathToChangedFile: File? = null,
    changedFileContent: String? = null,
    verbosePrinter: VerbosePrinter? = null,
): Triple<List<Statement>, List<Pair<String, List<Statement>>>, List<File>> {
    val mainAST = getAst(source = mainFileContent, file = File(mainFilePath))
    val listOfPaths = mutableListOf<File>()
    // generate ast for others
    // in lsp mode we change one file at a time

    // Virtual threads from java 21
//    val otherASTs = if (!resolveOnlyOneFile) {
//        listOfPaths.addAll(otherFileContents)
//
//        val markBeforeAsync = markNow()
//
//        val executor = Executors.newVirtualThreadPerTaskExecutor() // Java 21 API
//
//        try {
//            val futures: List<Future<Pair<String, List<Statement>>>> = otherFileContents.map { file ->
//                executor.submit<Pair<String, List<Statement>>> {
//                    val localMark = markNow()
//                    verbosePrinter?.print { GREEN + "${markBeforeAsync.getMs()} ms Parsing file ${file.name}: Started" + RESET }
//
//                    val src =
//                        if (changedFileContent != null && pathToChangedFile != null &&
//                            file.absolutePath == pathToChangedFile.absolutePath)
//                            changedFileContent
//                        else
//                            Files.readString(file.toPath()) // More idiomatic for Java interop
//
//                    verbosePrinter?.print { "${localMark.getMs()} ms Parsing file ${file.name}: alone" + RESET }
//                    verbosePrinter?.print { RED + "${markBeforeAsync.getMs()} ms Parsing file ${file.name}: ended" + RESET }
//
//                    file.nameWithoutExtension to getAst(src, file)
//                }
//            }
//
//            verbosePrinter?.print { "Starting to wait for all ${markBeforeAsync.getMs()} ms" }
//
//            val results = futures.map { it.get() } // Blocks, but runs concurrently thanks to virtual threads
//
//            verbosePrinter?.print { "Ended to wait for all ${markBeforeAsync.getMs()} ms" }
//
//            results
//        } finally {
//            executor.shutdown()
//        }
//    } else {
//        emptyList()
//    }

    // single thread solution
//    val otherASTs = if (!resolveOnlyOneFile) {
//        otherFileContents.map {
//            val src =
//                if (changedFileContent != null && pathToChangedFile != null && it.absolutePath == pathToChangedFile.absolutePath)
//                    changedFileContent
//                else
//                    it.readText()
//            listOfPaths.add(it)
//            it.nameWithoutExtension to getAst(source = src, file = it)
//        }
//    } else emptyList()

    // Coroutines
    val otherASTs = if (!resolveOnlyOneFile) {
        listOfPaths.addAll(otherFileContents)
        // parallel on 4 threads
        val dispatcher = newFixedThreadPoolContext(4, "fileReaderPool")
        runBlocking {
            withContext(dispatcher) {
                val markBeforeAsync = markNow()
                val deferredAsts = otherFileContents.map { file ->

                    async(dispatcher) {
                        val localMark = markNow()
                        verbosePrinter?.print { GREEN + "${markBeforeAsync.getMs()} ms Parsing file ${file.name}: Started at " + RESET }

                        val src =
                            if (changedFileContent != null && pathToChangedFile != null && file.absolutePath == pathToChangedFile.absolutePath)
                                changedFileContent
                            else
                                file.readText()

                        verbosePrinter?.print { "${localMark.getMs()} ms Parsing file ${file.name}: alone "+ RESET }
                        verbosePrinter?.print { RED + "${markBeforeAsync.getMs()} ms Parsing file ${file.name}: ended at" + RESET}
                        file.nameWithoutExtension to getAst(source = src, file = file)
                    }
                }

                verbosePrinter?.print { "Starting to wait for all ${markBeforeAsync.getMs()} ms" }
                val x = deferredAsts.awaitAll()
                verbosePrinter?.print { "Ended to wait for all ${markBeforeAsync.getMs()} ms" }
                x
            }
        }
    } else {
        emptyList()
    }
    return Triple(mainAST, otherASTs, listOfPaths)
}
