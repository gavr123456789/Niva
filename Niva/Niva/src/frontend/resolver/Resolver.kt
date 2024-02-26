@file:Suppress("EnumEntryName", "NAME_SHADOWING")

package frontend.resolver

import codogen.GeneratorKt
import frontend.meta.Token
import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.resolver.Type.RecursiveType.copy
import frontend.resolver.messageResolving.resolveCodeBlock
import frontend.util.createFakeToken
import frontend.util.div
import main.*
import main.frontend.typer.*
import java.io.File
import java.util.Stack

private fun Resolver.addPrintingInfoAboutType(type: Type, printOnlyTypeName: Boolean) {
//    infoTypesToPrint.add(type)
    infoTypesToPrint[type] = printOnlyTypeName
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
                this.resolve(statement2.messages, previousAndCurrentScope, statement2)

                if (statement2.messages.isNotEmpty()) {
                    statement2.type =
                        statement2.messages.last().type
                            ?: statement2.token.compileError("Not all messages of $YEL${statement2.str} ${WHITE}has types")
                } else {
                    // every single expressions is unary message without messages
                    if (statement2.type == null) {
                        currentLevel++
                        resolve(listOf(statement2.receiver), previousAndCurrentScope, statement2)
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
            resolveVarDeclaration(statement, previousScope, currentScope)
        }

        is Message -> {
            resolveMessage(statement, previousScope, currentScope)
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
            if (currentLevel == 0)
                topLevelStatements.add(statement)
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

            getTypeForIdentifier(
                statement, previousScope, currentScope, kw
            )
            val type = statement.type

            // This Identifier is Type, like Person
            if (type != null && type !is Type.UserEnumRootType && rootStatement !is ControlFlow) {
                if (statement.isInfoRepl) {
                    addPrintingInfoAboutType(type, statement.str != type.name)
                }

                statement.isType = true
            }

            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is ExpressionInBrackets -> {
            resolveExpressionInBrackets(statement, (previousScope + currentScope).toMutableMap())
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is CodeBlock -> {
            resolveCodeBlock(statement, previousScope, currentScope, rootStatement)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is ListCollection -> {
            resolveCollection(statement, "MutableList", (previousScope + currentScope).toMutableMap(), rootStatement)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is SetCollection -> {
            resolveSet(statement, (previousScope + currentScope).toMutableMap(), rootStatement)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is MapCollection -> {
            resolveMap(statement, rootStatement, previousScope, currentScope)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

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

        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType -> {}

        is ControlFlow -> {
            resolveControlFlow(statement, previousScope, currentScope, rootStatement)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is Assign -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            currentLevel++
            resolve(listOf(statement.value), previousAndCurrentScope, statement)
            currentLevel--

            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is ReturnStatement -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            val expr = statement.expression
            if (expr != null) {
                resolve(listOf(expr), previousAndCurrentScope, statement)
                if (expr.type == null) {
                    throw Exception("Cant infer type of return statement on line: ${expr.token.line}")
                }
            }
            val unit = Resolver.defaultTypes[InternalTypes.Unit]!!
            val q = if (expr == null) unit else expr.type!!
            wasThereReturn = q
        }

        is DotReceiver -> {
            statement.type = findThisInScopes(statement.token, currentScope, previousScope)
        }

    }
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
    }

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


// if this is compare for assign, then type1 = type2, so if t1 is nullable, and t2 is null, it's true
fun compare2Types(type1: Type, type2: Type, token: Token? = null, unpackNull: Boolean = false, isOut: Boolean = false): Boolean {
    if (type1 === type2) return true

    if (type1 is Type.Lambda && type2 is Type.Lambda) {
        if (type1.args.count() != type2.args.count()) {
            token?.compileError("Codeblock `${YEL}${type1.name}${RESET}` has ${CYAN}${type1.args.count()}${RESET} arguments but `${YEL}${type2.name}${RESET}` has ${CYAN}${type2.args.count()}")
            return false
        }

        // temp for adding "(k,v)" for map, filter for hash maps
        if (type2.specialFlagForLambdaWithDestruct) {
            type1.specialFlagForLambdaWithDestruct = true
        }
        if (type1.specialFlagForLambdaWithDestruct) {
            type2.specialFlagForLambdaWithDestruct = true
        }
        //

        type1.args.forEachIndexed { i, it ->
            val it2 = type2.args[i]
            val isEqual = compare2Types(it.type, it2.type)
            if (!isEqual) {
                token?.compileError("argument $WHITE${it.name}${RESET} has type ${YEL}${it.type}${RESET} but ${WHITE}${it2.name}${RESET} has type ${YEL}${it2.type}")
                return false
            }
        }

        // check return types
        val return1 = type1.returnType
        val return2 = type2.returnType
        val isReturnTypesEqual =
            (return2.name == InternalTypes.Unit.name || return1.name == InternalTypes.Unit.name) || compare2Types(
                return1,
                return2,
                token,
                unpackNull
            )
        if (!isReturnTypesEqual) {
            token?.compileError("return types are not equal: ${YEL}$type1 ${RESET}!= ${YEL}$type2")
        }

        return true
    }

    // one of the types is top type Any
    if (type1.name == InternalTypes.Any.name || type2.name == InternalTypes.Any.name) {
        return true
    }


    // if one of them is generic
    if ((type1 is Type.UnknownGenericType && type2 !is Type.UnknownGenericType ||
        type2 is Type.UnknownGenericType && type1 !is Type.UnknownGenericType)
    ) {
        if (!isOut)
            return true // getting T but got Int, is OK
        if (isOut)
            return false // -> T, but Int returned
    }

    // if both are generics of the different letters
    if (type1 is Type.UnknownGenericType && type2 is Type.UnknownGenericType && type1.name != type2.name) {
        // if we return than its wrong
        // -> G, but T is returned
        // if we get than it's ok
        if (isOut)
            return false // returning T, but -> G declarated
        if (!isOut)
            return true // getting T, but got G is OK
    }


    val pkg1 = type1.pkg
    val pkg2 = type2.pkg
    val isDifferentPkgs = pkg1 != pkg2 && pkg1 != "core" && pkg2 != "core"

    if (type1 is Type.UserLike && type2 is Type.UserLike) {
        // if types from different packages, and it's not core

        if (isDifferentPkgs) {
            token?.compileError("$YEL$type1$RESET is from $WHITE$pkg1$RESET pkg, and $YEL$type2$RESET from $WHITE$pkg2")
            return false
        }

        // if both types has generic params
        if (type1.typeArgumentList.isNotEmpty() && type2.typeArgumentList.isNotEmpty()) {
            val args1 = type1.typeArgumentList
            val args2 = type2.typeArgumentList
            if (args1.count() != args2.count()) {
                token?.compileError("Types: $YEL$type1$RESET and $YEL$type2$RESET have a different number of generic parameters")
                return false
            }

            val isSameNames = type1.toString() == type2.toString()
            args1.forEachIndexed { index, arg1 ->
                val arg2 = args2[index]
                if (isSameNames) {
                    if (arg1 is Type.UnknownGenericType) {
                        type1.typeArgumentList = type2.typeArgumentList
                        return true
                    }
                    if (arg2 is Type.UnknownGenericType) {
                        type2.typeArgumentList = type1.typeArgumentList
                        return true
                    }
                }

                val sameArgs = compare2Types(arg1, arg2, token)
                if (!sameArgs) {
                    token?.compileError("Generic argument of type: $YEL${type1.name} $WHITE$arg1$RESET != $WHITE$arg2$RESET from type $YEL${type2.name}")
                    throw Exception("Generic argument of type: $YEL${type1.name} $WHITE$arg1$RESET != $WHITE$arg2$RESET from type $YEL${type2.name}")
                } else
                    return sameArgs // List::Int and List::T are the same
            }
        }


        // first is parent of the second
        var parent1: Type? = type1.parent
        while (parent1 != null) {
            if (compare2Types(type2, parent1)) {
                return true
            }
            parent1 = parent1.parent
        }
        // second is parent of the first
        var parent2: Type? = type2.parent
        while (parent2 != null) {
            if (compare2Types(type1, parent2)) {
                return true
            }
            parent2 = parent2.parent
        }
    }


    // x::Int? = null
    if (type1 is Type.NullableType && type2 is Type.InternalType && type2.name == "Null") {
        return true
    }

    // Ins sas -> Int? = ^42
    if (unpackNull) {
        if ((type1 is Type.NullableType && type2 !is Type.NullableType)) {
            val win = compare2Types(type1.realType, type2, token)
            if (win) return true
        }
        if ((type2 is Type.NullableType && type1 !is Type.NullableType)) {
            val win = compare2Types(type1, type2.realType, token)
            if (win) return true
        }
    }


    if (type1.toString() == type2.toString() && !isDifferentPkgs) {
        return true
    }

    // comparing with nothing is always true, its bottom type, subtype of all types,
    // so we can return nothing from switch expr branches, beside u cant do it with different types
    val nothing = Resolver.defaultTypes[InternalTypes.Nothing]

    return type1 == nothing || type2 == nothing
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

//fun Resolver.findAnyMessage(receiverType: Type)


fun Resolver.getPackage(packageName: String, token: Token): Package {
    val p = this.projects[currentProjectName] ?: token.compileError("There are no such project: $currentProjectName")
    val pack = p.packages[packageName] ?: token.compileError("There are no such package: $packageName")
    return pack
}


fun Resolver.getCurrentProtocol(type: Type, token: Token, customPkg: Package? = null): Pair<Protocol, Package> {
    val pack = customPkg ?: getPackage(currentPackageName, token)
//    val type =
//        pack.types[typeName]
//        ?: getPackage("common", token).types[typeName]
//        ?: getPackage("core", token).types[typeName]
//        ?: token.compileError("There are no such type: $YEL$typeName${RESET} in package $WHITE$currentPackageName${RESET} in project: $WHITE$currentProjectName${RESET}")

    val protocol = type.protocols[currentProtocolName]

    if (protocol == null) {
        val newProtocol = Protocol(currentProtocolName)
        type.protocols[currentProtocolName] = newProtocol
        return Pair(newProtocol, pack)
    }
    return Pair(protocol, pack)
}

fun Resolver.getCurrentPackage(token: Token) = getPackage(currentPackageName, token)
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
                        ?: statement.token.compileError("Type of keyword message $YEL${statement.msgDeclaration.name}${RESET}'s arg $WHITE${it.name}${RESET} not registered")
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
    forType: Type? = null
): MessageMetadata {
    val customPkg = if (st.forTypeAst is TypeAST.UserType && st.forTypeAst.names.count() > 1) {
        getPackage(st.forTypeAst.names.dropLast(1).joinToString("."), st.token)
    } else null

    val type =
        if (forType is Type.UnknownGenericType)
            Resolver.defaultTypes[InternalTypes.UnknownGeneric]!!
        else
            st.forType ?: st.token.compileError("Compiler error, type for $st not resolved")

//    val realType =
//        if (forType is Type.UnknownGenericType) Resolver.defaultTypes[InternalTypes.UnknownGeneric]!! else forType
    val (protocol, pkg) = getCurrentProtocol(type, st.token, customPkg)

    val messageData = st.toAnyMessageData(typeDB, typeTable, pkg, isGetter, this)


    when (st) {
        is MessageDeclarationUnary -> protocol.unaryMsgs[st.name] = messageData as UnaryMsgMetaData
        is MessageDeclarationBinary -> protocol.binaryMsgs[st.name] = messageData as BinaryMsgMetaData
        is MessageDeclarationKeyword -> protocol.keywordMsgs[st.name] = messageData as KeywordMsgMetaData
        is ConstructorDeclaration -> {
            // st.toAnyMessageData already adding static to db
        }
    }


    addMsgToPackageDeclarations(st)
    return messageData
}

fun Resolver.addMsgToPackageDeclarations(statement: MessageDeclaration) {
    val pack = getPackage(currentPackageName, statement.token)
    pack.declarations.add(statement)

    pack.addImport(statement.forType!!.pkg)
}

fun Resolver.typeAlreadyRegisteredInCurrentPkg(type: Type, pkg: Package? = null, token: Token? = null): Type.UserLike? {
    val pack = pkg ?: getCurrentPackage(token ?: createFakeToken())
    val result = pack.types[type.name]
    return result as? Type.UserLike
}

fun Resolver.addNewType(
    type: Type,
    statement: SomeTypeDeclaration?,
    pkg: Package? = null,
    checkedOnUniq: Boolean = false
) {
    val pack = pkg ?: getCurrentPackage(
        statement?.token ?: createFakeToken()
    ) // getPackage(currentPackageName, statement?.token ?: createFakeToken())

    if (!checkedOnUniq && typeAlreadyRegisteredInCurrentPkg(type, pkg, statement?.token) != null) {
        val err = "Type ${YEL}${type.name}${RESET} already registered in package: $WHITE$currentPackageName"
        statement?.token?.compileError(err) ?: throw Exception(err)
    }

    if (statement != null) {
        pack.declarations.add(statement)
    }

    if (pack.isBinding && type is Type.UserLike) {
        type.isBinding = true
    }
    pack.types[type.name] = type
    typeTable[type.name] = type//fix
    typeDB.add(type, token = statement?.token ?: createFakeToken())
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
) {
    currentPackageName = newCurrentPackage
    val currentProject =
        projects[currentProjectName] ?: token.compileError("Can't find project: $WHITE$currentProjectName")
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
            neededImports = neededImports ?: mutableSetOf()
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
        else -> token.compileError("There is no such target as $WHITE$target${RESET}, supported targets are $WHITE${CompilationTarget.entries.map { it.name }}${RESET}, default: ${WHITE}jvm")
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
        else -> token.compileError("There is no such compilation mode as $WHITE$mode, supported targets are $WHITE${CompilationMode.entries.map { it.name }}${RESET}, default: ${WHITE}debug")
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
        getType2(x.names.first(), currentScope, previousScope, kw) ?: getType2(x.name, currentScope, previousScope, kw)
//    else getType(
//        x.name,
//        currentScope,
//        previousScope
//    )
        ?: x.token.compileError("Unresolved reference: $WHITE${x.str}")

    x.type = type
    return type
}


fun Resolver.getType2(
    typeName: String,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    statement: KeywordMsg?
): Type? {
    val typeFromDb = typeDB.getType(typeName, currentScope, previousScope)
    val currentPackage = getCurrentPackage(statement?.token ?: createFakeToken())

    val type = typeFromDb.getTypeFromTypeDBResultConstructor(statement, currentPackage.imports, currentPackageName)
    return type
}


fun IfBranch.getReturnTypeOrThrow(): Type = when (this) {
    is IfBranch.IfBranchSingleExpr -> this.thenDoExpression.type!!
    is IfBranch.IfBranchWithBody -> body.type!!
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


@Suppress("UNUSED_VARIABLE")
class Resolver(
    val projectName: String,

    var statements: MutableList<Statement>,

//    val mainFile: File,
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

    val stack: Stack<MessageSend> = Stack()
) {
    companion object {

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
                    floatType = floatType,
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
        val genericType = Type.UnknownGenericType("T")
        val differentGenericType = Type.UnknownGenericType("G")
        val compiler = defaultTypes[InternalTypes.Compiler]!!


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
            fields = mutableListOf(TypeField("first", genericType), TypeField("second", differentGenericType)),
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

        fun addCustomTypeToDb(type: Type.UserType, protocols: MutableMap<String, Protocol>) {
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
//                TypeField("key", genericType),
//                TypeField("value", differentGenericType)
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
        val errorType = Type.UserType(
            name = "Error",
            typeArgumentList = listOf(),
            fields = mutableListOf(TypeField("message", stringType)),
            pkg = "core",
        )
        errorType.isBinding = true

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
            TypeField("name", stringType),
            TypeField("fields", fieldsMap),
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

//class TypeType(
//    val name: String,
//    val fields: MutableMap<String, TypeType>
//)
