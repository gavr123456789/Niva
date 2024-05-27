@file:Suppress("EnumEntryName", "NAME_SHADOWING")

package frontend.resolver

import frontend.parser.types.ast.KeyPragma
import frontend.resolver.Type.RecursiveType.copy
import frontend.resolver.messageResolving.resolveCodeBlock
import main.codogen.GeneratorKt
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*
import main.frontend.typer.*
import main.utils.*
import java.io.File
import java.util.Stack

private fun Resolver.addPrintingInfoAboutType(type: Type, printOnlyTypeName: Boolean) {
//    infoTypesToPrint.add(type)
    infoTypesToPrint[type] = printOnlyTypeName
}

fun Resolver.addToTopLevelStatements(statement: Statement) {
    if (currentLevel == 0 && resolvingMainFile) topLevelStatements.add(statement)
}

private fun Resolver.resolveStatement(
    statement: Statement,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {
    val resolveTypeForMessageSend = { statement2: MessageSend ->
        when (statement2.receiver.token.lexeme) {
            "Project", "Bind" -> {}

            else -> {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                resolve(statement2.messages, previousAndCurrentScope, statement2)

                // fix return types of cascade messages
                val isThereCascade = statement2.messages.find { it.isCascade } != null
                if (isThereCascade) {
                    if (statement2.messages.count() == 1) {
                        val first = statement2.messages[0]
                        if (first.isCascade) {
                            first.type = first.receiver.type
                        }
                    } else if (statement2.messages.count() > 1) {
                        for (i in 1 until statement2.messages.count()) {
                            val it = statement2.messages[i]
                            if (it.isCascade) {
                                it.type = statement2.messages[i - 1].receiver.type
                            }
                        }
                    }
                }


                if (statement2.messages.isNotEmpty()) {
                    statement2.type =
                        statement2.messages.last().type
                            ?: statement2.token.compileError("Not all messages of ${YEL}${statement2.str} ${WHITE}has types")
                } else {
                    // every single expressions is unary message without messages
                    if (statement2.type == null) {
                        currentLevel++
                        resolveSingle((statement2.receiver), previousAndCurrentScope, statement2)
                        currentLevel--
                    }
                    statement2.type = statement2.receiver.type
                        ?: statement2.token.compileError("Can't find type for ${YEL}${statement2.str} on line ${WHITE}${statement2.token.line}")
                }
            }
        }

    }

    when (statement) {
        is Declaration -> {
            val resolveMsgDeclarationOnlyBody = { statement: MessageDeclaration ->
                currentLevel++
                resolveMessageDeclaration(
                    statement,
                    true,
                    (previousScope).toMutableMap(), // don't send currentScope in body, because actually declarations cant see top level declarated variables
                    addToDb = false
                )
                currentLevel--
            }

            if (!allDeclarationResolvedAlready)
                resolveDeclarations(statement, previousScope, true)
            else if (statement is MessageDeclaration) {
                // first time all declarations resolved without bodies, now we need to resolve them
                resolveMsgDeclarationOnlyBody(statement)
            } else if (statement is ExtendDeclaration) {
                statement.messageDeclarations.forEach {
                    resolveMsgDeclarationOnlyBody(it)
                }
            }
        }

        is VarDeclaration -> {
            stack.push(statement)
            resolveVarDeclaration(statement, currentScope, previousScope)
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // var
            }
            stack.pop()
        }

        is Message -> {
            resolveMessage(statement, currentScope, previousScope)
        }

        is MessageSend -> {
            stack.push(statement)

            if (statement.receiver.token.lexeme == InternalTypes.Compiler.name) {
                val msg = statement.messages[0]
                if (msg.selectorName == "getName") {
                    val intArg = (msg as KeywordMsg).args[0].keywordArg
                    val codeAtr = KeyPragma("arg", intArg as Primary)

                    resolvingMessageDeclaration?.apply {
                        this.pragmas.add(codeAtr)
                    }
                    statement.pragmas.add(codeAtr)
                }

            }

            resolveTypeForMessageSend(statement)
            addToTopLevelStatements(statement)
            stack.pop()
        }

        is StaticBuilder -> {
            currentLevel++
            resolve(statement.statements, (currentScope + previousScope).toMutableMap())
            currentLevel--
            TODO()
        }

        is IdentifierExpr -> {
            val kw = if (rootStatement is KeywordMsg) {
                rootStatement
            } else null

            statement.type = getTypeForIdentifier(
                statement, previousScope, currentScope, kw
            )
            val type = statement.type

            // This Identifier is Type, like Person
            if (type != null && type !is Type.EnumRootType && rootStatement !is ControlFlow) {
                if (statement.isInfoRepl) {
                    addPrintingInfoAboutType(type, statement.str != type.name)
                }

                statement.isType = true
            }
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // identifier
            }

            addToTopLevelStatements(statement)
        }

        is ExpressionInBrackets -> {
            resolveExpressionInBrackets(statement, (previousScope + currentScope).toMutableMap())
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // (expr)
            }
            addToTopLevelStatements(statement)
        }

        is CodeBlock -> {
            stack.push(statement)

            resolveCodeBlock(statement, previousScope, currentScope, rootStatement)

            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // codeblock
            }
            addToTopLevelStatements(statement)
            stack.pop()
        }

        is ListCollection -> {
            resolveCollection(statement, "MutableList", (previousScope + currentScope).toMutableMap(), rootStatement)
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // list
            }
            addToTopLevelStatements(statement)
        }

        is SetCollection -> {
            resolveSet(statement, (previousScope + currentScope).toMutableMap(), rootStatement)
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // set
            }
            addToTopLevelStatements(statement)
        }

        is MapCollection -> {
            resolveMap(statement, rootStatement, previousScope, currentScope)
            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // map
            }
            addToTopLevelStatements(statement)
        }

        is  LiteralExpression -> {

            when (statement) {
                is LiteralExpression.FloatExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Float]

                is LiteralExpression.DoubleExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Double]

                is LiteralExpression.IntExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Int]

                is LiteralExpression.StringExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.String]

                is LiteralExpression.CharExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Char]

                is LiteralExpression.TrueExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Boolean]

                is LiteralExpression.NullExpr -> {
                    if (rootStatement is VarDeclaration) {
                        val astValueType = rootStatement.valueTypeAst
                        if (astValueType != null) {
                            val type = astValueType.toType(typeDB, typeTable)
                            statement.type = type
                        }
                    } else {
                        statement.type = Resolver.defaultTypes[InternalTypes.Null]
                    }
                }

                is LiteralExpression.FalseExpr ->
                    statement.type = Resolver.defaultTypes[InternalTypes.Boolean]
            }

            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // literal
            }
        }


        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType -> {}

        is ControlFlow -> {
            stack.push(statement)

            resolveControlFlow(statement, previousScope, currentScope, rootStatement)

            if (GlobalVariables.isLspMode) {
                onEachStatement!!(statement, currentScope, previousScope) // if
            }

            addToTopLevelStatements(statement)
            stack.pop()
        }

        is Assign -> {
            stack.push(statement)

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            currentLevel++
            resolveSingle((statement.value), previousAndCurrentScope, statement)
            currentLevel--
            val q = previousAndCurrentScope[statement.name]
            if (q != null) {
                // this is <-, not =
                val w = statement.value.type!!
                if (!compare2Types(q, w, unpackNullForFirst = true)) {
                    statement.token.compileError("In $WHITE$statement $YEL$q$RESET != $YEL$w")
                }
            }
            addToTopLevelStatements(statement)

            stack.pop()
        }

        is ReturnStatement -> {
            stack.push(statement)

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            val expr = statement.expression
            if (expr != null) {
                resolveSingle((expr), previousAndCurrentScope, statement)
                if (expr.type == null) {
                    throw Exception("Cant infer type of return statement on line: ${expr.token.line}")
                }
            }
            val unit = Resolver.defaultTypes[InternalTypes.Unit]!!
            val typeOfReturnExpr = if (expr == null) unit else expr.type!!

            ///
            val previousReturnType = wasThereReturn
            val resolvingMessageDeclaration2 = resolvingMessageDeclaration

            if (resolvingMessageDeclaration2 != null && resolvingMessageDeclaration2.returnTypeAST == null) {
                if (previousReturnType != null) {
                    val g = findGeneralRoot(previousReturnType, typeOfReturnExpr)
                        ?: statement.token.compileError("Cant find general root between return types $YEL$typeOfReturnExpr$RESET and $YEL$previousReturnType$RESET")
                    resolvingMessageDeclaration2.returnType = g
                } else {
                    resolvingMessageDeclaration2.returnType = typeOfReturnExpr
                }
            }

            ///
            wasThereReturn = typeOfReturnExpr

            stack.pop()
        }

        is DotReceiver -> {
            statement.type = findThisInScopes(statement.token, currentScope, previousScope)
        }

        is NeedInfo -> {
            val t = statement.expression
            when (t) {
                is MessageSend -> {
                    // resolve receiver
                    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                    currentLevel++
                    resolveSingle((t.receiver), previousAndCurrentScope, statement)
                    currentLevel--
                    val receiverType = t.receiver.type!!


                    // get what to search
                    val searchRequest = t.messages.first().selectorName.lowercase()
                    if (!GlobalVariables.isLspMode)
                        findSimilar(searchRequest, receiverType)

                    endOfSearch(currentScope + previousScope)
                }

                else -> {
                    endOfSearch(currentScope + previousScope)
                }
            }
        }

        is MethodReference -> {
            val forType = statement.forType.toType(typeDB, typeTable)

            when (statement) {
                is MethodReference.Binary -> {
                    forType.protocols.values.forEach {
                        it.binaryMsgs[statement.name]?.let { method -> statement.method = method }
                    }
                }
                is MethodReference.Keyword -> {
                    val name = statement.keys.first() + statement.keys.drop(1).joinToString("") { it.uppercase() }
                    forType.protocols.values.forEach {
                        val w = it.keywordMsgs[name]
                        if (w != null) {
                            statement.method = w
                        }
                    }
                }
                is MethodReference.Unary -> {
                    forType.protocols.values.forEach {
                        it.unaryMsgs[statement.name]?.let { method -> statement.method = method }
                    }
                }
            }


            val method = statement.method ?: statement.token.compileError("Can't find method $CYAN${statement.name}")
            statement.type = method.toLambda(forType)
        }

    }
}

fun Resolver.resolveSingle(
    statement: Statement,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement? = null, // since we in recursion, and can't define on what level of it, it's the only way to know previous statement
): Statement {
    val currentScope = mutableMapOf<String, Type>()
    resolveStatement(
        statement,
        currentScope,
        previousScope,
        rootStatement
    )



    return statement
}

fun Resolver.resolve(
    statements: List<Statement>,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement? = null, // since we in recursion, and can't define on what level of it, it's the only way to know previous statement
): List<Statement> {

    val currentScope = mutableMapOf<String, Type>()

    statements.forEach { statement ->

        resolveStatement(
            statement,
            currentScope,
            previousScope,
            rootStatement
        )

//        println("$currentLevel on line ${statement.token.line} resolving1 ${statement::class.simpleName} $statement")

    }

    // we need to filter this things, only ifcurrent level is 0
    topLevelStatements = topLevelStatements.filter {
        !(it is MessageSendKeyword && (it.receiver.str == "Project" || it.receiver.str == "Bind" || it.receiver.str == "Compiler"))
    }.toMutableList()


    return statements
}

fun Resolver.resolveExpressionInBrackets(
    statement: ExpressionInBrackets,
    previousAndCurrentScope: MutableMap<String, Type>,
): Type {
    if (statement.statements.isEmpty()) {
        statement.token.compileError("Parens must contain expression")
    }
    val lastExpr = statement.statements.last()
    if (lastExpr !is Expression) {
        statement.token.compileError("Last statement inside parens must be expression")
    }

    resolve(statement.statements, previousAndCurrentScope, statement)
    statement.type = lastExpr.type
    return lastExpr.type!!
}


fun findGeneralRoot(type1: Type, type2: Type): Type? {

    if (type1 == type2)
        return type1
    if (type1 is Type.UnknownGenericType && type2 is Type.UnknownGenericType && type1.name == type2.name)
        return type1

    // first is parent of the second
    var parent1: Type? = type1.parent
    while (parent1 != null) {
        if (findGeneralRoot(type2, parent1) != null) {
            return parent1
        }
        parent1 = parent1.parent
    }
    // second is parent of the first
    var parent2: Type? = type2.parent
    while (parent2 != null) {
        if (findGeneralRoot(type1, parent2) != null) {
            return parent2
        }
        parent2 = parent2.parent
    }

    return null
}

fun Package.addImport(pkg: String, concrete: Boolean = false) {
    if (packageName != pkg) {
        if (!concrete) {
            imports.add(pkg)
        } else {
            concreteImports.add(pkg)
        }
    }
}

fun Resolver.findPackageOrError(packageName: String, token: Token): Package {
    val p = this.projects[currentProjectName] ?: token.compileError("There are no such project: $currentProjectName")
    val pack = p.packages[packageName] ?: token.compileError("There are no such package: $packageName")
    return pack
}


fun Resolver.getCurrentProtocol(type: Type, token: Token, customPkg: Package? = null): Pair<Protocol, Package> {
    val pack = customPkg ?: findPackageOrError(currentPackageName, token)
    val protocol = type.protocols[currentProtocolName]

    if (protocol == null) {
        val newProtocol = Protocol(currentProtocolName)
        type.protocols[currentProtocolName] = newProtocol
        return Pair(newProtocol, pack)
    }
    return Pair(protocol, pack)
}

fun Resolver.getCurrentPackage(token: Token) = findPackageOrError(currentPackageName, token)
fun Resolver.getCurrentImports(token: Token) = getCurrentPackage(token).imports

// TODO! make universal as toAnyMessageData
fun Resolver.addStaticDeclaration(statement: ConstructorDeclaration): MessageMetadata {
    val typeOfReceiver = typeTable[statement.forTypeAst.name]!!//testing
    // if return type is not declared then use receiver
    val returnType = if (statement.returnTypeAST == null)
        typeOfReceiver
    else
        statement.returnType ?: statement.returnTypeAST.toType(typeDB, typeTable)

    val messageData = when (statement.msgDeclaration) {
        is MessageDeclarationUnary -> {
            val type =
                statement.forType ?: statement.token.compileError("Compiler error, type for $statement not resolved")
            val (protocol, pkg) = getCurrentProtocol(type, statement.token)

            val messageData = UnaryMsgMetaData(
                name = statement.msgDeclaration.name,
                returnType = returnType,
                pragmas = statement.pragmas,
                pkg = pkg.packageName
            )

            protocol.staticMsgs[statement.name] = messageData
            messageData
        }

        is MessageDeclarationBinary -> {
            statement.token.compileError("Binary static message, really? This is not allowed")
        }

        is MessageDeclarationKeyword -> {
            val type =
                statement.forType ?: statement.token.compileError("Compiler error, type for $statement not resolved")

            val (protocol, pkg) = getCurrentProtocol(type, statement.token)

            val keywordArgs = statement.msgDeclaration.args.map {
                KeywordArg(
                    name = it.name,
                    type = it.typeAST?.toType(typeDB, typeTable)//fix
                        ?: statement.token.compileError("Type of keyword message ${YEL}${statement.msgDeclaration.name}${RESET}'s arg ${WHITE}${it.name}${RESET} not registered")
                )
            }

            val messageData = KeywordMsgMetaData(
                name = statement.msgDeclaration.name,
                argTypes = keywordArgs,
                returnType = returnType,
                pragmas = statement.pragmas,
                pkg = pkg.packageName
            )
            protocol.staticMsgs[statement.name] = messageData
            messageData
        }

        is ConstructorDeclaration -> TODO()
    }
    return messageData
}


fun Resolver.addNewAnyMessage(
    st: MessageDeclaration,
    isGetter: Boolean = false,
    isSetter: Boolean = false,
    forType: Type? = null
): MessageMetadata {
    val customPkg = if (st.forTypeAst is TypeAST.UserType && st.forTypeAst.names.count() > 1) {
        findPackageOrError(st.forTypeAst.names.dropLast(1).joinToString("."), st.token)
    } else null

    val type =
        if (forType is Type.UnknownGenericType)
            Resolver.defaultTypes[InternalTypes.UnknownGeneric]!!
        else
            st.forType
                ?: st.token.compileError("Compiler error, receiver type of $WHITE$st$RESET declaration not resolved")

    val (protocol, pkg) = getCurrentProtocol(type, st.token, customPkg)
    val messageData = st.toAnyMessageData(typeDB, typeTable, pkg, isGetter, isSetter, this)


    when (st) {
        is MessageDeclarationUnary -> protocol.unaryMsgs[st.name] = messageData as UnaryMsgMetaData
        is MessageDeclarationBinary -> protocol.binaryMsgs[st.name] = messageData as BinaryMsgMetaData
        is MessageDeclarationKeyword -> protocol.keywordMsgs[st.name] = messageData as KeywordMsgMetaData
        is ConstructorDeclaration -> {
            // st.toAnyMessageData already adding static to db
        }
    }

    st.messageData = messageData
    addMsgToPackageDeclarations(st)
    return messageData
}

fun Resolver.addMsgToPackageDeclarations(statement: MessageDeclaration) {
    val pack = findPackageOrError(currentPackageName, statement.token)
    pack.declarations.add(statement)

    pack.addImport(statement.forType!!.pkg)

    fun addArgsPackage(messageData: MessageMetadata) {
        when (messageData) {
            is UnaryMsgMetaData -> { /* no args */ }
            is BinaryMsgMetaData -> {
                pack.addImport(messageData.argType.pkg)
            }
            is KeywordMsgMetaData -> {
                messageData.argTypes.forEach {
                    pack.addImport(it.type.pkg)
                }
            }
        }
    }

    statement.messageData?.let {
        addArgsPackage(it)
    }


}

fun Resolver.typeAlreadyRegisteredInCurrentPkg(
    typeName: String,
    pkg: Package? = null,
    token: Token? = null
): Type.UserLike? {
    val pack = pkg ?: getCurrentPackage(token ?: createFakeToken())
    val result = pack.types[typeName]
    return result as? Type.UserLike
}

fun Resolver.addNewType(
    type: Type,
    statement: SomeTypeDeclaration?,
    pkg: Package? = null,
    alreadyCheckedOnUnique: Boolean = false,
    alias: Boolean = false
) {
    // 1 get package
    val pack = pkg ?: getCurrentPackage(statement?.token ?: createFakeToken())
    // 2 check type for unique
    val typeName = if (alias) (statement as TypeAliasDeclaration).typeName else type.name
    if (!alreadyCheckedOnUnique && typeAlreadyRegisteredInCurrentPkg(typeName, pkg, statement?.token) != null) {
        val err = "Type with name ${YEL}${typeName}${RESET} already registered in package: ${WHITE}$currentPackageName"
        statement?.token?.compileError(err) ?: throw Exception(err)
    }
    // 3 add declaration to be generated
    if (statement != null) {
        pack.declarations.add(statement)
    }
    // 4 is binding
    if (pack.isBinding && type is Type.UserLike) {
        type.isBinding = true
    }
    // 5 put type to all type sources
    pack.types[typeName] = type
    typeTable[typeName] = type//fix
    typeDB.add(type, token = statement?.token ?: createFakeToken(), customNameAlias = typeName)
}


fun Resolver.changeProject(newCurrentProject: String, token: Token) {
    // clear all current, load project
    currentProjectName = newCurrentProject
    // check that there are no such project already

    if (projects[newCurrentProject] != null) {
        token.compileError("Project with name: $newCurrentProject already exists")
    }
    val commonProject = projects["common"] ?: token.compileError("Can't find common project")


    projects[newCurrentProject] = Project(
        name = newCurrentProject,
        usingProjects = mutableListOf(commonProject)
    )

    TODO()
}

fun Resolver.changePackage(
    newCurrentPackage: String,
    token: Token,
    isBinding: Boolean = false,
    isMainFile: Boolean = false,
    neededImports: MutableSet<String>? = null,
    neededPlugins: MutableSet<String>? = null,
) {
    currentPackageName = newCurrentPackage
    val currentProject =
        projects[currentProjectName] ?: token.compileError("Can't find project: ${WHITE}$currentProjectName")
    val alreadyExistsPack = currentProject.packages[newCurrentPackage]

    // check that this package not exits already
    if (alreadyExistsPack != null) {
        // load table of types
        typeTable.putAll(alreadyExistsPack.types)//fixed
        alreadyExistsPack.types.values.forEach {
            if (it is Type.InternalType) {
                typeDB.addInternalType(it.name, it)
            }
            if (it is Type.UserLike) {
                typeDB.addUserLike(it.name, it, token)
            }
        }

    } else {
        // create this new package
        val pack = Package(
            packageName = newCurrentPackage,
            isBinding = isBinding,
            neededImports = neededImports ?: mutableSetOf(),
            plugins = neededPlugins ?: mutableSetOf()
        )

        currentProject.packages[newCurrentPackage] = pack
        // top level statements and default definitions located in different pkgs
        // so to add access from top level statements(mainNiva) to this definitions
        // we need to always import it
        if (isMainFile) {
            val mainNivaPkg = currentProject.packages[MAIN_PKG_NAME]!!
            mainNivaPkg.addImport(newCurrentPackage)
        }
    }

}

fun Resolver.changeProtocol(protocolName: String) {
    currentProtocolName = protocolName
}

fun Resolver.usePackage(packageName: String, noStarImport: Boolean = false) {
    val currentPkg = getCurrentPackage(this.statements.last().token)
    currentPkg.addImport(packageName, noStarImport)
}

enum class CompilationTarget {
    jvm,
    linux,
    macos,
//    windows,
}

enum class CompilationMode {
    release,
    debug,
}


fun Resolver.changeTarget(target: String, token: Token) {
    fun targetFromString(target: String, token: Token): CompilationTarget = when (target) {
        "jvm" -> CompilationTarget.jvm
        "linux" -> CompilationTarget.linux
        "macos" -> CompilationTarget.macos
        "windows" -> token.compileError("Windows native target not supported yet")
        "js" -> token.compileError("js target not supported yet")
        else -> token.compileError("There is no such target as ${WHITE}$target${RESET}, supported targets are ${WHITE}${CompilationTarget.entries.map { it.name }}${RESET}, default: ${WHITE}jvm")
    }

    val target = targetFromString(target, token)
    compilationTarget = target
}

fun CompilationMode.toCompileOnlyTask(target: CompilationTarget): String {
    if (target == CompilationTarget.jvm) return "dist"
    val targetStr = when (target) {
        CompilationTarget.linux -> "LinuxX64"
        CompilationTarget.macos -> "MacosArm64"
        else -> TODO()
    }
    val compMode = when (this) {
        CompilationMode.release -> "Release"
        CompilationMode.debug -> "Debug"
    }

    return "link$targetStr${compMode}Executable$targetStr"
}

fun CompilationMode.toBinaryPath(target: CompilationTarget, pathToProjectRoot: String): String {
    if (target == CompilationTarget.jvm) return ""
    val targetStr = when (target) {
        CompilationTarget.linux -> "linuxX64"
        CompilationTarget.macos -> "macosArm64"
        else -> TODO()
    }
    val compMode = when (this) {
        CompilationMode.release -> "${targetStr}ReleaseExecutable"
        CompilationMode.debug -> "${targetStr}DebugExecutable"
    }

    return pathToProjectRoot / "build" / "bin" / targetStr / compMode / "kotlin.kexe"
}


fun Resolver.changeCompilationMode(mode: String, token: Token) {
    fun modeFromString(mode: String, token: Token): CompilationMode = when (mode) {
        "release" -> CompilationMode.release
        "debug" -> CompilationMode.debug
        else -> token.compileError("There is no such compilation mode as ${WHITE}$mode, supported targets are ${WHITE}${CompilationMode.entries.map { it.name }}${RESET}, default: ${WHITE}debug")
    }

    val modeEnum = modeFromString(mode, token)
    compilationMode = modeEnum
}


fun Resolver.getTypeForIdentifier(
    x: IdentifierExpr,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    kw: KeywordMsg? = null
): Type {

    val type =
        getAnyType(x.names.first(), currentScope, previousScope, kw, x.names) ?: getAnyType(
            x.name,
            currentScope,
            previousScope,
            kw,
            x.names
        )

        ?: x.token.compileError("Unresolved reference: ${WHITE}${x.str}")


    val typeWithGenericResolved =
        if (x.typeAST != null && !x.typeAST.name.isGeneric() && type is Type.UserLike && type.typeArgumentList.count() == 1) {
            // replace Generic from typeAst with sas
            val e = getAnyType(x.typeAST.name, currentScope, previousScope, kw)!!
            val copy = type.copy()
            copy.typeArgumentList = listOf(e)
            copy
        } else type
//    x.type = type
    return typeWithGenericResolved
}

// If there are more than one type with the same name, and pkg is not specified, than this method will throw
fun Resolver.getAnyType(
    typeName: String,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    statement: KeywordMsg?, // type constructor call
    names: List<String> = listOf()
): Type? {
    val typeFromDb = typeDB.getType(typeName, currentScope, previousScope)
    val currentPackage = getCurrentPackage(statement?.token ?: createFakeToken())

    val type = typeFromDb.getTypeFromTypeDBResultConstructor(statement, currentPackage.imports, currentPackageName)
    return type
}


fun IfBranch.getReturnTypeOrThrow(): Type = when (this) {
    is IfBranch.IfBranchSingleExpr -> this.thenDoExpression.type!!
    is IfBranch.IfBranchWithBody -> {
        val t = body.type
        if (t is Type.Lambda) {
            t.returnType
        } else {
            val last = body.statements.last()
            if (last is Expression) {
                last.type!!
            } else {
                Resolver.defaultTypes[InternalTypes.Unit]!!
            }
        }
    }
}


typealias TypeName = String


@Suppress("NOTHING_TO_INLINE")
inline fun createDefaultType(type: InternalTypes): Pair<InternalTypes, Type.InternalType> {
    return type to Type.InternalType(
        typeName = type,
        pkg = "common",
    )
}

typealias PkgToUnresolvedDecl<T> = MutableMap<String, MutableSet<T>>

fun <T> PkgToUnresolvedDecl<T>.add(pkg: String, decl: T) {
    val q = this[pkg]
    if (q == null) {
        this[pkg] = mutableSetOf(decl)
    } else {
        q.add(decl)
    }
}

fun <T> PkgToUnresolvedDecl<T>.remove(pkg: String, decl: T) {
    this[pkg]?.remove(decl)
}

val createTypeListOfType = { name: String, internalType: Type.InternalType, listType: Type.UserType ->
    Type.UserType(
        name = name,
        typeArgumentList = listOf(internalType.copy().also { it.beforeGenericResolvedName = "T" }),
        fields = mutableListOf(),
        pkg = "core",
        protocols = listType.protocols
    )
}
val createTypeMapOfType = { name: String, key: Type.InternalType, value: Type.UserLike, mapType: Type.UserType ->
    Type.UserType(
        name = name,
        typeArgumentList = listOf(
            key.copy().also { it.beforeGenericResolvedName = "T" },
            value.copy().also { it.beforeGenericResolvedName = "G" }
        ),
        fields = mutableListOf(),
        pkg = "core",
        protocols = mapType.protocols
    )
}


class Resolver(
    val projectName: String,

    var statements: MutableList<Statement>,

    val otherFilesPaths: List<File> = listOf(),

    val projects: MutableMap<String, Project> = mutableMapOf(),

    // reload when package changed
    val typeTable: MutableMap<TypeName, Type> = mutableMapOf(),
    val typeDB: TypeDB = TypeDB(),

    var topLevelStatements: MutableList<Statement> = mutableListOf(),
    var currentLevel: Int = 0,

    var currentProjectName: String = "common",
    var currentPackageName: String = "common",
    var currentProtocolName: String = "common",

    // number when resolving lambda or keyword
    var currentArgumentNumber: Int = -1,

    // for recursive types
    val unResolvedMessageDeclarations: PkgToUnresolvedDecl<MessageDeclaration> = mutableMapOf(),
    val unResolvedSingleExprMessageDeclarations: PkgToUnresolvedDecl<MessageDeclaration> = mutableMapOf(),
    val unResolvedTypeDeclarations: PkgToUnresolvedDecl<SomeTypeDeclaration> = mutableMapOf(),
    var allDeclarationResolvedAlready: Boolean = false,

    val generator: GeneratorKt = GeneratorKt(),

    var compilationTarget: CompilationTarget = CompilationTarget.jvm,
    var compilationMode: CompilationMode = CompilationMode.debug,

    // set to null before body resolve, set to real inside body, check after to know was there return or not
    var wasThereReturn: Type? = null,
    var resolvingMessageDeclaration: MessageDeclaration? = null,


    val infoTypesToPrint: MutableMap<Type, Boolean> = mutableMapOf(),

    val stack: Stack<Statement> = Stack(),

    var resolvingMainFile: Boolean = false,

    val onEachStatement: ((Statement, Map<String, Type>?, Map<String, Type>?) -> Unit)? = null
) {
    fun reset() {
        statements = mutableListOf()
        resolvingMainFile = false
        stack.clear()
        infoTypesToPrint.clear()
        allDeclarationResolvedAlready = false
        currentArgumentNumber = -1
        currentLevel = 0
        topLevelStatements.clear()
    }
    companion object {
        fun empty(otherFilesPaths: List<File>) = Resolver(
            projectName = "common",
            otherFilesPaths = otherFilesPaths,
            statements = mutableListOf(),
            onEachStatement = null
        )

        val defaultTypes: Map<InternalTypes, Type.InternalType> = mapOf(

            createDefaultType(InternalTypes.Int),
            createDefaultType(InternalTypes.String),
            createDefaultType(InternalTypes.Char),
            createDefaultType(InternalTypes.Float),
            createDefaultType(InternalTypes.Double),
            createDefaultType(InternalTypes.Boolean),
            createDefaultType(InternalTypes.Unit),

            createDefaultType(InternalTypes.Project),
            createDefaultType(InternalTypes.Bind),
            createDefaultType(InternalTypes.Compiler),
            createDefaultType(InternalTypes.IntRange),
            createDefaultType(InternalTypes.CharRange),

            createDefaultType(InternalTypes.Any),
            createDefaultType(InternalTypes.Nothing),
            createDefaultType(InternalTypes.Null),

            createDefaultType(InternalTypes.UnknownGeneric),
            createDefaultType(InternalTypes.Test),
        )

        init {
            val intType = defaultTypes[InternalTypes.Int]!!
            val stringType = defaultTypes[InternalTypes.String]!!
            val charType = defaultTypes[InternalTypes.Char]!!
            val floatType = defaultTypes[InternalTypes.Float]!!
            val doubleType = defaultTypes[InternalTypes.Double]!!
            val boolType = defaultTypes[InternalTypes.Boolean]!!
            val unitType = defaultTypes[InternalTypes.Unit]!!
            val intRangeType = defaultTypes[InternalTypes.IntRange]!!
            val charRangeType = defaultTypes[InternalTypes.CharRange]!!
            val anyType = defaultTypes[InternalTypes.Any]!!
            val unknownGenericType = defaultTypes[InternalTypes.UnknownGeneric]!!
            val test = defaultTypes[InternalTypes.Test]!!


//            val nullType = defaultTypes[InternalTypes.Null]!!
//            val compiler = defaultTypes[InternalTypes.Compiler]!!


            intType.protocols.putAll(
                createIntProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    floatType = floatType,
                    doubleType = doubleType,
                    intRangeType = intRangeType,
                    anyType = anyType,
                    charType = charType
                )
            )

            floatType.protocols.putAll(
                createFloatProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    floatType = floatType,
                ).also { it["double"] = Protocol("double", mutableMapOf(createUnary("toDouble", doubleType))) }
            )


            doubleType.protocols.putAll(
                createFloatProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    floatType = doubleType,
                ).also { it["float"] = Protocol("float", mutableMapOf(createUnary("toFloat", floatType))) }
            )


            stringType.protocols.putAll(
                createStringProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    charType = charType,
                    any = anyType,
                    floatType = floatType,
                    doubleType = doubleType,
                    intRangeType = intRangeType
                )
            )

            boolType.protocols.putAll(
                createBoolProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    any = anyType,
                    genericParam = Type.UnknownGenericType("T")
                )
            )

            charType.protocols.putAll(
                createCharProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    charType = charType,
                    any = anyType,
                    charRange = charRangeType
                )
            )


            anyType.protocols.putAll(
                createAnyProtocols(
                    unitType = unitType,
                    any = anyType,
                    boolType = boolType,
                    stringType = stringType
                )
            )
            // we need to have different links to protocols any and T, because different msgs can be added for both
            unknownGenericType.protocols.putAll(
                createAnyProtocols(
                    unitType = unitType,
                    any = anyType,
                    boolType = boolType,
                    stringType = stringType
                )
            )

            intRangeType.protocols.putAll(
                createRangeProtocols(
                    rangeType = intRangeType,
                    boolType = boolType,
                    itType = intType,
                    unitType = unitType,
                    any = anyType,
                )
            )

            charRangeType.protocols.putAll(
                createRangeProtocols(
                    rangeType = charRangeType,
                    boolType = boolType,
                    itType = charType,
                    unitType = unitType,
                    any = anyType,
                )
            )

            test.protocols.putAll(
                createTestProtocols(
                    rangeType = charRangeType,
                    boolType = boolType,
                    itType = charType,
                    unitType = unitType,
                    any = anyType,
                )
            )

        }

    }

    init {
        // Default types
        val intType = defaultTypes[InternalTypes.Int]!!
        val stringType = defaultTypes[InternalTypes.String]!!
//        val charType = defaultTypes[InternalTypes.Char]!!
//        val floatType = defaultTypes[InternalTypes.Float]!!
        val boolType = defaultTypes[InternalTypes.Boolean]!!
        val unitType = defaultTypes[InternalTypes.Unit]!!
        val intRangeType = defaultTypes[InternalTypes.IntRange]!!
        val anyType = defaultTypes[InternalTypes.Any]!!
        val nothingType = defaultTypes[InternalTypes.Nothing]!!
        val compiler = defaultTypes[InternalTypes.Compiler]!!
//        val test = defaultTypes[InternalTypes.Test]!!

        val genericType = Type.UnknownGenericType("T")
        val differentGenericType = Type.UnknownGenericType("G")


        /// Default packages
        val commonProject = Project("common")
        val corePackage = Package("core")
        val mainPackage = Package(MAIN_PKG_NAME)

        // pkg with everything that was declared without package specification
        commonProject.packages["common"] = Package("common")
        // pkg with std types like Int
        commonProject.packages["core"] = corePackage
        // package with main function
        commonProject.packages[MAIN_PKG_NAME] = mainPackage


        /////init basic types/////
        defaultTypes.forEach { (k, v) ->
            typeTable[k.name] = v // fixed
            typeDB.addInternalType(k.name, v)
        }
        // add all default types to core package
        defaultTypes.forEach { (k, v) ->
            corePackage.types[k.name] = v
        }

        // Pair
        val pairType = Type.UserType(
            name = "Pair",
            typeArgumentList = listOf(genericType, differentGenericType),
            fields = mutableListOf(KeywordArg("first", genericType), KeywordArg("second", differentGenericType)),
            pkg = "core",
        )

        ///add collections///
        // List
        val listType = Type.UserType(
            name = "List",
            typeArgumentList = listOf(genericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        val listTypeOfDifferentGeneric = Type.UserType(
            name = "List",
            typeArgumentList = listOf(differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )

        fun addCustomTypeToDb(type: Type.UserLike, protocols: MutableMap<String, Protocol>) {
            type.protocols.putAll(
                protocols
            )

            typeTable[type.name] = type// fixed
            typeDB.addUserLike(type.name, type, createFakeToken())
            corePackage.types[type.name] = type
        }
        // Sequence
        val sequenceType = Type.UserType(
            name = "Sequence",
            typeArgumentList = listOf(genericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        val sequenceTypeOfDifferentGeneric = Type.UserType(
            name = "Sequence",
            typeArgumentList = listOf(differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )

        addCustomTypeToDb(
            sequenceType, createListProtocols(
                intType = intType,
                stringType = stringType,
                unitType = unitType,
                boolType = boolType,
                mutListType = sequenceType,
                listTypeOfDifferentGeneric = sequenceTypeOfDifferentGeneric,
                itType = genericType,
                differentGenericType = differentGenericType,
                sequenceType = sequenceType,
                pairType = pairType
            )
        )

        sequenceTypeOfDifferentGeneric.protocols.putAll(sequenceType.protocols)


        // List
        addCustomTypeToDb(
            listType,
            createListProtocols(
                intType = intType,
                stringType = stringType,
                unitType = unitType,
                boolType = boolType,
                mutListType = listType,
                listTypeOfDifferentGeneric = listTypeOfDifferentGeneric,
                itType = genericType,
                differentGenericType = differentGenericType,
                sequenceType = sequenceType,
                pairType = pairType
            )
        )

        listTypeOfDifferentGeneric.protocols.putAll(listType.protocols)


        // now when we have list type with its protocols, we add split method for String, that returns List::String
        val listOfString = createTypeListOfType("List", stringType, listType)

        listType.protocols
        stringType.protocols["common"]!!.keywordMsgs
            .putAll(listOf(createKeyword(KeywordArg("split", stringType), listOfString)))

        // add toList to IntRange
        val listOfInts = createTypeListOfType("List", intType, listType)
        val mutListOfInts = createTypeListOfType("MutableList", intType, listType)
        val intRangeProto = intRangeType.protocols["common"]!!
        val toListForIntProtocol = createUnary("toList", listOfInts)
        val toMutListForIntProtocol = createUnary("toMutableList", mutListOfInts)
        intRangeProto.unaryMsgs.putAll(arrayOf(toListForIntProtocol, toMutListForIntProtocol))
        // Mutable list
        val mutableListType = Type.UserType(
            name = "MutableList",
            typeArgumentList = listOf(genericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        val mutListTypeOfDifferentGeneric = Type.UserType(
            name = "MutableList",
            typeArgumentList = listOf(differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )

        addCustomTypeToDb(
            mutableListType, createListProtocols(
                intType = intType,
                stringType = stringType,
                unitType = unitType,
                boolType = boolType,
                mutListType = mutableListType,
                listTypeOfDifferentGeneric = mutListTypeOfDifferentGeneric,
                itType = genericType,
                differentGenericType = differentGenericType,
                sequenceType = sequenceType,
                pairType = pairType
            )
        )

        mutListTypeOfDifferentGeneric.protocols.putAll(mutableListType.protocols)


        // mutable set
        val mutableSetType = Type.UserType(
            name = "MutableSet",
            typeArgumentList = listOf(genericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        val mutSetTypeOfDifferentGeneric = Type.UserType(
            name = "MutableSet",
            typeArgumentList = listOf(differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        addCustomTypeToDb(
            mutableSetType, createSetProtocols(
                intType = intType,
                unitType = unitType,
                boolType = boolType,
                setType = mutableSetType,
                setTypeOfDifferentGeneric = mutSetTypeOfDifferentGeneric,
                itType = genericType,
                differentGenericType = differentGenericType,
                listType = listType
            )
        )

        mutSetTypeOfDifferentGeneric.protocols.putAll(mutableSetType.protocols)

        // mutable map
        val mapType = Type.UserType(
            name = "MutableMap",
            typeArgumentList = listOf(genericType, differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )
        val mapTypeOfDifferentGeneric = Type.UserType(
            name = "MutableMap",
            typeArgumentList = listOf(differentGenericType),
            fields = mutableListOf(),
            pkg = "core",
        )

        /// MapEntry

//        val mapEntryType = Type.UserType(
//            name = "Entry",
//            typeArgumentList = listOf(genericType, differentGenericType),
//            fields = mutableListOf(
//                KeywordArg("key", genericType),
//                KeywordArg("value", differentGenericType)
//            ),
//            pkg = "Map",
//        )
//        mapEntryType.isBinding = true

//        addCustomTypeToDb(
//            mapEntryType, mutableMapOf()
//        )
        ///


        addCustomTypeToDb(
            mapType, createMapProtocols(
                intType = intType,
                unitType = unitType,
                boolType = boolType,
                mapType = mapType,
                mapTypeOfDifferentGeneric = listTypeOfDifferentGeneric,
                keyType = genericType,
                valueType = differentGenericType,
                setType = mutableSetType,
                setTypeOfDifferentGeneric = mutSetTypeOfDifferentGeneric,
//                mapEntryType
            )
        )

        mapTypeOfDifferentGeneric.protocols.putAll(mapType.protocols)


//        val kotlinPkg = Package("kotlin", isBinding = true)
//        commonProject.packages["kotlin"] = kotlinPkg

        /// add Error

        /// ERROR TYPE
        val errorRootType = Type.UnionRootType(
            name = "ErrorRoot",
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            pkg = "core",
            isError = true,
            branches = listOf()
        )

        val errorType = Type.UnionBranchType(
            name = "Error",
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            pkg = "core",
            isError = true,
            root = errorRootType
        )
        errorRootType.branches = listOf(errorType)

        errorType.isBinding = true
        ///

        addCustomTypeToDb(
            errorType, createExceptionProtocols(
                errorType,
                unitType,
                nothingType,
                stringType
            )
        )

        // StringBuilder
        val stringBuilderType = Type.UserType(
            name = "StringBuilder",
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            pkg = "core",
        )
        stringBuilderType.isBinding = true

        addCustomTypeToDb(
            stringBuilderType, createStringBuilderProtocols(
                stringBuilderType,
                anyType,
                stringType
            )
        )


        // TypeType
        val typeType = Type.UserType(
            name = "TypeType",
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            pkg = "core",
        )
        val fieldsMap = createTypeMapOfType("MutableMap", stringType, typeType, mapType)

        // add fields
        typeType.fields = mutableListOf(
            KeywordArg("name", stringType),
            KeywordArg("fields", fieldsMap),
        )

        addCustomTypeToDb(
            typeType,
            mutableMapOf()
        )

        // Compiler
        compiler.protocols.putAll(
            createCompilerProtocols(
                intType = intType,
                stringType = stringType,
                typeType = typeType
            )
        )




        projects[projectName] = commonProject
    }
}

private fun Type.InternalType.copy(): Type.InternalType {
    return Type.InternalType(
        typeName = InternalTypes.valueOf(name),
        pkg = this.pkg,
        isPrivate,
        protocols
    )
}
