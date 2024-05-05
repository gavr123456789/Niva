package frontend.resolver

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import main.*
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*
import main.frontend.typer.resolveDeclarations
import main.frontend.typer.resolveDeclarationsOnly
import main.utils.CYAN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.infoPrint
import java.io.File

const val MAIN_PKG_NAME = "mainNiva"

fun Resolver.createArgsFromMain(): MutableMap<String, Type> {
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
    val listType = this.typeDB.userTypes["List"]!!.first()
    val listOfString = createTypeListOfType("List", stringType, listType as Type.UserType)
    return mutableMapOf("args" to listOfString)

}

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


    val resolveUnresolved = {
        if (typeDB.unresolvedTypes.isNotEmpty() ) { // && i != 0
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
//                    fieldToRemove.type = realType

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

    resolveDeclarationsOnly(mainAST)
    resolveUnresolved()
    otherASTs.forEachIndexed { i, it ->
        // create package
        changePackage(it.first, fakeTok)
        statements = it.second.toMutableList()
        resolveDeclarationsOnly(it.second)
        resolveUnresolved()
    }

    if (typeDB.unresolvedTypes.isNotEmpty()) {

        fakeTok.compileError(buildString {
            append("These types remained unrecognized after checking all files: \n")
            typeDB.unresolvedTypes.forEach { (a, b) ->
                // "| 11 name:  is unresolved"
                append("| ", b.ast?.token?.line ?: "")
                append(WHITE, b.fieldName, ": ", YEL, a, RESET, " in declaration of ", YEL, b.parent.pkg, ".", b.parent.name, RESET, "\n")
            }
        })
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
    unResolvedMessageDeclarations.forEach { (_, u) ->
        if (u.isNotEmpty()) {
            val decl = u.first()
            decl.token.compileError("Method `${CYAN}${decl}${RESET}` for unresolved type: `${YEL}${decl.forTypeAst.name}${RESET}`")
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
            decl.token.compileError("Type `${YEL}${decl}${RESET}` for unresolved type: `${YEL}${decl.typeName}${RESET}`")
        }
    }
    unResolvedTypeDeclarations.clear()

    /// end of resolve all declarations

    allDeclarationResolvedAlready = true

    // here we are resolving all statements(in bodies), not only declarations

    currentPackageName = mainFile.nameWithoutExtension

    // main args
    resolvingMainFile = true
    resolve(mainAST, createArgsFromMain())
    resolvingMainFile = false

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
