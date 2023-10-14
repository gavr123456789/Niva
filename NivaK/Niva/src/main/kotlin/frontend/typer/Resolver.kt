package frontend.typer

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.util.removeDoubleQuotes
import java.io.File

typealias TypeName = String


@Suppress("NOTHING_TO_INLINE")
inline fun createDefaultType(type: InternalTypes): Pair<InternalTypes, Type.InternalType> {
    return type to Type.InternalType(
        typeName = type,
        pkg = "common",
    )
}

@Suppress("UNUSED_VARIABLE")
class Resolver(
    val projectName: String,

    // statements from all files
    // if there cycle types then just remember the unresolved types and then try to resolve them again in the end
    var statements: MutableList<Statement>,

    val mainFile: File,
    val otherFilesPaths: List<File> = listOf(),

    val projects: MutableMap<String, Project> = mutableMapOf(),

    // reload when package changed
    val typeTable: MutableMap<TypeName, Type> = mutableMapOf(),

    val unaryForType: MutableMap<TypeName, MessageDeclarationUnary> = mutableMapOf(),
    val binaryForType: MutableMap<TypeName, MessageDeclarationBinary> = mutableMapOf(),
    val keywordForType: MutableMap<TypeName, MessageDeclarationKeyword> = mutableMapOf(),

    val staticUnaryForType: MutableMap<TypeName, MessageDeclarationUnary> = mutableMapOf(),
    val staticBinaryForType: MutableMap<TypeName, MessageDeclarationBinary> = mutableMapOf(),
    val staticKeywordForType: MutableMap<TypeName, MessageDeclarationKeyword> = mutableMapOf(),


    var topLevelStatements: MutableList<Statement> = mutableListOf(),
    var currentLevel: Int = 0,

//    var currentSelf: Type = Resolver.defaultBasicTypes[InternalTypes.Unit]!!,

    var currentProjectName: String = "common",
    var currentPackageName: String = "common",
    var currentProtocolName: String = "common",

    var currentFile: String = "",
    var currentArgumentNumber: Int = -1,

    // for recursive types
    val unResolvedMessageDeclarations: MutableSet<MessageDeclaration> = mutableSetOf(),
    val unResolvedTypeDeclarations: MutableSet<SomeTypeDeclaration> = mutableSetOf(),
    var allDeclarationResolvedAlready: Boolean = false
) {
    companion object {

        val defaultTypes: Map<InternalTypes, Type.InternalType> = mapOf(

            createDefaultType(InternalTypes.Int),
            createDefaultType(InternalTypes.String),
            createDefaultType(InternalTypes.Char),
            createDefaultType(InternalTypes.Float),
            createDefaultType(InternalTypes.Boolean),
            createDefaultType(InternalTypes.Unit),

            createDefaultType(InternalTypes.Project),
            createDefaultType(InternalTypes.IntRange),

            createDefaultType(InternalTypes.Any),
        )

        init {
            val intType = defaultTypes[InternalTypes.Int]!!
            val stringType = defaultTypes[InternalTypes.String]!!
            val charType = defaultTypes[InternalTypes.Char]!!
            val floatType = defaultTypes[InternalTypes.Float]!!
            val boolType = defaultTypes[InternalTypes.Boolean]!!
            val unitType = defaultTypes[InternalTypes.Unit]!!
            val intRangeType = defaultTypes[InternalTypes.IntRange]!!
            val anyType = defaultTypes[InternalTypes.Any]!!

            intType.protocols.putAll(
                createIntProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    floatType = floatType,
                    intRangeType = intRangeType,
                    anyType = anyType
                )
            )

            floatType.protocols.putAll(
                createFloatProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    floatType = floatType,
                    intRangeType = intRangeType,
                    anyType = anyType
                )
            )

            stringType.protocols.putAll(
                createStringProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    charType = charType,
                    any = anyType

                )
            )

            boolType.protocols.putAll(
                createBoolProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    any = anyType
                )
            )

            charType.protocols.putAll(
                createCharProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    charType = charType,
                    any = anyType
                )
            )

        }

    }

    init {
        // Default types
        val intType = defaultTypes[InternalTypes.Int]!!
        val stringType = defaultTypes[InternalTypes.String]!!
        val charType = defaultTypes[InternalTypes.Char]!!
        val floatType = defaultTypes[InternalTypes.Float]!!
        val boolType = defaultTypes[InternalTypes.Boolean]!!
        val unitType = defaultTypes[InternalTypes.Unit]!!
        val intRangeType = defaultTypes[InternalTypes.IntRange]!!
        val anyType = defaultTypes[InternalTypes.Any]!!
        val genericType = Type.UnknownGenericType("T")
        val differentGenericType = Type.UnknownGenericType("G")

        /// Default packages
        val commonProject = Project("common")
        val corePackage = Package("core")
        val mainPackage = Package("main")

        commonProject.packages["common"] = Package("common")
        commonProject.packages["core"] = corePackage
        commonProject.packages["main"] = mainPackage


        /////init basic types/////
        defaultTypes.forEach { (k, v) ->
            typeTable[k.name] = v
        }
        // add all default types to core package
        defaultTypes.forEach { (k, v) ->
            corePackage.types[k.name] = v
        }

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
        listType.protocols.putAll(
            createListProtocols(
                intType = intType,
                unitType = unitType,
                boolType = boolType,
                listType = listType,
                listTypeOfDifferentGeneric = listTypeOfDifferentGeneric,
                genericType = genericType,
                differentGenericType = differentGenericType
            )
        )
        listTypeOfDifferentGeneric.protocols.putAll(listType.protocols)
        typeTable[listType.name] = listType
        corePackage.types[listType.name] = listType
        // Set TODO
        // Map TODO


        ///

        projects[projectName] = commonProject

    }
}


fun Resolver.resolveDeclarationsOnly(statements: List<Statement>) {
    statements.forEach {
        if (it is Declaration)
            resolveDeclarations(it, mutableMapOf(), resolveBody = false)
    }
}

// нужен механизм поиска типа, чтобы если не нашли метод в текущем типе, то посмотреть в Any
fun Resolver.resolve(
    statements: List<Statement>,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement? = null, // since we in recursion, and can't define on what level of it, its the only way to know previous statement
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
        !(it is MessageSendKeyword && it.receiver.str == "Project")
    }.toMutableList()


    return statements
}

fun Resolver.resolveDeclarations(
    statement: Declaration,
    previousScope: MutableMap<String, Type>,
    resolveBody: Boolean = true,
) {
    currentLevel += 1

    when (statement) {
        is TypeDeclaration -> {
            val newType = statement.toType(currentPackageName, typeTable)
            addNewType(newType, statement)
        }

        is UnionDeclaration -> {
            val root = statement.toType(currentPackageName, typeTable, isUnion = true) as Type.UserUnionRootType


            addNewType(root, statement)
            val branches = mutableListOf<Type.UserUnionBranchType>()
            statement.branches.forEach {
                val branchType = it.toType(currentPackageName, typeTable, root = root) as Type.UserUnionBranchType
                branchType.parent = root
                branchType.fields += root.fields
                branches.add(branchType)

                addNewType(branchType, it)
            }
            root.branches = branches
        }

        is AliasDeclaration -> TODO()

        is MessageDeclaration -> {
            // check if the type already registered
            val forType = typeTable[statement.forType.name]
            if (forType == null) {
                unResolvedMessageDeclarations.add(statement) // statement.token.compileError("type ${statement.forType.name} is not registered")
                currentLevel--
                return
            } else {
                unResolvedMessageDeclarations.remove(statement)
            }

            // check that there is no field with the same name (because of getter has the same signature)
            if (forType is Type.UserType) {
                val q = forType.fields.find { it.name == statement.name }
                if (q != null) {
                    statement.token.compileError("Type ${statement.forType.name} already has field with name ${statement.name}")
                }
            }

            val bodyScope = mutableMapOf<String, Type>()
            when (statement) {
                is MessageDeclarationUnary -> addNewUnaryMessage(statement)
                is MessageDeclarationBinary -> addNewBinaryMessage(statement)
                is MessageDeclarationKeyword -> {
                    statement.args.forEach {
                        val astType = it.type!!
                        val type = astType.toType(typeTable, it.name)

                        if (type.name == it.type.name) {
                            bodyScope[it.name] = type
                        } else {
                            bodyScope[it.name] = type
                        }
                        if (type is Type.UnknownGenericType) {
                            statement.typeArgs.add(type.name)
                        }
                        if (type is Type.UserType && type.typeArgumentList.isNotEmpty()) {
                            statement.typeArgs.addAll(type.typeArgumentList.map { typeArg -> typeArg.name })
                            if (type.name == it.type.name) {
                                bodyScope[it.name] = type
                            }
                        }

                    }
                    addNewKeywordMessage(statement)
                }

                is ConstructorDeclaration -> addStaticDeclaration(statement)
            }


            if (resolveBody) {
                bodyScope["this"] = forType
                val previousAndCurrentScope = (previousScope + bodyScope).toMutableMap()
                this.resolve(statement.body, previousAndCurrentScope, statement)

            }
            // TODO check that return type is the same as declared return type, or if it not declared -> assign it

        }

        is UnionBranch -> {
            println("Union branch???")
        }
    }
    currentLevel -= 1
}


private fun Resolver.resolveStatement(
    statement: Statement,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {
    val resolveTypeForMessageSend = { statement2: MessageSend ->
        if (statement2.receiver.str != "Project") {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            this.resolve(statement2.messages, previousAndCurrentScope, statement2)

            // TODO check then return parameter of each send match the next input parameter
            if (statement2.messages.isNotEmpty()) {
                statement2.type =
                    statement2.messages.last().type
                        ?: statement2.token.compileError("Not all messages of ${statement2.str} has types")
            } else {
                // every single expressions is unary message without messages
                if (statement2.type == null) {
                    currentLevel++
                    resolve(listOf(statement2.receiver), previousAndCurrentScope, statement2)
                    currentLevel--
                }
                statement2.type = statement2.receiver.type
                    ?: statement2.token.compileError("Can't find type for ${statement2.str} on line ${statement2.token.line}")
            }
        } else {
            // add to the current project
            assert(statement2.messages.count() == 1)
            val keyword = statement2.messages[0] as KeywordMsg

            keyword.args.forEach {
                if (it.keywordArg.token.kind == TokenType.String) {
                    val substring = it.keywordArg.token.lexeme.removeDoubleQuotes()
                    when (it.selectorName) {
                        "name" -> changeProject(substring, statement2.token)
                        "package" -> changePackage(substring, statement2.token)
                        "protocol" -> changeProtocol(substring)
                    }
                } else it.keywordArg.token.compileError("Only string arguments for Project allowed")
            }
        }

    }

    when (statement) {
        is Declaration -> {
            if (!allDeclarationResolvedAlready)
                resolveDeclarations(statement, previousScope, true)
            else if (statement is MessageDeclaration) {
                // after first type check, only body of messages remains unresolved
                currentLevel++
                // add this to scope
                // add arguments to scope, if this is binary or keyword
                val bodyScope = mutableMapOf<String, Type>()
                val forType = typeTable[statement.forType.name]!!

                bodyScope["this"] = forType

                when (statement) {
                    is MessageDeclarationUnary -> {}
                    is MessageDeclarationBinary -> {
                        bodyScope[statement.arg.name] = statement.arg.type!!.toType(typeTable)
                    }

                    is MessageDeclarationKeyword -> {

                        statement.args.forEach {
                            val astType = it.type!!
                            val type = astType.toType(typeTable, it.name)

                            if (type.name == it.type.name) {
                                bodyScope[it.name] = type
                            } else {
                                bodyScope[it.name] = type
                            }
                            if (type is Type.UnknownGenericType) {
                                statement.typeArgs.add(type.name)
                            }
                            if (type is Type.UserType && type.typeArgumentList.isNotEmpty()) {
                                statement.typeArgs.addAll(type.typeArgumentList.map { typeArg -> typeArg.name })
                                if (type.name == it.type.name) {
                                    bodyScope[it.name] = type
                                }
                            }

                        }
                    }

                    is ConstructorDeclaration -> {}
                }
                resolve(statement.body, (previousScope + bodyScope).toMutableMap())
                currentLevel--

            }
        }

        is VarDeclaration -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            // currentNode, depth + 1
            currentLevel++
            resolve(listOf(statement.value), previousAndCurrentScope, statement)
            currentLevel--

            val valueType = statement.value.type
                ?: statement.token.compileError("In var declaration ${statement.name} value doesn't got type")
            val statementDeclaredType = statement.valueType

            // check that declared type == inferred type
            if (statementDeclaredType != null) {
                if (statementDeclaredType.name != valueType.name) {
                    val text = "${statementDeclaredType.name} != ${valueType.name}"
                    statement.token.compileError("Type declared for ${statement.name} is not equal for it's value type($text)")
                }
            }

            currentScope[statement.name] = valueType

            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }

        }

        is Message -> {
            resolveMessage(statement, previousScope, currentScope)
        }

        is MessageSend -> {
            resolveTypeForMessageSend(statement)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }


        is IdentifierExpr -> {
            getTypeForIdentifier(statement, previousScope, currentScope)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is ExpressionInBrackets -> {
            resolveExpressionInBrackets(statement, previousScope, currentScope)
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is CodeBlock -> {
            resolveCodeBlock(statement, previousScope, currentScope, rootStatement)
        }

        is ListCollection -> {

            if (statement.initElements.isNotEmpty()) {
                val q = statement.initElements[0]
                if (q.typeAST != null) {
                    val w = q.typeAST.toType(typeTable)
                    w.beforeGenericResolvedName = "T" // Default List has T type
                    val listType =
                        this.projects["common"]!!.packages["core"]!!.types["List"] as Type.UserType// Resolver.defaultTypes[InternalTypes.List]!!

                    // try to find list with the same generic type
                    val typeName = "List"
                    val currentPkg = getCurrentPackage(statement.token)
                    val alreadyExistsListType = currentPkg.types[typeName]

                    val listProtocols = listType.protocols

                    val genericType = alreadyExistsListType ?: Type.UserType(
                        name = typeName,
                        typeArgumentList = listOf(w),
                        fields = mutableListOf(),
                        pkg = currentPackageName,
                        protocols = listProtocols
                    )

//                    if (alreadyExistsListType == null) {
//                        addNewType(genericType, null, currentPkg)
//                    }

                    statement.type = genericType
                } else {
                    statement.token.compileError("Cant get type of elements of list literal")
                }
            }

            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }
        }


        is LiteralExpression.FloatExpr ->
            statement.type = Resolver.defaultTypes[InternalTypes.Float]

        is LiteralExpression.IntExpr ->
            statement.type = Resolver.defaultTypes[InternalTypes.Int]

        is LiteralExpression.StringExpr ->
            statement.type = Resolver.defaultTypes[InternalTypes.String]

        is LiteralExpression.TrueExpr ->
            statement.type = Resolver.defaultTypes[InternalTypes.Boolean]

        is LiteralExpression.FalseExpr ->
            statement.type = Resolver.defaultTypes[InternalTypes.Boolean]

        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType -> {}

        is ControlFlow -> {
            resolveControlFlow(statement, previousScope, currentScope, rootStatement)
        }

        is Assign -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            resolve(listOf(statement.value), previousAndCurrentScope, statement)



            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }
        }

        is ReturnStatement -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            resolve(listOf(statement.expression), previousAndCurrentScope, statement)

            val q = statement.expression.type!!
            if (rootStatement is MessageDeclaration) {
                val w = rootStatement.returnType?.toType(typeTable)
                if (w != null) {
                    val isReturnTypeEqualToReturnExprType = compare2Types(q, w)
                    if (!isReturnTypeEqualToReturnExprType) {
                        statement.token.compileError("Return type is ${w.name} is not equal to what you returning ${q.name}")
                    }

                }

            }

        }

        else -> {

        }
    }
}

fun Resolver.resolveExpressionInBrackets(
    statement: ExpressionInBrackets,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type {
    if (statement.statements.isEmpty()) {
        statement.token.compileError("Parens must contain expression")
    }
    val lastExpr = statement.statements.last()
    if (lastExpr !is Expression) {
        statement.token.compileError("Last statement inside parens must be expression")
    }

    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
    resolve(statement.statements, previousAndCurrentScope, statement)
    statement.type = lastExpr.type
    return lastExpr.type!!
}


fun Resolver.compare2Types(type1: Type, type2: Type): Boolean {
    // TODO temp
    if (type1 is Type.Lambda || type2 is Type.Lambda) {
        return true
    }

    return type1.name == type2.name
}

fun Resolver.findUnaryMessageType(receiverType: Type, selectorName: String, token: Token): Type {

    fun findUnary(receiverType: Type, selectorName: String, token: Token): Type? {
        receiverType.protocols.forEach { (_, v) ->
            val q = v.unaryMsgs[selectorName]

            if (q != null) {
                // TODO! add unisng of unary
                val pkg = getCurrentPackage(token)
                pkg.currentImports.add(receiverType.pkg)
                return q.returnType
            }
        }
        return null
    }

    val result = findUnary(receiverType, selectorName, token)
    if (result != null)
        return result

    var parent: Type? = receiverType.parent
    while (parent != null) {
        val parentResult = findUnary(parent, selectorName, token)
        if (parentResult != null)
            return parentResult
        parent = parent.parent
    }

    println("Sas???")
    token.compileError("Cant find unary message: $selectorName for type ${receiverType.name}")
}

fun Resolver.findStaticMessageType(receiverType: Type, selectorName: String, token: Token): Type {
    receiverType.protocols.forEach { (_, v) ->
        val q = v.staticMsgs[selectorName]
        if (q != null) {
            // TODO! add unisng of binary
            val pkg = getCurrentPackage(token)
            pkg.currentImports.add(receiverType.pkg)
            return q.returnType
        }
    }
    token.compileError("Cant find static message: $selectorName for type ${receiverType.name}")
}

fun Resolver.findBinaryMessageType(receiverType: Type, selectorName: String, token: Token): Type {
    if (receiverType.name.length == 1 && receiverType.name[0].isUpperCase()) {
        throw Exception("Can't receive generic type to find binary method for it")
    }
    receiverType.protocols.forEach { (_, v) ->
        val q = v.binaryMsgs[selectorName]
        if (q != null) {
            // TODO! add unisng of keyword
            val pkg = getCurrentPackage(token)
            pkg.currentImports.add(receiverType.pkg)
            return q.returnType
        }
    }
    token.compileError("Cant find binary message: $selectorName for type ${receiverType.name}")
}

fun Resolver.findKeywordMsgType(receiverType: Type, selectorName: String, token: Token): KeywordMsgMetaData {
    if (receiverType.name.length == 1 && receiverType.name[0].isUpperCase()) {
        throw Exception("Can't receive generic type to find keyword method for it")
    }

    receiverType.protocols.forEach { (_, v) ->
        val q = v.keywordMsgs[selectorName]
        if (q != null) {
            // TODO! add unisng of keyword
            val pkg = getCurrentPackage(token)
            pkg.currentImports.add(receiverType.pkg)
            return q
        }
    }
    token.compileError("Cant find keyword message: $selectorName for type ${receiverType.name}")
}


fun Resolver.getPackage(packageName: String, token: Token): Package {
    val p = this.projects[currentProjectName] ?: token.compileError("there are no such project: $currentProjectName")
    val pack = p.packages[packageName] ?: token.compileError("there are no such package: $packageName")
    return pack
}


fun Resolver.getCurrentProtocol(typeName: String, token: Token): Protocol {
    val pack = getPackage(currentPackageName, token)
    val type2 = pack.types[typeName]
        ?: getPackage("common", token).types[typeName]
        ?: getPackage("core", token).types[typeName]
        ?: token.compileError("there are no such type: $typeName in package $currentPackageName in project: $currentProjectName")

    val protocol =
        type2.protocols[currentProtocolName]
    if (protocol == null) {
        val newProtocol = Protocol(currentProtocolName)
        type2.protocols[currentProtocolName] = newProtocol
        return newProtocol
    }
    return protocol
}

fun Resolver.getCurrentPackage(token: Token) = getPackage(currentPackageName, token)


fun Resolver.addStaticDeclaration(statement: ConstructorDeclaration) {
    val typeOfReceiver = typeTable[statement.forType.name]!!
    when (statement.msgDeclaration) {
        is MessageDeclarationUnary -> {
            staticUnaryForType[statement.name] = statement.msgDeclaration
            val protocol = getCurrentProtocol(statement.forType.name, statement.token)
            val messageData = UnaryMsgMetaData(
                name = statement.msgDeclaration.name,
                returnType = typeOfReceiver,
            )
            protocol.staticMsgs[statement.name] = messageData
        }

        is MessageDeclarationBinary -> {
            statement.token.compileError("Binary static message, really? This is not allowed")
        }

        is MessageDeclarationKeyword -> {
            staticKeywordForType[statement.name] = statement.msgDeclaration
            val protocol = getCurrentProtocol(statement.forType.name, statement.token)
            val messageData = UnaryMsgMetaData(
                name = statement.msgDeclaration.name,
                returnType = typeOfReceiver,
            )
            protocol.staticMsgs[statement.name] = messageData
        }

        is ConstructorDeclaration -> TODO()
    }
    addMsgToPackageDeclarations(statement)

}

fun Resolver.addNewUnaryMessage(statement: MessageDeclarationUnary) {
    unaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name, statement.token)
    val messageData = statement.toMessageData(typeTable)
    protocol.unaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewBinaryMessage(statement: MessageDeclarationBinary) {
    binaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name, statement.token)
    val messageData = statement.toMessageData(typeTable)
    protocol.binaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewKeywordMessage(statement: MessageDeclarationKeyword) {
    keywordForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name, statement.token)
    val messageData = statement.toMessageData(typeTable)
    protocol.keywordMsgs[statement.name] = messageData

    // add msg to package declarations
    addMsgToPackageDeclarations(statement)
}

fun Resolver.addMsgToPackageDeclarations(statement: Declaration) {
    val pack = getPackage(currentPackageName, statement.token)
    pack.declarations.add(statement)
}

fun createFakeToken(): Token = Token(
    TokenType.Identifier, "!!!Nothing!!!", 0, Position(0, 1),
    Position(0, 1), File("Nothing")
)

fun Resolver.addNewType(type: Type, statement: SomeTypeDeclaration?, pkg: Package? = null) {
    val pack = pkg ?: getPackage(currentPackageName, statement?.token ?: createFakeToken())
    if (pack.types.containsKey(type.name)) {
        throw Exception("Type ${type.name} already registered in project: $currentProjectName in package: $currentPackageName")
    }

    if (statement != null) {
        pack.declarations.add(statement)
    }

    pack.types[type.name] = type
    typeTable[type.name] = type
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

fun Resolver.changePackage(newCurrentPackage: String, token: Token) {
    currentPackageName = newCurrentPackage

    val currentProject = projects[currentProjectName] ?: token.compileError("Can't find project: $currentProjectName")

    val alreadyExistsPack = currentProject.packages[newCurrentPackage]

    // check that this package not exits already
    if (alreadyExistsPack != null) {
        // load table of types
        typeTable.clear()
        typeTable.putAll(alreadyExistsPack.types)
    } else {
        // create this new package
        val pack = Package(
            packageName = newCurrentPackage
        )
        currentProject.packages[newCurrentPackage] = pack
    }

}

fun Resolver.changeProtocol(protocolName: String) {
    currentProtocolName = protocolName
}

fun Resolver.getTypeForIdentifier(
    x: IdentifierExpr,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type {
    val type = typeTable[x.str]
        ?: currentScope[x.str]
        ?: previousScope[x.str]
        ?: x.token.compileError("Unresolved reference: ${x.str}")
    x.type = type

    return type
}


fun IfBranch.getReturnTypeOrThrow(): Type = when (this) {
    is IfBranch.IfBranchSingleExpr -> {
        this.thenDoExpression.type!!
    }

    is IfBranch.IfBranchWithBody -> {
        (this.body.last() as Expression).type!!
    }
}
