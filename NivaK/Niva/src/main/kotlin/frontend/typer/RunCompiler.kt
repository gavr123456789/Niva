package frontend.typer

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.Statement
import lex
import java.io.File

fun Resolver.run() {
    fun getAst(source: String, file: File): List<Statement> {
        val tokens = lex(source, file)
        val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
        val ast = parser.statements()
        return ast
    }


    // generate ast for main file with filling topLevelStatements
    // 1) read content of mainFilePath
    // 2) generate ast
    val mainSourse = mainFile.readText()
    val mainAST = getAst(source = mainSourse, file = mainFile)
    // generate ast for others
    val otherASTs = otherFilesPaths.map {
        val src = it.readText()
        getAst(source = src, file = it)
    }

    ////resolve all the AST////
    statements = mainAST.toMutableList()

    // resolve types
    resolveDeclarationsOnly(mainAST)
    otherASTs.forEach {
        statements = it.toMutableList()
        resolveDeclarationsOnly(it)
    }

    if (unResolvedMessageDeclarations.isNotEmpty()) {
        resolveDeclarationsOnly(unResolvedMessageDeclarations.toMutableList())
    }
    if (unResolvedMessageDeclarations.isNotEmpty()) {
        throw Exception("Not all message declarations resolved: $unResolvedMessageDeclarations")
    }
    if (unResolvedTypeDeclarations.isNotEmpty()) {
        resolveDeclarationsOnly(unResolvedTypeDeclarations.toMutableList())

    }


    allDeclarationResolvedAlready = true


    resolve(mainAST, mutableMapOf())

    otherASTs.forEach {
        statements = it.toMutableList()
        resolve(it, mutableMapOf())
    }

}
