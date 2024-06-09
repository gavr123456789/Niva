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
import kotlin.io.path.absolute
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.markNow

const val MAIN_PKG_NAME = "mainNiva"

fun Resolver.createArgsFromMain(): MutableMap<String, Type> {
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
    val listType = this.typeDB.userTypes["List"]!!.first()
    val listOfString = createTypeListOfType("List", stringType, listType as Type.UserType)
    return mutableMapOf("args" to listOfString)

}

fun TimeSource.Monotonic.ValueTimeMark.getMs() = this.elapsedNow().inWholeMilliseconds.toString()


fun Resolver.resolve(
    mainFile: File,
    verbosePrinter: VerbosePrinter,
    resolveOnlyOneFile: Boolean = false,
    customMainSource: String? = null,
) {

    fun getAst(source: String, file: File): List<Statement> {
        val tokens = lex(source, file)
        val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
        val ast = parser.statements()
        return ast
    }

    val fakeTok = createFakeToken()
    verbosePrinter.print {
        "Files to compile: ${otherFilesPaths.count() + 1}\n\t${mainFile.toPath().absolute()}" +
                (if (otherFilesPaths.isNotEmpty()) "\n\t" else "") +
                otherFilesPaths.joinToString("\n\t") { it.path }
    }


    val beforeParserMark = markNow()

    val mainAST = getAst(source = customMainSource ?: mainFile.readText(), file = mainFile)


    // generate ast for others
    // in lsp mode we change one file at a time
    val otherASTs = if (!resolveOnlyOneFile)
        otherFilesPaths.map {
            val src = it.readText()
            it.nameWithoutExtension to getAst(source = src, file = it)
        }
    else listOf()

    verbosePrinter.print { "Parsing: ${beforeParserMark.getMs()} ms" }
    /// resolve all declarations
    statements = mainAST.toMutableList()


    // create main package
    changePackage(mainFile.nameWithoutExtension, fakeTok, isMainFile = true)
    currentResolvingFileName = mainFile

    val resolveUnresolved = {
        if (typeDB.unresolvedTypes.isNotEmpty()) { // && i != 0
            val iterator = typeDB.unresolvedTypes.iterator()
            while (iterator.hasNext()) {
                val (a, b) = iterator.next()

                val resolvedFromDifferentFileType = getAnyType(a, mutableMapOf(), mutableMapOf(), null)
                if (resolvedFromDifferentFileType != null) {
                    val fieldToRemove = b.parent.fields.first { it.name == b.fieldName }


                    val ast = b.ast
                    val realType = if (ast != null) {
                        ast.toType(typeDB, typeTable)
                    } else resolvedFromDifferentFileType
                    // remove field with placeholder, and replace type to real type inside placeholder
                    // because we still need to generate correct types, and they are generated from Declarations(with placeholders in Fields)

                    b.typeDeclaration.fields.first { it.name == b.fieldName }.type = realType
                    b.parent.fields.remove(fieldToRemove)

                    b.parent.fields.add(
                        KeywordArg(
                            name = b.fieldName,
                            type = realType
                        )
                    )
                    iterator.remove()
                }
            }
        }
    }

    val resolveDeclarationsOnlyMark = markNow()

    resolveDeclarationsOnly(mainAST)
    resolveUnresolved()
    otherASTs.forEachIndexed { i, it ->
        currentResolvingFileName = otherFilesPaths[i]
        // create package
        changePackage(it.first, fakeTok)
        statements = it.second.toMutableList()
        resolveDeclarationsOnly(it.second)
        resolveUnresolved()
    }

    verbosePrinter.print { "Resolving: declarations in ${resolveDeclarationsOnlyMark.getMs()} ms" }

    val resolveUnresolvedDeclarationsOnlyMark = markNow()

    if (typeDB.unresolvedTypes.isNotEmpty()) {

        typeDB.unresolvedTypes.values.first().typeDeclaration.token.compileError(buildString {
            append("These types remained unrecognized after checking all files: \n")
            typeDB.unresolvedTypes.forEach { (a, b) ->
                // "| 11 name:  is unresolved"
                append(b.ast?.token?.line ?: "", "| ")
                append(
                    WHITE, b.fieldName, ": ", YEL, a, RESET, " in declaration of ", YEL, b.parent.pkg, ".",
                    b.parent.name, RESET, "\n"
                )
            }
        })
    }

    val isThereUnresolvedDecls = unResolvedMessageDeclarations.isNotEmpty() || unResolvedTypeDeclarations.isNotEmpty()
    verbosePrinter.print {
        if (unResolvedMessageDeclarations.isNotEmpty()) {
            "Resolving: unresolved from first pass MessageDeclarations:\n\t${
                unResolvedMessageDeclarations.values.flatten().joinToString("\n\t") { it.name }
            }"
        } else ""
    }

    verbosePrinter.print {
        if (unResolvedTypeDeclarations.isNotEmpty()) {
            "Resolving: unresolved from first pass TypeDeclarations:\n\t${
                unResolvedTypeDeclarations.values.flatten().joinToString("\n\t") { it.typeName }
            }"
        } else ""
    }

    // unresolved types
    unResolvedTypeDeclarations.forEach { (t, u) ->
        changePackage(t, fakeTok)
        resolveDeclarationsOnly(u.toMutableList())
    }
    // TODO replace to iter and remove inside, if there is type
    unResolvedTypeDeclarations.clear()
    unResolvedTypeDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Type `${YEL}${decl}${RESET}` for unresolved type: `${YEL}${decl.typeName}${RESET}`")
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


    /// end of resolve all declarations

    verbosePrinter.print {
        if (isThereUnresolvedDecls)
            "Resolving: all unresolved declarations resolved in ${resolveUnresolvedDeclarationsOnlyMark.getMs()} ms"
        else ""
    }

    allDeclarationResolvedAlready = true


    // here we are resolving all statements(in bodies), not only declarations

    currentPackageName = mainFile.nameWithoutExtension

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
    val mainFilePkg = currentProject.packages[mainFile.nameWithoutExtension]!!
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
