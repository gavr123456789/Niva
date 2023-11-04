package frontend.typer

import codogen.GeneratorKt
import codogen.collectAllGenericsFromBranches
import codogen.loadPackages
import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
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
    var allDeclarationResolvedAlready: Boolean = false,

    val generator: GeneratorKt = GeneratorKt()
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
            createDefaultType(InternalTypes.Bind),
            createDefaultType(InternalTypes.IntRange),

            createDefaultType(InternalTypes.Any),
            createDefaultType(InternalTypes.Nothing),
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
            anyType.protocols.putAll(
                createAnyProtocols(
                    unitType = unitType,
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
        val nothingType = defaultTypes[InternalTypes.Nothing]!!
        val genericType = Type.UnknownGenericType("T")
        val differentGenericType = Type.UnknownGenericType("G")

        /// Default packages
        val commonProject = Project("common")
        val corePackage = Package("core")
        val mainPackage = Package("main")

        // pkg with everything that was declared without package specification
        commonProject.packages["common"] = Package("common")
        // pkg with std types like Int
        commonProject.packages["core"] = corePackage
        // package with main function
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
                genericTypeOfListElements = genericType,
                differentGenericType = differentGenericType
            )
        )
        listTypeOfDifferentGeneric.protocols.putAll(listType.protocols)
        typeTable[listType.name] = listType
        corePackage.types[listType.name] = listType
        // Set TODO
        // Map TODO


//        val kotlinPkg = Package("kotlin", isBinding = true)
//        commonProject.packages["kotlin"] = kotlinPkg

        /// add Error
        val errorType = Type.UserType(
            name = "Error",
            typeArgumentList = listOf(),
            fields = mutableListOf(TypeField("message", stringType)),
            pkg = "kotlin",
        )
        errorType.isBinding = true
        errorType.protocols.putAll(
            createExceptionProtocols(
                errorType,
                unitType,
                nothingType,
                stringType
            )
        )
//        kotlinPkg.types["Exception"] = exceptionType


        typeTable[errorType.name] = errorType
        corePackage.types[errorType.name] = errorType

        projects[projectName] = commonProject

    }
}

//fun MessageSendKeyword.toMessageDeclarationKeyword(): MessageDeclarationKeyword {
//    val msg = messages[0]
//    if (this.messages.count() > 0 || msg !is KeywordMsg) {
//        this.token.compileError("Can't translate msg send to msg declaration")
//    }
//
//    val result = MessageDeclarationKeyword(
//        name = msg.selectorName,
//        forType = TypeAST(
//            name = receiver.str,
//            is
//        )
//    )
//}

fun Resolver.resolveDeclarationsOnly(statements: List<Statement>) {
    val savedPackageName = currentPackageName
    statements.forEach {
        if (it is Declaration) {
            changePackage(savedPackageName, createFakeToken())
            resolveDeclarations(it, mutableMapOf(), resolveBody = false)
        }
        if (it is MessageSendKeyword && it.receiver.str == "Bind") {
            val msg = it.messages[0]
            if (msg !is KeywordMsg)
                it.token.compileError("Bind must have keyword message")
            if (msg.args.count() < 2)
                it.token.compileError("Bind must have at least 2 argument: package and content")

            val pkgArg = msg.args.find { it.selectorName == "package" }
            if (pkgArg == null)
                msg.token.compileError("'package' param is missing")

            val contentArg = msg.args.find { it.selectorName == "content" }
            if (contentArg == null)
                msg.token.compileError("'content' param is missing")



            if (pkgArg.keywordArg !is LiteralExpression)
                pkgArg.keywordArg.token.compileError("Package argument must be a string")
            if (contentArg.keywordArg !is CodeBlock)
                contentArg.keywordArg.token.compileError("Content argument must be a code block with type and method declarations")


            val pkgName = pkgArg.keywordArg.toString()

            changePackage(pkgName, it.token, true)
            val declarations = contentArg.keywordArg.statements
            declarations.forEach {
                if (it is Declaration) {
                    resolveDeclarations(it, mutableMapOf(), resolveBody = false)
                } else {
                    it.token.compileError("There can be only declarations inside Bind, but found $it")
                }
            }

            val gettersArg = msg.args.find { it.selectorName == "getters" }
            if (gettersArg != null) {
                if (gettersArg.keywordArg !is CodeBlock)
                    gettersArg.keywordArg.token.compileError("Getter argument must be a code block with type and method declarations")
                val gettersDeclarations = gettersArg.keywordArg.statements
                gettersDeclarations.forEach { getter ->

                    if (getter !is MessageDeclarationUnary) {
                        getter.token.compileError("Union declaration expected")
                    }
                    addNewUnaryMessage(getter, isGetter = true)

                }
            }
        }
    }
    changePackage(savedPackageName, createFakeToken())
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
        !(it is MessageSendKeyword && (it.receiver.str == "Project" || it.receiver.str == "Bind"))
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
            val rootType = statement.toType(currentPackageName, typeTable, isUnion = true) as Type.UserUnionRootType
            addNewType(rootType, statement)


            val branches = mutableListOf<Type.UserUnionBranchType>()
            val genericsOfBranches = mutableSetOf<Type>()
            statement.branches.forEach {
                val branchType = it.toType(currentPackageName, typeTable, root = rootType) as Type.UserUnionBranchType
                branchType.parent = rootType
                branchType.fields += rootType.fields
                branches.add(branchType)

                addNewType(branchType, it)

                genericsOfBranches.addAll(branchType.typeArgumentList)
            }
            rootType.branches = branches
            rootType.typeArgumentList = rootType.typeArgumentList + genericsOfBranches


            /// generics
            // add generics from branches
            val allGenerics = statement.collectAllGenericsFromBranches() + statement.genericFields
            statement.genericFields.clear()
            statement.genericFields.addAll(allGenerics)
            // not only to statement, but to Type too
            rootType

        }

        is AliasDeclaration -> TODO()

        is MessageDeclaration -> {
            // check if the type already registered
            val forType = typeTable[statement.forTypeAst.name]
            if (forType == null) {
                unResolvedMessageDeclarations.add(statement) // statement.token.compileError("type ${statement.forType.name} is not registered")
                currentLevel--
                return
            } else {
                unResolvedMessageDeclarations.remove(statement)
                statement.forType = forType
            }

            // check that there is no field with the same name (because of getter has the same signature)
            if (forType is Type.UserType) {
                val q = forType.fields.find { it.name == statement.name }
                if (q != null) {
                    statement.token.compileError("Type ${statement.forTypeAst.name} already has field with name ${statement.name}")
                }
            }

            val bodyScope = mutableMapOf<String, Type>()
            when (statement) {
                is MessageDeclarationUnary -> addNewUnaryMessage(statement)
                is MessageDeclarationBinary -> addNewBinaryMessage(statement)
                is MessageDeclarationKeyword -> {
                    statement.args.forEach {
                        if (it.type == null) {
                            statement.token.compileError("Can't parse type for argument ${it.name}")
                        }
                        val astType = it.type
                        val type = astType.toType(typeTable)

                        if (type.name == it.type.name) {
                            bodyScope[it.localName ?: it.name] = type
                        } else {
                            bodyScope[it.localName ?: it.name] = type
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
        when (statement2.receiver.str) {
            "Project" -> {
                // add to the current project
                assert(statement2.messages.count() == 1)
                val keyword = statement2.messages[0] as KeywordMsg

                keyword.args.forEach {

                    when (it.keywordArg) {
                        is LiteralExpression.StringExpr -> {
                            val substring = it.keywordArg.token.lexeme.removeDoubleQuotes()
                            when (it.selectorName) {
                                "name" -> changeProject(substring, statement2.token)
                                "package" -> changePackage(substring, statement2.token)
                                "protocol" -> changeProtocol(substring)
                            }
                        }

                        is ListCollection -> {
                            when (it.selectorName) {
                                "loadPackages" -> {
                                    if (it.keywordArg.initElements[0] !is LiteralExpression.StringExpr) {
                                        it.keywordArg.token.compileError("packages must be listed as String")
                                    }

                                    generator.loadPackages(it.keywordArg.initElements.map { it.token.lexeme })
                                }
                            }
                        }

                        else -> it.keywordArg.token.compileError("Only String args allowed for ${it.selectorName}")

                    }


                }
            }

            "Bind" -> {
            }

            else -> {
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
                val forType = typeTable[statement.forTypeAst.name]!!

                bodyScope["this"] = forType

                fun addArgumentsToBodyScope(statement: MessageDeclarationKeyword) {
                    statement.args.forEach {
                        val astType = it.type!!
                        val type = astType.toType(typeTable)

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
                when (statement) {
                    is MessageDeclarationUnary -> {}
                    is MessageDeclarationBinary -> {
                        bodyScope[statement.arg.name] = statement.arg.type!!.toType(typeTable)
                    }

                    is MessageDeclarationKeyword -> {
                        addArgumentsToBodyScope(statement)

                    }

                    is ConstructorDeclaration -> {
                        val constructorDecl = statement.msgDeclaration
                        if (constructorDecl is MessageDeclarationKeyword) {
                            addArgumentsToBodyScope(constructorDecl)
                        }
                    }
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
            val value = statement.value
            val valueType = value.type
                ?: statement.token.compileError("In var declaration ${statement.name} value doesn't got type")
            val statementDeclaredType = statement.valueType

            // if this is Type with zero fields constructor
            // replace Identifier with Keyword kind: Constructor
            if (value is IdentifierExpr && valueType is Type.UserLike && valueType.fields.isEmpty()) {
                statement.value = KeywordMsg(
                    receiver = value, //IdentifierExpr("", token = createFakeToken()),
                    selectorName = value.str,
                    type = valueType,
                    token = statement.value.token,
                    args = listOf(),
                    path = listOf(),
                    kind = KeywordLikeType.Constructor
                )
            }

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
            if (currentLevel == 0)
                topLevelStatements.add(statement)
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
                        this.projects["common"]!!.packages["core"]!!.types["List"] as Type.UserType

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
            val expr = statement.expression
            if (expr != null)
                resolve(listOf(expr), previousAndCurrentScope, statement)

            val q = expr?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
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


fun compare2Types(type1: Type, type2: Type): Boolean {
    // TODO temp
    if (type1 is Type.Lambda || type2 is Type.Lambda) {
        return true
    }
    // TODO temp, there could be types with same names in different packages
    if (type1.name == type2.name) {
        return true
    }

    if (type1 is Type.UserLike && type2 is Type.UserLike) {
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

    // comparing with nothing is always true, its bottom type, subtype of all types
    // so we can return nothing from switch expr branches, beside u cant do it with different types
    val nothing = Resolver.defaultTypes[InternalTypes.Nothing]
    return type1 == nothing || type2 == nothing
}

fun Package.addImport(pkg: String) {
    if (packageName != pkg) {
        currentImports.add(pkg)
    }
}

fun Resolver.findUnaryMessageType(receiverType: Type, selectorName: String, token: Token): UnaryMsgMetaData {

    fun findUnary(receiverType: Type, selectorName: String, token: Token): UnaryMsgMetaData? {
        receiverType.protocols.forEach { (_, v) ->
            val q = v.unaryMsgs[selectorName]

            if (q != null) {
                // TODO! add unisng of unary
                val pkg = getCurrentPackage(token)
                pkg.addImport(receiverType.pkg)
                return q
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

    // this is Any
    val anyType = Resolver.defaultTypes[InternalTypes.Any]!!
    val messageFromAny = findUnary(anyType, selectorName, token)
    if (messageFromAny != null) {
        return messageFromAny
    }

    println("Sas???")
    token.compileError("Cant find unary message: $selectorName for type ${receiverType.name}")
}


// returns true if it is static call, but not constructor(so we generate Clock.System instead of Clock.System())
fun Resolver.findStaticMessageType(
    receiverType: Type,
    selectorName: String,
    token: Token,
    msgType: MessageDeclarationType? = null
): Pair<Type, Boolean> {

    receiverType.protocols.forEach { (_, v) ->
        val q = v.staticMsgs[selectorName]
        if (q != null) {
            val pkg = getCurrentPackage(token)
            pkg.addImport(receiverType.pkg)
            return Pair(q.returnType, false)
        }
    }

    // if this is binding, then getters are static, calls without ()
    if (msgType != null && getPackage(receiverType.pkg, token).isBinding) {
        when (msgType) {
            MessageDeclarationType.Unary ->
                return Pair(findUnaryMessageType(receiverType, selectorName, token).returnType, true)

            MessageDeclarationType.Keyword ->
                return Pair(findKeywordMsgType(receiverType, selectorName, token).returnType, true)

            MessageDeclarationType.Binary -> TODO()
        }

    }

    throw Exception("Cant find static message: $selectorName for type ${receiverType.name}")
//    token.compileError("Cant find static message: $selectorName for type ${receiverType.name}")
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
            pkg.addImport(receiverType.pkg)
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
            pkg.addImport(receiverType.pkg)
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
    val typeOfReceiver = typeTable[statement.forTypeAst.name]!!
    when (statement.msgDeclaration) {
        is MessageDeclarationUnary -> {
            staticUnaryForType[statement.name] = statement.msgDeclaration
            val protocol = getCurrentProtocol(statement.forTypeAst.name, statement.token)
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
            val protocol = getCurrentProtocol(statement.forTypeAst.name, statement.token)

            val keywordArgs = statement.msgDeclaration.args.map {
                KeywordArg(
                    name = it.name,
                    type = it.type?.toType(typeTable)
                        ?: statement.token.compileError("Type of keyword message ${statement.msgDeclaration.name}'s arg ${it.name} not registered")
                )
            }
            val messageData = KeywordMsgMetaData(
                name = statement.msgDeclaration.name,
                argTypes = keywordArgs,
                returnType = typeOfReceiver,
            )
            protocol.staticMsgs[statement.name] = messageData
        }

        is ConstructorDeclaration -> TODO()
    }
    addMsgToPackageDeclarations(statement)

}

fun Resolver.addNewUnaryMessage(statement: MessageDeclarationUnary, isGetter: Boolean = false) {
    unaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forTypeAst.name, statement.token)
    val messageData = statement.toMessageData(typeTable, isGetter)

    protocol.unaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewBinaryMessage(statement: MessageDeclarationBinary) {
    binaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forTypeAst.name, statement.token)
    val messageData = statement.toMessageData(typeTable)
    protocol.binaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewKeywordMessage(statement: MessageDeclarationKeyword) {
    keywordForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forTypeAst.name, statement.token)
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

    if (pack.isBinding && type is Type.UserLike) {
        type.isBinding = true
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

fun Resolver.changePackage(newCurrentPackage: String, token: Token, isBinding: Boolean = false) {
    currentPackageName = newCurrentPackage

    val currentProject = projects[currentProjectName] ?: token.compileError("Can't find project: $currentProjectName")

    val alreadyExistsPack = currentProject.packages[newCurrentPackage]

    // check that this package not exits already
    if (alreadyExistsPack != null) {
        // load table of types
//        typeTable.clear()
        typeTable.putAll(alreadyExistsPack.types)
    } else {
        // create this new package
        val pack = Package(
            packageName = newCurrentPackage,
            isBinding = isBinding
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
    val type = findType(x.name, currentScope, previousScope)
        ?: x.token.compileError("Unresolved reference: ${x.str}")

    x.type = type
    return type
}

fun Resolver.findType(
    typeName: String,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type? =
    typeTable[typeName]
        ?: currentScope[typeName]
        ?: previousScope[typeName]
        ?: findTypeInAllPackages(typeName)


fun Resolver.findTypeInAllPackages(x: String): Type? {
    val packages = projects[currentProjectName]!!.packages.values
    packages.forEach {
        val result = it.types[x]
        if (result != null) {
            return result
        }
    }

    return null
}


fun IfBranch.getReturnTypeOrThrow(): Type = when (this) {
    is IfBranch.IfBranchSingleExpr -> {
        this.thenDoExpression.type!!
    }

    is IfBranch.IfBranchWithBody -> {
        val last = body.last()
        when (last) {
            is Expression -> last.type!!
//            is ReturnStatement -> last.expression.type!!
            else -> Resolver.defaultTypes[InternalTypes.Unit]!!
        }
    }
}
