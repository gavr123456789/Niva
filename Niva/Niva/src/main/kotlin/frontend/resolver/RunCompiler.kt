package frontend.resolver

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
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
                        val ast = field2.ast
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
//fun Resolver.resolveByUnresolvedFields(otherASTs: List<Pair<String, List<Statement>>>, fakeTok: Token) {
//    fillFieldsWithResolvedTypes()
//    otherASTs.forEachIndexed { i, it ->
//        currentResolvingFileName = otherFilesPaths[i]
//        // create package
//        changePackage(it.first, fakeTok)
//        statements = it.second.toMutableList()
//        resolveDeclarationsOnly(it.second)
//        fillFieldsWithResolvedTypes()
//    }
//}

fun Resolver.resolveWithBackTracking(
    mainAST: List<Statement>,
    otherASTs: List<Pair<String, List<Statement>>>,
    mainFilePath: String,
    mainFileNameWithoutExtension: String,
    verbosePrinter: VerbosePrinter,
) {

    verbosePrinter.print {
        "Files to compile: ${otherFilesPaths.count() + 1}\n\t${mainFilePath}" +
                (if (otherFilesPaths.isNotEmpty()) "\n\t" else "") +
                otherFilesPaths.joinToString("\n\t") { it.path }
    }


    val beforeParserMark = markNow()

    verbosePrinter.print { "Parsing: ${beforeParserMark.getMs()} ms" }
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
        unResolvedTypeDeclarations.forEach { (t, u) ->
            changePackage(t, fakeTok)
            resolveDeclarationsOnly(u.toMutableList())
        }
    }
    var c = unResolvedTypeDeclarations.flatMap { it.value }.count()
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
    resolve(mainAST, createArgsFromMain())
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

fun Resolver.createArgsFromMain(): MutableMap<String, Type> {
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
    val listType = this.typeDB.userTypes["List"]!!.first()
    val listOfString = createTypeListOfType("List", stringType, listType as Type.UserType)
    return mutableMapOf("args" to listOfString)
}

fun TimeSource.Monotonic.ValueTimeMark.getMs() = this.elapsedNow().inWholeMilliseconds.toString()


fun getAst(source: String, file: File): List<Statement> {
    val tokens = lex(source, file)
    val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
    val ast = parser.statements()
    return ast
}

// third param is list of file paths
fun getAstFromFiles(
    mainFileContent: String,
    otherFileContents: List<File>,
    mainFilePath: String,
    resolveOnlyOneFile: Boolean
): Triple<List<Statement>, List<Pair<String, List<Statement>>>, List<File>> {
    val mainAST = getAst(source = mainFileContent, file = File(mainFilePath))
    val listOfPaths = mutableListOf<File>()
    // generate ast for others
    // in lsp mode we change one file at a time
    val otherASTs = if (!resolveOnlyOneFile)
        otherFileContents.map {
            val src = it.readText()
            listOfPaths.add(it)
            it.nameWithoutExtension to getAst(source = src, file = it)
        }
    else emptyList()
    return Triple(mainAST, otherASTs, listOfPaths)
}
