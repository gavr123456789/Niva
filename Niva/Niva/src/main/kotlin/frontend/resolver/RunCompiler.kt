package frontend.resolver

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import main.*
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.typer.resolveDeclarations
import main.frontend.typer.resolveDeclarationsOnly
import main.frontend.util.createFakeToken
import main.utils.infoPrint
import java.io.File

const val MAIN_PKG_NAME = "mainNiva"

fun Resolver.resolve(mainFile: File) {
    fun getAst(source: String, file: File): List<Statement> {
        val tokens = lex(source, file)
        val parser = Parser(file = file, tokens = tokens, source = "sas.niva")
        val ast = parser.statements()
        return ast
    }
    val fakeTok = createFakeToken()

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
    changePackage(mainFile.nameWithoutExtension, fakeTok, isMainFile = true)


    resolveDeclarationsOnly(mainAST)
    otherASTs.forEach {
        // create package
        changePackage(it.first, fakeTok)
        statements = it.second.toMutableList()
        resolveDeclarationsOnly(it.second)
    }

    // unresolved methods that contains unresolved types in args, receiver or return
    unResolvedMessageDeclarations.forEach { (pkgName, unresolvedDecl) ->
        changePackage(pkgName, fakeTok)
        resolveDeclarationsOnly(unresolvedDecl.toMutableList())
    }
    // second time resolve single expressions
    unResolvedSingleExprMessageDeclarations.forEach { (pkgName, unresolvedDecl) ->
        changePackage(pkgName, fakeTok)
        unresolvedDecl.forEach {
            resolveDeclarations(it, mutableMapOf(), resolveBody = true)
        }
    }
    unResolvedMessageDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Method `$CYAN${decl}$RESET` for unresolved type: `$YEL${decl.forTypeAst.name}$RESET`")
        }
    }
    unResolvedMessageDeclarations.clear()

    // unresolved types
    unResolvedTypeDeclarations.forEach { (t, u) ->
        changePackage(t, fakeTok)
        resolveDeclarationsOnly(u.toMutableList())
    }
    unResolvedTypeDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Type `${YEL}${decl}$RESET` for unresolved type: `${YEL}${decl.typeName}$RESET`")
        }
    }
    unResolvedTypeDeclarations.clear()

    /// end of resolve all declarations

    allDeclarationResolvedAlready = true

    // here we are resolving all statements(in bodies), not only declarations

    currentPackageName = mainFile.nameWithoutExtension

    // main args
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
    val listType = this.typeDB.userTypes["List"]!!.first()
    val listOfString = createTypeListOfType("List", stringType, listType as Type.UserType)
    val mainArgs = "args" to listOfString
    resolve(mainAST, mutableMapOf(mainArgs))

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
