package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.resolver.*
import frontend.util.createFakeToken
import frontend.util.removeDoubleQuotes
import main.RED
import main.RESET
import main.WHITE
import main.YEL
import main.frontend.resolver.resolveStaticBuilderDeclaration
import main.frontend.typer.project.resolveProjectKeyMessage


fun Resolver.resolveDeclarations(
    statement: Declaration,
    previousScope: MutableMap<String, Type>,
    resolveBody: Boolean = true,
) {
    currentLevel += 1
    when (statement) {
        is TypeDeclaration -> {
            val newType = statement.toType(currentPackageName, typeTable, typeDB)// fixed
            addNewType(newType, statement)
        }

        is MessageDeclaration -> {
            if (resolveMessageDeclaration(statement, resolveBody, previousScope)) return
        }
        is StaticBuilderDeclaration -> resolveStaticBuilderDeclaration(statement, resolveBody, previousScope)

        is ExtendDeclaration -> {
            var atLeastOneUnresolved = false
            statement.messageDeclarations.forEach {
                val cantBeResolve = resolveMessageDeclaration(it, resolveBody, previousScope)
                if (!atLeastOneUnresolved && cantBeResolve) atLeastOneUnresolved = true
            }
            if (atLeastOneUnresolved) return
        }

        is UnionDeclaration -> {
            resolveUnionDeclaration(statement, previousScope)
        }

        is EnumDeclarationRoot -> {
            resolveEnumDeclaration(statement, previousScope)
        }

        is EnumBranch -> TODO()
        is AliasDeclaration -> TODO()


        is UnionBranch -> {
            println("Union branch???")
        }


    }
    currentLevel -= 1
}

fun Resolver.resolveDeclarationsOnly(statements: List<Statement>) {
    statements.forEach {
        if (it is Declaration) {
            resolveDeclarations(it, mutableMapOf(), resolveBody = false)
        }
        // special messages like Project package: ""
        if (it is MessageSendKeyword) {
            when (it.receiver.str) {
                "Project" ->
                    resolveProjectKeyMessage(it)

                "Bind" -> {
                    val savedPackageName = currentPackageName

                    val msg = it.messages[0]
                    if (msg !is KeywordMsg)
                        it.token.compileError("${YEL}Bind$RESET must have keyword message")
                    if (msg.args.count() < 2)
                        it.token.compileError("${YEL}Bind$RESET must have at least 2 argument: package and content")
                    val pkgArg = msg.args.find { x -> x.name == "package" }
                    if (pkgArg == null)
                        msg.token.compileError("${WHITE}package$RED param is missing")
                    val contentArg = msg.args.find {x -> x.name == "content" }
                    if (contentArg == null)
                        msg.token.compileError("${WHITE}content$RED param is missing")



                    if (pkgArg.keywordArg !is LiteralExpression)
                        pkgArg.keywordArg.token.compileError("Package argument must be a string")
                    if (contentArg.keywordArg !is CodeBlock)
                        contentArg.keywordArg.token.compileError("Content argument must be a code block with type and method declarations")

                    val importsArg = msg.args.find { x -> x.name == "imports" }
                    val neededImports =
                    if (importsArg != null) {
                        if (importsArg.keywordArg !is ListCollection) {
                            importsArg.keywordArg.token.compileError("Imports argument must be ${YEL}List::String")
                        }
                         importsArg.keywordArg.initElements.map { it.token.lexeme.removeDoubleQuotes() }.toMutableSet()
                    } else null

                    val pkgName = pkgArg.keywordArg.toString()
                    if (savedPackageName == pkgName) {
                        it.token.compileError("Package $savedPackageName already exists, it is proposed to rename it to ${savedPackageName}.bind.niva")
                    }
                    changePackage(pkgName, it.token, true, neededImports = neededImports)
                    val declarations = contentArg.keywordArg.statements
                    declarations.forEach { decl ->
                        if (decl is Declaration) {
                            resolveDeclarations(decl, mutableMapOf(), resolveBody = false)
                        } else {
                            decl.token.compileError("There can be only declarations inside Bind, but found $WHITE$decl")
                        }
                    }


                    val gettersArg = msg.args.find { it.name == "getters" }
                    if (gettersArg != null) {
                        if (gettersArg.keywordArg !is CodeBlock)
                            gettersArg.keywordArg.token.compileError("Getter argument must be a code block with type and method declarations")
                        val gettersDeclarations = gettersArg.keywordArg.statements
                        gettersDeclarations.forEach { getter ->
                            if (getter !is MessageDeclarationUnary) {
                                getter.token.compileError("Unary declaration expected inside getters block")
                            } else {
                                resolveMessageDeclaration(getter, false, mutableMapOf())
                            }
                            addNewUnaryMessage(getter, isGetter = true)

                        }
                    }

                    changePackage(savedPackageName, createFakeToken())
                }
            }
        }

    }
//    changePackage(savedPackageName, createFakeToken())
}
