package frontend.typer

import frontend.meta.compileError
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.Statement
import lex
import java.io.File

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
        getAst(source = src, file = it)
    }

    /// resolve all declarations
    statements = mainAST.toMutableList()

    resolveDeclarationsOnly(mainAST)
    otherASTs.forEach {
        statements = it.toMutableList()
        resolveDeclarationsOnly(it)
    }

    if (unResolvedMessageDeclarations.isNotEmpty()) {
        resolveDeclarationsOnly(unResolvedMessageDeclarations.toMutableList())
    }
    if (unResolvedMessageDeclarations.isNotEmpty()) {
        unResolvedMessageDeclarations.forEach {
            it.token.compileError("Method `$it` for unresolved type: `${it.forType.name}`")
        }
        throw Exception("Not all message declarations resolved: $unResolvedMessageDeclarations")
    }

    if (unResolvedTypeDeclarations.isNotEmpty()) {
        resolveDeclarationsOnly(unResolvedTypeDeclarations.toMutableList())
    }
    if (unResolvedTypeDeclarations.isNotEmpty()) {
        unResolvedTypeDeclarations.forEach {
            it.token.compileError("Unresolved type: $it")
        }
        throw Exception("Not all type declarations resolved: $unResolvedTypeDeclarations")
    }
    /// end of resolve all declarations

    allDeclarationResolvedAlready = true


    currentPackageName = "main"
    resolve(mainAST, mutableMapOf())
    currentPackageName = "common"
    otherASTs.forEach {
        statements = it.toMutableList()
        resolve(it, mutableMapOf())
    }

}
