package frontend.typer

import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.*
import frontend.util.checkIfAllStringsEqual
import frontend.util.removeDoubleQuotes
import lex
import java.io.File

typealias TypeName = String


inline fun createDefaultType(type: InternalTypes): Pair<InternalTypes, Type.InternalType> {
    return type to Type.InternalType(
        typeName = type,
        `package` = "common",
    )
}

//inline fun createCollectionType(type: InternalTypes): Pair<InternalTypes, Type.GenericType> {
//
//    return type to Type.GenericType(
//        mainType = type,
//        `package` = "common",
//        typeArgumentList = listOf(InternalTypes.Unknown),
//        fields = listOf(
////            TypeField(
////                name = "length",
////                type = Resolver.defaultTypes[InternalTypes.Int]!!
////            )
//        ),
//
//
//    )
//}

class Resolver(
    val projectName: String,

    // statements from all files
    // if there cycle types then just remember the unresolved types and then try to resolve them again in the end
    var statements: MutableList<Statement>,

    val mainFilePath: File,
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
    val notResolvedStatements: MutableList<Statement> = mutableListOf()
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
            createDefaultType(InternalTypes.Unknown),

            createDefaultType(InternalTypes.List),
            createDefaultType(InternalTypes.Map),
            createDefaultType(InternalTypes.Set),

//            createCollectionType(InternalTypes.List),
//            createCollectionType(InternalTypes.Map),
//            createCollectionType(InternalTypes.Set),
//            createDefaultType(InternalTypes.Array),

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
            val listType = defaultTypes[InternalTypes.List]!!
            val unknownGenericType = defaultTypes[InternalTypes.Unknown]!!



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

            listType.protocols.putAll(
                createListProtocols(
                    intType = intType,
                    stringType = stringType,
                    unitType = unitType,
                    boolType = boolType,
                    listType = listType,
                    anyType = anyType,
                    unknownGenericType = unknownGenericType
                )
            )

            // TODO add default protocols for other types
        }

    }

    init {
        /////init packages/////
        Resolver.defaultTypes.forEach { (k, v) ->
            typeTable[k.name] = v
        }

        val defaultProject = Project("common")
        defaultProject.packages["common"] = Package("common")
        val corePackage = Package("core")
        defaultProject.packages["core"] = corePackage

        // add all default types to core package
        defaultTypes.forEach { (k, v) ->
            corePackage.types[k.name] = v
        }

        projects[projectName] = defaultProject
        ///////generate ast from files////////
        if (statements.isEmpty()) {
            fun getAst(source: String, fileName: String): List<Statement> {
                val tokens = lex(source)
                val parser = Parser(file = fileName, tokens = tokens, source = "sas.niva")
                val ast = parser.statements()
                return ast
            }
            // generate ast for main file with filling topLevelStatements
            // 1) read content of mainFilePath
            // 2) generate ast
            val mainSourse = mainFilePath.readText()
            val mainAST = getAst(source = mainSourse, fileName = mainFilePath.name)
            // generate ast for others
            val otherASTs = otherFilesPaths.map {
                val src = it.readText()
                getAst(source = src, fileName = it.name)
            }

            ////resolve all the AST////
            statements = mainAST.toMutableList()
            resolve(mainAST, mutableMapOf())

            otherASTs.forEach {
                statements = it.toMutableList()
                resolve(it, mutableMapOf())
            }

        }
    }
}


// нужен механизм поиска типа, чтобы если не нашли метод в текущем типе, то посмотреть в Any
fun Resolver.resolve(
    statements: List<Statement>,
    previousScope: MutableMap<String, Type>,
    rootStatement: Statement? = null, // since we in recursion, and can't define on what level of it, its the only way to know previous statement
): List<Statement> {
    val currentScope = mutableMapOf<String, Type>()

    statements.forEachIndexed { i, statement ->

        resolveStatement(
            statement,
            currentScope,
            previousScope,
            i,
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
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
) {
    currentLevel += 1

    when (statement) {
        is TypeDeclaration -> {
            // if not then add
            val newType = statement.toType(currentPackageName, typeTable)
            addNewType(newType, statement)
        }

        is UnionDeclaration -> TODO()
        is AliasDeclaration -> TODO()
//        is ConstructorDeclaration -> {
//
//            if (statement.body.count() != 1) {
//                throw Exception("Constructor must contain only one expression, line: ${statement.token.line}")
//            }
//
//            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
//            resolve(statement.body, previousAndCurrentScope, statement)
//        }

        is MessageDeclaration -> {
            // check if the type already registered
            // if no then error
            val forType = typeTable[statement.forType.name]
                ?: throw Exception("type ${statement.forType.name} is not registered")

            // add this
            currentScope["this"] = forType

            // if yes, check for register in unaryTable
            val isUnaryRegistered = unaryForType.containsKey(statement.name)
            if (isUnaryRegistered) {
                throw Exception("Unary ${statement.name} for type ${statement.forType.name} is already registered")
            }
            // check that there is no field with the same name (because of getter has the same signature)

            if (forType is Type.UserType) {
                val q = forType.fields.find { it.name == statement.name }
                if (q != null) {
                    throw Exception("Type ${statement.forType.name} already has field with name ${statement.name}")
                }
            }

            when (statement) {
                is MessageDeclarationUnary -> addNewUnaryMessage(statement)
                is MessageDeclarationBinary -> addNewBinaryMessage(statement)
                is MessageDeclarationKeyword -> addNewKeywordMessage(statement)

                is ConstructorDeclaration -> addStaticDeclaration(statement)
            }

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            previousAndCurrentScope["this"] = forType

            val body = this.resolve(statement.body, previousAndCurrentScope, statement)

            // TODO check that return type is the same as declared return type, or if it not declared -> assign it

        }
//        is MessageDeclarationBinary -> TODO()
//        is MessageDeclarationKeyword -> TODO()
//        is MessageDeclarationUnary -> TODO()
    }
    currentLevel -= 1
}

private fun Resolver.resolveStatement(
    statement: Statement,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    i: Int,
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
                        ?: throw Exception("Not all messages of ${statement2.str} has types")
            } else {
                // every single expressions is unary message without messages
                if (statement2.type == null) {
                    currentLevel++
                    resolve(listOf(statement2.receiver), previousAndCurrentScope, statement2)
                    currentLevel--
                }
                statement2.type = statement2.receiver.type
                    ?: throw Exception("Can't find type for ${statement2.str} on line ${statement2.token.line}")
            }
        } else {
            // add to the current project
            assert(statement2.messages.count() == 1)
            val keyword = statement2.messages[0] as KeywordMsg

            keyword.args.forEach {
                if (it.keywordArg.token.kind == TokenType.String) {
                    val substring = it.keywordArg.token.lexeme.removeDoubleQuotes()
                    when (it.selectorName) {
                        "name" -> changeProject(substring)
                        "package" -> changePackage(substring)
                        "protocol" -> changeProtocol(substring)
                    }
                } else throw Exception("Only string arguments for Project allowed")
            }
        }

    }

    when (statement) {
        is Declaration -> resolveDeclarations(statement, currentScope, previousScope)
        is VarDeclaration -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            // currentNode, depth + 1
            currentLevel++
            resolve(listOf(statement.value), previousAndCurrentScope, statement)
            currentLevel--

            val valueType = statement.value.type
                ?: throw Exception("Line: ${statement.token.line} In var declaration ${statement.name} value doesn't got type")
            val statementDeclaredType = statement.valueType

            // check that declared type == inferred type
            if (statementDeclaredType != null) {
                if (statementDeclaredType.name != valueType.name) {
                    val text = "${statementDeclaredType.name} != ${valueType.name}"
                    throw Exception("Type declared for ${statement.name} is not equal for it's value type($text)")
                }
            }

            currentScope[statement.name] = valueType

            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }

        }


        is KeywordMsg -> {
            // check for constructor
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(listOf(statement.receiver), previousAndCurrentScope, statement)
                currentLevel--
            }
            val receiverType =
                statement.receiver.type
                    ?: throw Exception("Can't infer receiver ${statement.receiver.str} type on line ${statement.token.line}")


            // resolve args types
            val args = statement.args
            args.forEachIndexed { argNum, it ->
                if (it.keywordArg.type == null) {
                    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                    currentLevel++
                    currentArgumentNumber = argNum
                    resolve(listOf(it.keywordArg), previousAndCurrentScope, statement)
                    currentLevel--
                }
            }
            currentArgumentNumber = -1


            // if receiverType is lambda then we need to check does it have same argument names and types

            if (receiverType is Type.Lambda) {

                // need
                if (receiverType.args.count() != statement.args.count()) {
                    throw Exception("you need to use  on Line ${statement.token.line}")
                }

                statement.args.forEachIndexed { ii, it ->
                    // name check
                    if (it.selectorName != receiverType.args[ii].name) {
                        throw Exception("${it.selectorName} is not valid arguments for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.selectorName }} on Line ${statement.token.line}")
                    }
                    // type check
                    val isTypesEqual = compare2Types(it.keywordArg.type!!, receiverType.args[ii].type)
                    if (!isTypesEqual) {
                        throw Exception("${it.selectorName} is not valid type for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.keywordArg.type?.name }} on Line ${statement.token.line}")
                    }
                }

                statement.type = receiverType.returnType
                statement.kind = KeywordLikeType.ForCodeBlock
                return
            }


            // check if receiver is type
            // Person name: "sas"
            val receiverText = statement.receiver.str
            val q = typeTable[receiverText]
            if (receiverText == "Project") {
                throw Error("We cant get here, type Project are ignored")
            }
            if (q == null) {
                val checkForSetter = { receiverType2: Type ->
                    // if the amount of keyword's arg is 1, and its name on of the receiver field, then its setter

                    if (statement.args.count() == 1 && receiverType2 is Type.UserType) {
                        val keyArgText = statement.args[0].selectorName
                        // find receiver arg same as keyArgText
                        val receiverArgWithSameName = receiverType2.fields.find { it.name == keyArgText }
                        if (receiverArgWithSameName != null) {
                            // this is setter
                            statement.kind = KeywordLikeType.Setter
                            statement.type = receiverArgWithSameName.type
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                // this is the usual message or setter
                checkForSetter(receiverType)
                if (statement.kind != KeywordLikeType.Setter) {
                    statement.kind = KeywordLikeType.Keyword
                    val q = findKeywordMsgType(receiverType, statement.selectorName)

                    // KOSTЫL для list2 = list.map...
                    statement.type =
                        if (q.returnType.name == InternalTypes.List.name && receiverType is Type.GenericType && receiverType.mainType.name == "List") {
                            receiverType
                        } else q.returnType
                }
            } else {
                // this is a constructor

                // check that all fields are filled
                if (receiverType is Type.UserType) {
                    val receiverFields = receiverType.fields
                    if (statement.args.count() != receiverFields.count()) {
                        throw Exception("For ${statement.selectorName} constructor call, not all fields are listed")
                    }

                    statement.args.forEachIndexed { i, arg ->
                        val typeField = receiverFields[i]
                        if (typeField.name != arg.selectorName) {
                            throw Exception("In constructor message for type ${statement.receiver.str} field ${typeField.name} != ${arg.selectorName}")
                        }
                    }
                }
                statement.kind = KeywordLikeType.Constructor
                statement.type = receiverType
            }

        }

        is BinaryMsg -> {
            val forType =
                statement.receiver.type?.name
            val receiver = statement.receiver

            receiver.type = when (receiver) {
                is MessageSend -> TODO()
                is CodeBlock -> TODO()
                is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, previousScope, currentScope)
                is ListCollection -> TODO()
                is MapCollection -> TODO()
                is BinaryMsg -> TODO()
                is KeywordMsg -> TODO()
                is UnaryMsg -> TODO()
                is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)
                is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
            }
            val receiverType = receiver.type!!
            // find message for this type
            val messageReturnType = findBinaryMessageType(receiverType, statement.selectorName)
            statement.type = messageReturnType
        }

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
            val forType =
                statement.receiver.type?.name// ?: throw Exception("${statement.selectorName} hasn't type")
            val receiver = statement.receiver

            // for constructors
            var isStaticCall = false

            if (receiver.type == null)
                receiver.type = when (receiver) {

                    is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, currentScope, previousScope)
                    is CodeBlock -> TODO()
                    is ListCollection -> TODO()
                    is MapCollection -> TODO()
                    // receiver
                    is UnaryMsg -> TODO()
                    is BinaryMsg -> TODO()
                    is KeywordMsg -> TODO()

                    // receiver
                    is MessageSend -> TODO()


                    is IdentifierExpr -> {
                        if (typeTable[receiver.str] != null) {
                            isStaticCall = true
                        }
                        getTypeForIdentifier(receiver, currentScope, previousScope)
                    }

                    is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                    is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                    is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                }

            val receiverType = receiver.type!!



            if (receiverType is Type.Lambda) {
                if (statement.selectorName != "exe") {
                    if (receiverType.args.isNotEmpty())
                        throw Exception("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                    else
                        throw Exception("For lambda ${statement.str} on Line ${statement.token.line} you can use only unary 'exe' message")
                }


                if (receiverType.args.isNotEmpty()) {
                    throw Exception("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                }

                statement.type = receiverType.returnType
                statement.kind = UnaryMsgKind.ForCodeBlock
                return
            }


            val checkForGetter = {
                if (receiverType is Type.UserType) {
                    val fieldWithSameName = receiverType.fields.find { it.name == statement.selectorName }
                    Pair(fieldWithSameName != null, fieldWithSameName)
                } else Pair(false, null)
            }

            val (isGetter, w) = checkForGetter()
            if (isGetter) {
                statement.kind = UnaryMsgKind.Getter
                statement.type = w!!.type

            } else {
                // usual message
                // find this message

                val messageReturnType =
                    if (!isStaticCall)
                        findUnaryMessageType(receiverType, statement.selectorName, statement.token.line)
                    else
                        findStaticMessageType(receiverType, statement.selectorName, statement.token.line)

                statement.kind = UnaryMsgKind.Unary
                statement.type = messageReturnType
            }


        }

        is MessageSend -> {
            resolveTypeForMessageSend(statement)
            if (currentLevel == 0) topLevelStatements.add(statement)

        }


        is IdentifierExpr -> {
            getTypeForIdentifier(statement, previousScope, currentScope)
        }

        is ExpressionInBrackets -> {
            resolveExpressionInBrackets(statement, previousScope, currentScope)
        }

        is CodeBlock -> {

            // [] vs x = []
            if ((rootStatement != null && (rootStatement !is VarDeclaration && rootStatement !is Message)) || rootStatement == null) {
                statement.isSingle = true
            }


            val variables = statement.inputList
            variables.forEach {
                if (it.typeAST != null) {
                    it.type = it.typeAST.toType(typeTable)
                } else {
                    it.type = getTypeForIdentifier(it, previousScope, currentScope)
                }

            }

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

            variables.forEach {
                previousAndCurrentScope[it.name] = it.type!!
            }

            var isThisWhileCycle = true
            // if this is lambda with one arg, then add "it" to scope
            // TODO don't add it if this lambda has named arg
            if (rootStatement != null && rootStatement is KeywordMsg && currentArgumentNumber != -1) {
                if (rootStatement.receiver !is CodeBlock) {
                    val metaData = findKeywordMsgType(rootStatement.receiver.type!!, rootStatement.selectorName)
                    val currentArgType = metaData.argTypes[currentArgumentNumber]

                    if (currentArgType.type is Type.Lambda && currentArgType.type.args.count() == 1) {

                        val weew = currentArgType.type.args[0].type
                        val receiverTypeArg = rootStatement.receiver.type
                        val onlyArgOfLambdaType =
                            if (weew.name == InternalTypes.Unknown.name && receiverTypeArg is Type.GenericType) {
                                receiverTypeArg.typeArgumentList[0]
                            } else weew

                        previousAndCurrentScope["it"] = onlyArgOfLambdaType

                    }

                    isThisWhileCycle = false
                }
            }

            currentLevel++
            resolve(statement.statements, previousAndCurrentScope, statement)
            currentLevel--
            val lastExpression = statement.statements.last()
            if (lastExpression !is Expression) {
                throw Exception("last statement of code block must be expression on line ${statement.token.line}")
            }

            // Add lambda type to code-block itself
            val returnType = lastExpression.type!!

            val args = statement.inputList.map {
                TypeField(name = it.name, type = it.type!!)
            }.toMutableList()

            val type = Type.Lambda(
                args = args,
                returnType = returnType,
                `package` = currentPackageName
            )
            statement.type = type


            // add whileTrue argument of lambda type
            if (isThisWhileCycle && rootStatement is KeywordMsg) {
                val receiverType = rootStatement.receiver.type

                // find if it is a whileTrue or whileFalse than add an argument of type lambda to receiver
                if (receiverType is Type.Lambda && receiverType.args.isEmpty() &&
                    (rootStatement.selectorName == "whileTrue" || rootStatement.selectorName == "whileFalse")

                ) {
                    receiverType.args.add(
                        TypeField(
                            name = rootStatement.selectorName,
                            type = Type.Lambda(
                                args = mutableListOf(),
                                returnType = Resolver.defaultTypes[InternalTypes.Any]!!,
                                `package` = currentPackageName
                            )
                        )
                    )
                }
            }

            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }
        }

        is ListCollection -> {

            if (statement.initElements.isNotEmpty()) {
                val q = statement.initElements[0]
                if (q.typeAST != null) {
                    val w = q.typeAST.toType(typeTable)
                    val listType = Resolver.defaultTypes[InternalTypes.List]!!

                    // try to find list with the same generic type
                    val typeName = "List::${w.name}"
                    val currentPkg = getCurrentPackage()
                    val alreadyExistsListType = currentPkg.types[typeName]

                    val listProtocols = listType.protocols

                    val genericType = alreadyExistsListType ?: Type.GenericType(
                        mainType = listType,
                        name = typeName,
                        typeArgumentList = listOf(w),
                        fields = listOf(),
                        `package` = currentPackageName,
                        protocols = listProtocols
                    )

                    if (alreadyExistsListType == null) {
                        addNewType(genericType, null, currentPkg)
                    }

                    statement.type = genericType
                } else {
                    throw Exception("Cant get type of elements of list literal on line ${statement.token.line}")
                }
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
            if (statement.ifBranches.isEmpty()) {
                throw Exception("If must contain at least one branch, Line: ${statement.token.line}")
            }

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()


            when (statement) {
                is ControlFlow.IfExpression -> {


                    var firstBranchReturnType: Type? = null

                    statement.ifBranches.forEachIndexed { i, it ->
                        /// resolving if
                        resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                        val currentType = it.ifExpression.type?.name
                        /// resolving then
                        when (it) {
                            is IfBranch.IfBranchSingleExpr -> {
                                resolve(listOf(it.thenDoExpression), previousAndCurrentScope, statement)
                            }

                            is IfBranch.IfBranchWithBody -> {
                                if (it.body.isNotEmpty()) {
                                    currentLevel++
                                    resolve(it.body, previousAndCurrentScope, statement)
                                    currentLevel--

                                    val lastExpr = it.body.last()
                                    if (lastExpr !is Expression) {
                                        throw Exception("In switch expression body last statement must be an expression, Line: ${lastExpr.token.line}")
                                    }
                                }
                            }
                        }

                        // compare the current branch type with the last one
                        if (i > 0) {
                            val prev = statement.ifBranches[i - 1]

                            val prevType: Type = prev.getReturnTypeOrThrow()
                            val currType = it.getReturnTypeOrThrow()
                            if (prevType.name != currType.name) {
                                throw Exception(
                                    "In if Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} " +
                                            "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                                )
                            }
                        } else {
                            firstBranchReturnType = it.getReturnTypeOrThrow()
                        }
                    }


                    if (statement.elseBranch == null) {
                        throw Exception("If expression must contain else branch, Line: ${statement.token.line}")
                    }


                    resolve(statement.elseBranch, previousAndCurrentScope, statement)
                    val lastExpr = statement.elseBranch.last()
                    if (lastExpr !is Expression) {
                        throw Exception("In switch expression body last statement must be an expression, Line: ${lastExpr.token.line}")
                    }
                    val elseReturnType = lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        throw Exception(
                            "In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)"
                        )
                    }

                    statement.type = elseReturnType
                }


                is ControlFlow.SwitchExpression -> {
                    if (statement.switch.type == null) {
                        resolve(listOf(statement.switch), previousAndCurrentScope, statement)
                    }

                    var firstBranchReturnType: Type? = null

                    statement.ifBranches.forEachIndexed { i, it ->
                        /// resolving if
                        resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                        val currentType = it.ifExpression.type?.name
                        if (currentType != statement.switch.type!!.name) {
                            val curTok = it.ifExpression.token
                            throw Exception("If branch ${curTok.lexeme} on line: ${curTok.line} is not of switching Expr type: ${statement.switch.type!!.name}")
                        }
                        /// resolving then
                        when (it) {
                            is IfBranch.IfBranchSingleExpr -> {
                                resolve(listOf(it.thenDoExpression), previousAndCurrentScope, statement)
                            }

                            is IfBranch.IfBranchWithBody -> {
                                if (it.body.isNotEmpty()) {
                                    currentLevel++
                                    resolve(it.body, previousAndCurrentScope, statement)
                                    currentLevel--

                                    val lastExpr = it.body.last()
                                    if (lastExpr !is Expression) {
                                        throw Exception("In switch expression body last statement must be an expression, Line: ${lastExpr.token.line}")
                                    }
                                }
                            }
                        }

                        // compare the current branch type with the last one
                        if (i > 0) {
                            val prev = statement.ifBranches[i - 1]

                            val prevType: Type = prev.getReturnTypeOrThrow()
                            val currType = it.getReturnTypeOrThrow()
                            if (prevType.name != currType.name) {
                                throw Exception(
                                    "In switch Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} " +
                                            "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                                )
                            }
                        } else {
                            firstBranchReturnType = it.getReturnTypeOrThrow()
                        }
                    }


                    if (statement.elseBranch == null) {
                        throw Exception("If expression must contain else branch, Line: ${statement.token.line}")
                    }


                    resolve(statement.elseBranch, previousAndCurrentScope, statement)
                    val lastExpr = statement.elseBranch.last()
                    if (lastExpr !is Expression) {
                        throw Exception("In switch expression body last statement must be an expression, Line: ${lastExpr.token.line}")
                    }
                    val elseReturnType = lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        throw Exception(
                            "In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)"
                        )
                    }

                    statement.type = elseReturnType
                }


                is ControlFlow.IfStatement -> {
                    statement.type = Resolver.defaultTypes[InternalTypes.Unit]!!
                }

                is ControlFlow.SwitchStatement -> {
                    statement.type = Resolver.defaultTypes[InternalTypes.Unit]!!
                }
            }





            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is Assign -> {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            resolve(listOf(statement.value), previousAndCurrentScope, statement)



            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }
        }

        else -> {

        }
    }
}

private fun Resolver.resolveExpressionInBrackets(
    statement: ExpressionInBrackets,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type {
    if (statement.statements.isEmpty()) {
        throw Exception("Parens must contain expression, line: ${statement.token.line}")
    }
    val lastExpr = statement.statements.last()
    if (lastExpr !is Expression) {
        throw Exception("Last statement inside parens must be expression, line: ${statement.token.line}")
    }

    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
    resolve(statement.statements, previousAndCurrentScope, statement)
    statement.type = lastExpr.type
    return lastExpr.type!!
}


private fun Resolver.compare2Types(type1: Type, type2: Type): Boolean {
    if (type1.name == "Any" && type2.name == "Any") {
        return true
    }
    // temp
    if (type1 is Type.Lambda || type2 is Type.Lambda) {
        return true
    }

    return type1.name === type2.name

}

private fun Resolver.findUnaryMessageType(receiverType: Type, selectorName: String, line: Int? = null): Type {
    receiverType.protocols.forEach { (k, v) ->
        val q = v.unaryMsgs[selectorName]

        if (q != null) {
            return q.returnType
        }
    }
    val lineMessagePart = if (line != null) "on Line: $line" else ""
    throw Error("Cant find unary message: $selectorName for type ${receiverType.name} $lineMessagePart")
}

private fun Resolver.findStaticMessageType(receiverType: Type, selectorName: String, line: Int? = null): Type {
    receiverType.protocols.forEach { (k, v) ->
        val q = v.staticMsgs[selectorName]

        if (q != null) {
            return q.returnType
        }
    }
    val lineMessagePart = if (line != null) "on Line: $line" else ""
    throw Error("Cant find static message: $selectorName for type ${receiverType.name} $lineMessagePart")
}

private fun Resolver.findBinaryMessageType(receiverType: Type, selectorName: String): Type {
    receiverType.protocols.forEach { (k, v) ->
        val q = v.binaryMsgs[selectorName]
        if (q != null) {
            return q.returnType
        }
    }
    throw Error("Cant find binary message: $selectorName for type ${receiverType.name}")
}

private fun Resolver.findKeywordMsgType(receiverType: Type, selectorName: String): KeywordMsgMetaData {
    receiverType.protocols.forEach { (k, v) ->
        val q = v.keywordMsgs[selectorName]
        if (q != null) {
            return q
        }
    }
    throw Error("Cant find keyword message: $selectorName for type ${receiverType.name}")
}


fun Resolver.getPackage(packageName: String): Package {
    val p = this.projects[currentProjectName] ?: throw Exception("there are no such project: $currentProjectName")
    val pack = p.packages[packageName] ?: throw Exception("there are no such package: $packageName")
    return pack
}

// @isThisTheLastTypeCheck if it is the last, then we throw errors, if not
fun Resolver.findPackageWhereTypeDeclarated(
    typeName: String,
    expectedTypeName: String? = null,
    isThisTheLastTypeCheck: Boolean = false,
): String? {
    val p = this.projects[currentProjectName] ?: throw Exception("there are no such project: $currentProjectName")
    var result: String? = null
    val foundResults = mutableListOf<String>()

    p.packages.forEach { (k, v) ->
        val foundType = v.types[typeName]
        if (foundType == null) {
            result = k
            foundResults.add(k)
        }
    }

    if (result == null) {
        if (isThisTheLastTypeCheck)
            throw Exception("Can't find $typeName in all packages")
        else
            return null // need to add statement to unresolved list
    }

    if (foundResults.count() == 1) {
        return result
    }
    // found more than one result
    // need to check them for equality
    val q = checkIfAllStringsEqual(foundResults)
    if (isThisTheLastTypeCheck) {
        if (q) {
            throw Exception("There is more than one $typeName in all packages")
        } else {
            result = null
        }
    }

    if (foundResults.count() > 1) {
        if (expectedTypeName != null && foundResults.contains(expectedTypeName)) {
            return expectedTypeName
        } else {
            result = null
        }
    }

    return result
}

fun Resolver.getCurrentProtocol(typeName: String): Protocol {
    val pack = getPackage(currentPackageName)
    val type2 = pack.types[typeName]
        ?: getPackage("common").types[typeName]
        ?: getPackage("core").types[typeName]
        ?: throw Exception("there are no such type: $typeName in package $currentPackageName in project: $currentProjectName")

    val protocol =
        type2.protocols[currentProtocolName] //?: throw Exception("there no such protocol: $currentProtocolName in type: ${type2.name} in package $currentPackageName in project: $currentProjectName")
    if (protocol == null) {
        val newProtocol = Protocol(currentProtocolName)
        type2.protocols[currentProtocolName] = newProtocol
        return newProtocol
    }
    return protocol
}

fun Resolver.getCurrentPackage() = getPackage(currentPackageName)


fun Resolver.addStaticDeclaration(statement: ConstructorDeclaration) {
    val typeOfReceiver = typeTable[statement.forType.name]!!
    when (statement.msgDeclaration) {
        is MessageDeclarationUnary -> {
            staticUnaryForType[statement.name] = statement.msgDeclaration
            val protocol = getCurrentProtocol(statement.forType.name)
            val messageData = UnaryMsgMetaData(
                name = statement.msgDeclaration.name,
                returnType = typeOfReceiver,
            )
            protocol.staticMsgs[statement.name] = messageData
        }

        is MessageDeclarationBinary -> {
            throw Exception("Binary static message, really? This is not allowed")
        }

        is MessageDeclarationKeyword -> {
            staticKeywordForType[statement.name] = statement.msgDeclaration
            val protocol = getCurrentProtocol(statement.forType.name)
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

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.unaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewBinaryMessage(statement: MessageDeclarationBinary) {
    binaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.binaryMsgs[statement.name] = messageData

    addMsgToPackageDeclarations(statement)
}

fun Resolver.addNewKeywordMessage(statement: MessageDeclarationKeyword) {
    keywordForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.keywordMsgs[statement.name] = messageData

    // add msg to package declarations
    addMsgToPackageDeclarations(statement)
}

fun Resolver.addMsgToPackageDeclarations(statement: Declaration) {
    val pack = getPackage(currentPackageName)
    pack.declarations.add(statement)
}


fun Resolver.addNewType(type: Type, statement: TypeDeclaration?, pkg: Package? = null) {
    val pack = pkg ?: getPackage(currentPackageName)
    if (pack.types.containsKey(type.name)) {
        throw Exception("Type ${type.name} already registered in project: $currentProjectName in package: $currentPackageName")
    }

    if (statement != null) {
        pack.declarations.add(statement)
    }

    pack.types[type.name] = type
    typeTable[type.name] = type
}


fun Resolver.changeProject(newCurrentProject: String) {
    // clear all current, load project
    currentProjectName = newCurrentProject
    // check that there are no such project already

    if (projects[newCurrentProject] != null) {
        throw Exception("Project with name: $newCurrentProject already exists")
    }
    val commonProject = projects["common"] ?: throw Exception("Can't find common project")


    projects[newCurrentProject] = Project(
        name = newCurrentProject,
        usingProjects = mutableListOf(commonProject)
    )

    TODO()
}

fun Resolver.changePackage(newCurrentPackage: String) {
    currentPackageName = newCurrentPackage

    val currentProject = projects[currentProjectName] ?: throw Exception("Can't find project: $currentProjectName")

    val alreadyExistsPack = currentProject.packages[newCurrentPackage]

    // check that this package not exits already
    if (alreadyExistsPack != null) {
        // load table of types
        typeTable.clear()
        typeTable.putAll(alreadyExistsPack.types)
//        throw Exception("package: $newCurrentPackage already exists")
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
//    val pack = getCurrentPackage()
//    val type = pack.types[typeName] ?: throw Exception("Can't find $typeName for protocol $protocolName")
//
//    val alreadyExistsProtocol = type.protocols[protocolName]
//    if (alreadyExistsProtocol == null) {
//        type.protocols[protocolName] =  Protocol(name = protocolName)
//    }
}

fun Resolver.getTypeForIdentifier(
    x: IdentifierExpr,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type {
    val type = typeTable[x.str]
        ?: currentScope[x.str]
        ?: previousScope[x.str]
        ?: throw Exception("Can't find type for identifier: ${x.str} on line ${x.token.line}")
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
