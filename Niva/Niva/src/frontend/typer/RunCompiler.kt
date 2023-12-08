package frontend.typer

import frontend.meta.compileError
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.Statement
import frontend.util.createFakeToken
import main.frontend.typer.resolveDeclarationsOnly
import main.lex
import java.io.File

const val MAIN_PKG_NAME = "mainNiva"

fun Resolver.resolve() {
    fun getAst(source: String, file: File): List<Statement> {
        val tokens = lex(source, file)
        val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
        val ast = parser.statements()
        return ast
    }


    // generate ast for main file with filling topLevelStatements
    // 1) read content of mainFilePath
    // 2) generate ast
    val mainSource = mainFile.readText()
    val mainAST = getAst(source = mainSource, file = mainFile)
    // generate ast for others
    val otherASTs = otherFilesPaths.map {
        val src = it.readText()
        it.nameWithoutExtension to getAst(source = src, file = it)
    }

    /// resolve all declarations
    statements = mainAST.toMutableList()


    // create main package
    changePackage(mainFile.nameWithoutExtension, createFakeToken(), isMainFile = true)


    resolveDeclarationsOnly(mainAST)
    otherASTs.forEach {
        // create package
        changePackage(it.first, createFakeToken())
        statements = it.second.toMutableList()
        resolveDeclarationsOnly(it.second)
    }

    // unresolved methods
    unResolvedMessageDeclarations.forEach { (t, u) ->
        changePackage(t, createFakeToken())
        resolveDeclarationsOnly(u.toMutableList())
    }
    unResolvedMessageDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Method `${decl}` for unresolved type: `${decl.forTypeAst.name}`")
        }
    }
    unResolvedMessageDeclarations.clear()

    // unresolved types
    unResolvedTypeDeclarations.forEach { (t, u) ->
        changePackage(t, createFakeToken())
        resolveDeclarationsOnly(u.toMutableList())
    }
    unResolvedTypeDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Type `${decl}` for unresolved type: `${decl.typeName}`")
        }
    }
    unResolvedTypeDeclarations.clear()

    /// end of resolve all declarations

    allDeclarationResolvedAlready = true


    currentPackageName = mainFile.nameWithoutExtension
    resolve(mainAST, mutableMapOf())
//    currentPackageName = "common"
    otherASTs.forEach {
        currentPackageName = it.first
        statements = it.second.toMutableList()
        resolve(it.second, mutableMapOf())
    }

    // need to add all imports from mainFile pkg to mainNiva pkg
    val currentProject = projects[currentProjectName]!!
    val mainNivaPkg = currentProject.packages[MAIN_PKG_NAME]!!
    val mainFilePkg = currentProject.packages[mainFile.nameWithoutExtension]!!
    mainNivaPkg.imports += mainFilePkg.imports
    mainNivaPkg.concreteImports += mainFilePkg.concreteImports

}
