package main.frontend.typer

import frontend.resolver.*
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*
import main.frontend.typer.project.resolveProjectKeyMessage
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.removeDoubleQuotes

// returns true if resolve failed to resolve(statement added to list of unresolved)
fun Resolver.resolveDeclarations(
    statement: Declaration,
    previousScope: MutableMap<String, Type>,
    resolveBody: Boolean = true,
): Boolean {
    currentLevel += 1
    val dynamicType = this.getAnyType("Dynamic", mutableMapOf(), mutableMapOf(), null, statement.token) ?: statement.token.compileError("Failed to find dynamic type(its internal type, always presented)")

    when (statement) {
        is TypeDeclaration -> {
            val newType = statement.toType(currentPackageName, typeTable, typeDB)// fixed
//            if (newType is Type.UserLike) {
                val dynamicProtocol = createDynamicProtocol(newType, dynamicType = dynamicType)
                newType.protocols["dynamic"] = dynamicProtocol
//            }

            addNewType(newType, statement)
        }

        is MessageDeclaration -> {
            if (resolveMessageDeclaration(statement, resolveBody, previousScope)) return true
        }

//        is StaticBuilderDeclaration ->
//            resolveStaticBuilderDeclaration(statement, resolveBody, previousScope)

        is ExtendDeclaration -> {
            var atLeastOneUnresolved = false
            statement.messageDeclarations.forEach {

                val cantBeResolve = resolveMessageDeclaration(it, resolveBody, previousScope)

                if (cantBeResolve) currentLevel++
                if (!atLeastOneUnresolved && cantBeResolve) atLeastOneUnresolved = true
            }
            if (atLeastOneUnresolved) {
                currentLevel--
                return true
            }
        }
        is ManyConstructorDecl -> {
            var atLeastOneUnresolved = false
            statement.messageDeclarations.forEach {
                val cantBeResolve = resolveMessageDeclaration(it, resolveBody, previousScope)

                if (cantBeResolve) currentLevel++
                if (!atLeastOneUnresolved && cantBeResolve) atLeastOneUnresolved = true
            }
            if (atLeastOneUnresolved) {
                currentLevel--
                return true
            }
        }

        is UnionRootDeclaration -> {
            resolveUnionDeclaration(statement, isError = false, dynamicType)
        }

        is EnumDeclarationRoot -> {
            resolveEnumDeclaration(statement, previousScope)
        }

        is EnumBranch -> TODO()

        is TypeAliasDeclaration -> {
            resolveTypeAlias(statement)
        }

        is ErrorDomainDeclaration -> {
            resolveUnionDeclaration(statement.unionDeclaration, true, dynamicType)
            statement.receiver = statement.unionDeclaration.receiver
        }

        is UnionBranchDeclaration -> {
            // strange, we parse branches when we parse roots
        }

    }
    currentLevel -= 1
    return false
}

fun Resolver.resolveDeclarationsOnly(statements: List<Statement>) {
    statements.forEach {
        if (it is Declaration) {
            val x = mutableMapOf<String, Type>()
            val resolveFailed = resolveDeclarations(it, x, resolveBody = false)

            if (onEachStatement != null && !resolveFailed) { // its true only in LSP mode
                onEachStatement(it, x, mutableMapOf(), it.token.file) // this.currentResolvingFileName
            }
            // remember doc comments to resolve references from them later
            val docComment = it.docComment
            if (docComment != null && docComment.identifiers != null) {
                unresolvedDocComments.addAll(docComment.identifiers)
            }
        }
        // special messages like Project package: ""
        if (it is MessageSendKeyword) {
            when (it.receiver.str) {
                "Project" -> resolveProjectKeyMessage(it)
                "Bind" -> {
                    val savedPackageName = currentPackageName

                    val msg = it.messages[0]
                    if (msg !is KeywordMsg)
                        it.token.compileError("${YEL}Bind$RESET must have keyword message")
                    if (msg.args.count() < 2)
                        it.token.compileError("${YEL}Bind$RESET must have at least 2 argument: package: \"name\" and content: [declarations]")
                    val pkgArg = msg.args.find { x -> x.name == "package" }
                    if (pkgArg == null)
                        msg.token.compileError("${WHITE}package$RED param is missing")
                    val contentArg = msg.args.find { x -> x.name == "content" }
                    if (contentArg == null)
                        msg.token.compileError("${WHITE}content$RED param is missing")



                    if (pkgArg.keywordArg !is LiteralExpression)
                        pkgArg.keywordArg.token.compileError("Package argument must be a String")
                    if (contentArg.keywordArg !is CodeBlock)
                        contentArg.keywordArg.token.compileError("Content argument must be a code block with type and method declarations")

                    // imports
                    val importsArg = msg.args.find { x -> x.name == "imports" }
                    val neededImports =
                        if (importsArg != null) {
                            if (importsArg.keywordArg !is ListCollection) {
                                importsArg.keywordArg.token.compileError("Imports argument must be ${YEL}List::String")
                            }
                            importsArg.keywordArg.initElements.map { it.token.lexeme.removeDoubleQuotes() }
                                .toMutableSet()
                        } else null

                    // plugins
                    val pluginsArg = msg.args.find { x -> x.name == "plugins" }
                    val neededPlugins =
                        if (pluginsArg != null) {
                            if (pluginsArg.keywordArg !is ListCollection) {
                                pluginsArg.keywordArg.token.compileError("Plugins argument must be ${YEL}List::String")
                            }
                            pluginsArg.keywordArg.initElements.map { it.token.lexeme.removeDoubleQuotes() }
                                .toMutableSet()
                        } else null

                    val pkgName = pkgArg.keywordArg.toString()
                    if (savedPackageName == pkgName) {
                        it.token.compileError("Package $savedPackageName already exists, it is proposed to rename it to ${savedPackageName}.bind.niva")
                    }

                    changePackage(pkgName, it.token, isBinding = true, neededImports = neededImports, neededPlugins = neededPlugins)
                    // declarations
                    val declarations = contentArg.keywordArg.statements
                    declarations.forEach { decl ->
                        if (decl is Declaration) {
                            val emptyScope: MutableMap<String, Type> = mutableMapOf()
                            resolveDeclarations(decl, emptyScope, resolveBody = false)
                            onEachStatement?.invoke(decl, emptyScope, emptyScope, decl.token.file) // Bind content
                        } else {
                            decl.token.compileError("There can be only declarations inside Bind, but found $WHITE$decl")
                        }
                    }

                    // getters
                    val gettersArg = msg.args.find { it.name == "getters" }
                    if (gettersArg != null) {
                        if (gettersArg.keywordArg !is CodeBlock)
                            gettersArg.keywordArg.token.compileError("getters argument must be a code block with kw sends(they will become setters and getters on java side)")
                        val gettersDeclarations = gettersArg.keywordArg.statements
                        gettersDeclarations.forEach { field ->
                            when (field) {
                                is MessageSendKeyword -> {
                                    val kw = field.messages.first() as KeywordMsg
                                    val receiver = field.receiver
                                    if (receiver !is IdentifierExpr) {
                                        field.token.compileError("receiver must be type")
                                    }
                                    kw.args.forEach {
                                        val forTypeAST = TypeAST.UserType(receiver.name, token = receiver.token)
                                        val forTypeReal = forTypeAST.toType(typeDB, typeTable)

                                        val typeAstFromArg =
                                            TypeAST.UserType(it.keywordArg.token.lexeme, token = it.keywordArg.token)
                                        val realTypeArg = typeAstFromArg.toType(typeDB, typeTable)

                                        // add getter and setter for each
                                        val msgDeclGetter = MessageDeclarationUnary(
                                            name = it.name,
                                            forType = forTypeAST,
                                            token = field.token,
                                            isSingleExpression = false,
                                            body = emptyList(),
                                            returnType = typeAstFromArg, // "Person name: String" // return type of getter is arg
                                            isInline = false,
                                            isSuspend = false,
                                        )

                                        msgDeclGetter.forType = forTypeReal
                                        msgDeclGetter.returnType = realTypeArg

                                        addNewAnyMessage(msgDeclGetter, isGetter = true)

                                        // lsp
                                        val emptyScope: MutableMap<String, Type> = mutableMapOf()

                                        onEachStatement?.invoke( // Bind getters
                                            msgDeclGetter,
                                            emptyScope,
                                            emptyScope,
                                            msgDeclGetter.token.file
                                        )
                                    }
                                }

                                else -> field.token.compileError("Use keyword msg send with fields, that fields will become getters and setters (${WHITE}Person name: String -> setName::String + getName -> String$RESET)")
                            }
                        }
                    }

                    // fields
                    val fieldsArg = msg.args.find { it.name == "fields" }
                    if (fieldsArg != null) {
                        if (fieldsArg.keywordArg !is CodeBlock)
                            fieldsArg.keywordArg.token.compileError("Fields argument must be a code block with kw sends(they will become setters and getters on java side)")
                        val fieldsDeclarations = fieldsArg.keywordArg.statements
                        fieldsDeclarations.forEach { field ->
                            when (field) {
                                is MessageSendKeyword -> {
                                    val kw = field.messages.first() as KeywordMsg
                                    val receiver = field.receiver
                                    if (receiver !is IdentifierExpr) {
                                        field.token.compileError("receiver must be type")
                                    }
                                    kw.args.forEach {


                                        val forTypeAST = TypeAST.UserType(receiver.name, token = receiver.token)
                                        val forTypeReal = forTypeAST.toType(typeDB, typeTable)

                                        val typeAstFromArg =
                                            TypeAST.UserType(it.keywordArg.token.lexeme, token = it.keywordArg.token)
                                        val realTypeArg = typeAstFromArg.toType(typeDB, typeTable)

                                        // add getter and setter for each
                                        val msgDeclGetter = MessageDeclarationUnary(
                                            name = it.name,
                                            forType = forTypeAST,
                                            token = field.token,
                                            isSingleExpression = false,
                                            body = emptyList(),
                                            returnType = typeAstFromArg, // "Person name: String" // return type of getter is arg
                                            isInline = false,
                                            isSuspend = false,
                                        )

                                        msgDeclGetter.forType = forTypeReal
                                        msgDeclGetter.returnType = realTypeArg

                                        val msgDeclSetter = MessageDeclarationKeyword(
                                            name = it.name,
                                            forType = forTypeAST,
                                            token = field.token,
                                            isSingleExpression = false,
                                            body = emptyList(),
                                            returnType = null,
                                            args = listOf(
                                                KeywordDeclarationArg(
                                                    name = it.name,
                                                    it.keywordArg.token,
                                                    typeAST = typeAstFromArg
                                                )
                                            ),
                                            isSuspend = false
                                        )

                                        msgDeclSetter.forType = forTypeReal
                                        msgDeclSetter.returnType = Resolver.defaultTypes[InternalTypes.Unit]!!


                                        addNewAnyMessage(msgDeclGetter, isGetter = true)
                                        addNewAnyMessage(msgDeclSetter, isSetter = true) // setter

                                        // lsp
                                        val emptyScope: MutableMap<String, Type> = mutableMapOf()

                                        onEachStatement?.invoke( // Bind fields
                                            msgDeclGetter,
                                            emptyScope,
                                            emptyScope,
                                            msgDeclGetter.token.file
                                        )

                                        onEachStatement?.invoke( // Bind fields
                                            msgDeclSetter, // setter
                                            emptyScope,
                                            emptyScope,
                                            msgDeclSetter.token.file
                                        )

                                    }

                                }

                                else -> field.token.compileError("Use keyword msg send with fields, that fields will become getters and setters (${WHITE}Person name: String -> setName::String + getName -> String$RESET)")
                            }
                        }
                    }

                    changePackage(savedPackageName, createFakeToken())
                }
            }
        }

    }
//    changePackage(savedPackageName, createFakeToken())
}
