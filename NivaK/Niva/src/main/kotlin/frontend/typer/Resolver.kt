package frontend.typer

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.*
import frontend.util.removeDoubleQuotes
import lex
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
//            createDefaultType(InternalTypes.Unknown),

//            createDefaultType(InternalTypes.List),
//            createDefaultType(InternalTypes.Map),
//            createDefaultType(InternalTypes.Set),


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
//            val listType = defaultTypes[InternalTypes.List]!!
//            val unknownGenericType = defaultTypes[InternalTypes.Unknown]!!


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

//            listType.protocols.putAll(
//                createListProtocols(
//                    intType = intType,
//                    stringType = stringType,
//                    unitType = unitType,
//                    boolType = boolType,
//                    listType = listType,
//                    anyType = anyType,
//                    unknownGenericType = unknownGenericType
//                )
//            )

            // TODO add default protocols for other types
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
        val defaultProject = Project("common")
        defaultProject.packages["common"] = Package("common")
        val corePackage = Package("core")
        defaultProject.packages["core"] = corePackage

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
            fields = listOf(),
            pkg = "core",
        )
        val listTypeOfDifferentGeneric = Type.UserType(
            name = "List",
            typeArgumentList = listOf(differentGenericType),
            fields = listOf(),
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

        projects[projectName] = defaultProject
        ///////generate ast from files////////
        if (statements.isEmpty()) {
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
) {
    currentLevel += 1

    when (statement) {
        is TypeDeclaration -> {

            val newType = statement.toType(currentPackageName, typeTable)
            addNewType(newType, statement)
        }

        is UnionDeclaration -> TODO()
        is AliasDeclaration -> TODO()

        is MessageDeclaration -> {
            // check if the type already registered
            val forType = typeTable[statement.forType.name]
                ?: statement.token.compileError("type ${statement.forType.name} is not registered")

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

            bodyScope["this"] = forType

            val previousAndCurrentScope = (previousScope + bodyScope).toMutableMap()

            this.resolve(statement.body, previousAndCurrentScope, statement)


            // TODO check that return type is the same as declared return type, or if it not declared -> assign it

        }

    }
    currentLevel -= 1
}

//fun<T> sas(x: T): T {
//    return 3
//}

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
        is Declaration -> resolveDeclarations(statement, previousScope)
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


        is KeywordMsg -> {
            /// check for constructor
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                resolve(listOf(statement.receiver), previousAndCurrentScope, statement)
            }
            val receiverType =
                statement.receiver.type
                    ?: statement.token.compileError("Can't infer receiver ${statement.receiver.str} type")


            fun resolveKindOfKeyword(statement: KeywordMsg): KeywordLikeType {
                if (receiverType is Type.Lambda) {
                    return KeywordLikeType.ForCodeBlock
                }
                val receiverText = statement.receiver.str
                val q = typeTable[receiverText]
                if (receiverText == "Project") {
                    statement.token.compileError("We cant get here, type Project are ignored")
                }

                if (q != null) {
                    statement.kind = KeywordLikeType.Constructor
                    return KeywordLikeType.Constructor
                }
                // This is Setter or Keyword now

                // if the amount of keyword's arg is 1, and its name on of the receiver field, then its setter
                if (statement.args.count() == 1 && receiverType is Type.UserType) {
                    val keyArgText = statement.args[0].selectorName
                    // find receiver arg same as keyArgText
                    val receiverArgWithSameName = receiverType.fields.find { it.name == keyArgText }
                    if (receiverArgWithSameName != null) {
                        // this is setter
                        statement.kind = KeywordLikeType.Setter
                        statement.type = receiverArgWithSameName.type
                        return KeywordLikeType.Setter
                    }
                }
                // this is Keyword
                statement.kind = KeywordLikeType.Keyword
                return KeywordLikeType.Keyword
            }

            val kind = resolveKindOfKeyword(statement)

            val letterToRealType = mutableMapOf<String, Type>();
            /// resolve arguments
            {
                val msgTypeFromDB = if (kind == KeywordLikeType.Keyword) findKeywordMsgType(
                    receiverType,
                    statement.selectorName,
                    statement.token
                ) else null


                // resolve args types
                val args = statement.args
                args.forEachIndexed { argNum, it ->
                    if (it.keywordArg.type == null) {
                        val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                        currentLevel++
                        currentArgumentNumber = argNum
                        resolve(listOf(it.keywordArg), previousAndCurrentScope, statement)
                        currentLevel--

                        if (it.unaryOrBinaryMsgsForArg != null) {
                            resolve(it.unaryOrBinaryMsgsForArg, previousAndCurrentScope, statement)
                        }
                        val argType =
                            if (it.unaryOrBinaryMsgsForArg == null) it.keywordArg.type!! else it.unaryOrBinaryMsgsForArg.last().type!!

                        // we need to check for generic args only if it is Keyword
                        if (msgTypeFromDB != null) {
                            val typeFromDBForThisArg = msgTypeFromDB.argTypes[argNum].type
                            if (typeFromDBForThisArg.name.length == 1 && typeFromDBForThisArg.name[0].isUpperCase()) {
                                letterToRealType[typeFromDBForThisArg.name] = argType
                            }

                            if (typeFromDBForThisArg is Type.Lambda) {
                                if (argType !is Type.Lambda) {
                                    throw Exception("If typeFromDBForThisArg is lambda then argType must be lambda")
                                }
                                /// remember letter to type args
                                typeFromDBForThisArg.args.forEachIndexed { i, typeField ->
                                    val beforeGenericResolvedName = typeField.type.beforeGenericResolvedName
                                    if (typeField.type.name.length == 1 && typeField.type.name[0].isUpperCase()) {
                                        letterToRealType[typeFromDBForThisArg.name] = argType.args[i].type
                                    } else if (beforeGenericResolvedName != null && beforeGenericResolvedName.length == 1 && beforeGenericResolvedName[0].isUpperCase()) {
                                        letterToRealType[beforeGenericResolvedName] = argType.args[i].type
                                    }
                                }
                                /// remember letter to return type
                                val returnTypeBefore = typeFromDBForThisArg.returnType.beforeGenericResolvedName

                                if (typeFromDBForThisArg.returnType.name.length == 1 && typeFromDBForThisArg.returnType.name[0].isUpperCase()) {
                                    letterToRealType[typeFromDBForThisArg.returnType.name] = argType.returnType
                                } else if (returnTypeBefore != null && returnTypeBefore.length == 1 && returnTypeBefore[0].isUpperCase()) {
                                    letterToRealType[returnTypeBefore] = argType.returnType
                                }

                            }
                        }


                    }
                }
                currentArgumentNumber = -1
            }()
            ///

            // if receiverType is lambda then we need to check does it have same argument names and types
            if (receiverType is Type.Lambda) {

                // need
                if (receiverType.args.count() != statement.args.count()) {
                    val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                    val setOfNeededFields = receiverType.args.map { it.name }.toSet()
                    val extraOrMissed = statement.args.count() > receiverType.args.count()
                    val whatIsMissingOrExtra =
                        if (extraOrMissed)
                            (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                        else
                            (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }

                    val beginText =
                        statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                    val text =
                        if (extraOrMissed)
                            "For $beginText code block eval, extra fields are listed: $whatIsMissingOrExtra"
                        else
                            "For $beginText code block eval, not all fields are listed, you missed: $whatIsMissingOrExtra"
                    statement.token.compileError(text)
                }

                statement.args.forEachIndexed { ii, it ->
                    // name check
                    if (it.selectorName != receiverType.args[ii].name) {
                        statement.token.compileError("${it.selectorName} is not valid arguments for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.selectorName }} on Line ${statement.token.line}")
                    }
                    // type check
                    val isTypesEqual = compare2Types(it.keywordArg.type!!, receiverType.args[ii].type)
                    if (!isTypesEqual) {
                        statement.token.compileError(
                            "${it.selectorName} is not valid type for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.keywordArg.type?.name }}"
                        )
                    }
                }

                statement.type = receiverType.returnType
                statement.kind = KeywordLikeType.ForCodeBlock
                return
            }



            when (kind) {
                KeywordLikeType.Constructor -> {
                    // check that all fields are filled
                    var replacerTypeIfItGeneric: Type? = null
                    if (receiverType is Type.UserType) {
                        val receiverFields = receiverType.fields
                        // check that amount of arguments if right
                        if (statement.args.count() != receiverFields.count()) {

                            val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                            val setOfNeededFields = receiverFields.map { it.name }.toSet()
                            val extraOrMissed = statement.args.count() > receiverFields.count()
                            val whatIsMissingOrExtra =
                                if (extraOrMissed)
                                    (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                                else
                                    (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }


                            val errorText =
                                statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                            val text =
                                if (extraOrMissed)
                                    "For $errorText constructor call, extra fields are listed: $whatIsMissingOrExtra"
                                else
                                    "For $errorText constructor call, not all fields are listed, you missed: $whatIsMissingOrExtra"
                            statement.token.compileError(text)
                        }

                        statement.args.forEachIndexed { i, arg ->
                            val typeField = receiverFields[i].name
                            // check that every arg name is right
                            if (typeField != arg.selectorName) {
                                statement.token.compileError("In constructor message for type ${statement.receiver.str} field $typeField != ${arg.selectorName}")
                            }
                        }

                        // replace every Generic type with real
                        if (receiverType.typeArgumentList.isNotEmpty()) {
                            replacerTypeIfItGeneric = Type.UserType(
                                name = receiverType.name,
                                typeArgumentList = receiverType.typeArgumentList.toList(),
                                fields = receiverType.fields.toList(),
                                isPrivate = receiverType.isPrivate,
                                pkg = receiverType.pkg,
                                protocols = receiverType.protocols.toMutableMap()
                            )
                            // match every type argument with fields
                            // replace fields types to real one
                            val map = mutableMapOf<String, Type>()
                            replacerTypeIfItGeneric.typeArgumentList.forEach { typeArg ->
                                val fieldsOfThisType =
                                    replacerTypeIfItGeneric.fields.filter { it.type.name == typeArg.name }
                                fieldsOfThisType.forEach { genericField ->
                                    // find real type from arguments
                                    val real = statement.args.find { it.selectorName == genericField.name }
                                        ?: statement.token.compileError("Can't find real type for field: ${genericField.name} of generic type: ${genericField.type.name}")
                                    val realType = real.keywordArg.type
                                        ?: real.keywordArg.token.compileError("Panic: ${real.selectorName} doesn't have type")
                                    genericField.type = realType
                                    map[typeArg.name] = realType
                                }
                            }
                            // replace typeFields to real ones
                            val realTypes = replacerTypeIfItGeneric.typeArgumentList.toMutableList()
                            map.forEach { (fieldName, fieldRealType) ->
                                val fieldIndex = realTypes.indexOfFirst { it.name == fieldName }
                                realTypes[fieldIndex] = fieldRealType
                            }
                            replacerTypeIfItGeneric.typeArgumentList = realTypes
                        }

                    }

                    statement.type = replacerTypeIfItGeneric ?: receiverType
                }

                KeywordLikeType.Setter -> {
                    // Nothing to do, because checke for setter already sets the type of statement
                }

                KeywordLikeType.Keyword -> {
                    val msgTypeFromDB = findKeywordMsgType(receiverType, statement.selectorName, statement.token)

                    val returnType = if (msgTypeFromDB.returnType is Type.UnknownGenericType) {
                        val realTypeFromTable = letterToRealType[msgTypeFromDB.returnType.name]
                        if (realTypeFromTable == null) {
                            throw Exception("Cant find generic type ${msgTypeFromDB.returnType.name} in letterToRealType table $letterToRealType")
                        }
                        realTypeFromTable
                    } else if (msgTypeFromDB.returnType is Type.UserType && msgTypeFromDB.returnType.typeArgumentList.find { it.name.length == 1 } != null) {
                        // что если у обычного кейворда возвращаемый тип имеет нересолвнутые женерик параметры
                        val returnType = msgTypeFromDB.returnType
                        val newResolvedTypeArgs = mutableListOf<Type>()

                        // идем по каждому, если он не резолвнутый, то добавляем из таблицы, если резолвнутый то добавляем так
                        returnType.typeArgumentList.forEach { typeArg ->
                            val isNotResolved = typeArg.name.length == 1 && typeArg.name[0].isUpperCase()
                            if (isNotResolved) {
                                val resolvedLetterType = letterToRealType[typeArg.name]
                                if (resolvedLetterType == null) {
                                    throw Exception("Can't find generic type: ${typeArg.name} in letter table")
                                }
                                newResolvedTypeArgs.add(resolvedLetterType)
                                resolvedLetterType.beforeGenericResolvedName = typeArg.name
                            } else {
                                newResolvedTypeArgs.add(typeArg)
                            }
                        }


                        Type.UserType(
                            name = returnType.name,
                            typeArgumentList = newResolvedTypeArgs,
                            fields = returnType.fields,
                            isPrivate = returnType.isPrivate,
                            pkg = returnType.pkg,
                            protocols = returnType.protocols
                        )
                    } else msgTypeFromDB.returnType
                    statement.type = returnType
                }

                KeywordLikeType.ForCodeBlock -> {
                    throw Exception("We can't reach here, because we do early return")
                }
            }

        }

        is BinaryMsg -> {
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


            // resolve messages
            if (statement.unaryMsgsForArg.isNotEmpty()) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForArg, previousAndCurrentScope, statement)
                currentLevel--
            }

            val isUnaryForReceiver = statement.unaryMsgsForReceiver.isNotEmpty()
            if (isUnaryForReceiver) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForReceiver, previousAndCurrentScope, statement)
                currentLevel--
            }
            // q = "sas" + 2 toString
            // find message for this type
            val messageReturnType =
                if (isUnaryForReceiver)
                    findBinaryMessageType(
                        statement.unaryMsgsForReceiver.last().type!!,
                        statement.selectorName,
                        statement.token
                    )
                else
                    findBinaryMessageType(receiverType, statement.selectorName, statement.token)
            statement.type = messageReturnType

        }

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
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

                    is MessageSend -> TODO()


                    is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)


                    is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                    is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                    is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                }

            // if this is message for type
            if (receiver is IdentifierExpr) {
                if (typeTable[receiver.str] != null) {
                    isStaticCall = true
                }
            }
            val receiverType = receiver.type!!



            if (receiverType is Type.Lambda) {
                if (statement.selectorName != "exe") {
                    if (receiverType.args.isNotEmpty())
                        statement.token.compileError("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                    else
                        statement.token.compileError("For lambda ${statement.str} on Line ${statement.token.line} you can use only unary 'do' message")
                }


                if (receiverType.args.isNotEmpty()) {
                    statement.token.compileError("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
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
                        findUnaryMessageType(receiverType, statement.selectorName, statement.token)
                    else
                        findStaticMessageType(receiverType, statement.selectorName, statement.token)

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
            if (currentLevel == 0) topLevelStatements.add(statement)
        }

        is ExpressionInBrackets -> {
            resolveExpressionInBrackets(statement, previousScope, currentScope)
            if (currentLevel == 0) topLevelStatements.add(statement)
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
            var metaDataFound: KeywordMsgMetaData? = null
            var itArgType: Type? = null
            // if this is lambda with one arg, then add "it" to scope
            // TODO don't add it if this lambda has named arg
            val genericLetterToTypes = mutableMapOf<String, Type>()
            if (rootStatement != null && rootStatement is KeywordMsg && currentArgumentNumber != -1) {
                if (rootStatement.receiver !is CodeBlock) {
                    val rootReceiverType = rootStatement.receiver.type!!
                    val metaData = findKeywordMsgType(
                        rootReceiverType,
                        rootStatement.selectorName,
                        rootStatement.token
                    )
                    metaDataFound = metaData
                    val currentArgType = metaData.argTypes[currentArgumentNumber]

                    // List(T, G) map::[T -> G] -> G = []

                    val we = typeTable[rootReceiverType.name]
                    if (we is Type.UserType && rootReceiverType is Type.UserType) {
                        we.typeArgumentList.forEachIndexed { i, it ->
                            if (it.name.length == 1 && it.name[0].isUpperCase()) {
                                val sameButResolvedArg = rootReceiverType.typeArgumentList[i]

                                if (sameButResolvedArg.name.length == 1) {
                                    throw Exception("Arg ${sameButResolvedArg.name} is unresolved")
                                }
                                genericLetterToTypes[it.name] = sameButResolvedArg
                            }
                        }
                    }


                    if (currentArgType.type is Type.Lambda) {
                        currentArgType.type.args.forEachIndexed { i, labmdaArg ->
                            if (labmdaArg.type is Type.UnknownGenericType && rootReceiverType is Type.UserType) {

                                // Заного получить из базы тип ресивера, он будет нересолвнутым, и там будут правильным буквы
                                // теперь у нас есть 2 типа, с резульвнутыми и без
                                // сопоставить тупа по порядку буквы к типам
                                // сопоставлять именно
//                                val realType =
//                                    rootReceiverType.typeArgumentList.find { arg -> arg.beforeGenericResolvedName == labmdaArg.type.name }!!
////                                it.type = realType
//                                val beforeGenericResolvedName = realType.beforeGenericResolvedName
//                                if (beforeGenericResolvedName != null) {
//                                    genericLetterToTypes[beforeGenericResolvedName] = realType
//                                }
                                // TODO именно тут, где у нас еще есть метадата, нам нужно назначить настоящие типы для дженерик параметров
//                                realType.beforeGenericResolvedName = currentArgType.type.returnType.name

                            }
                        }


                        if (currentArgType.type.args.count() == 1) {
                            val typeOfFirstArgs = currentArgType.type.args[0].type

                            val typeForIt = if (typeOfFirstArgs !is Type.UnknownGenericType) {
                                typeOfFirstArgs
                            } else {
                                val foundRealType = genericLetterToTypes[typeOfFirstArgs.name]
                                if (foundRealType == null) {
                                    throw Exception("Can't find resolved type ${typeOfFirstArgs.name} while resolvind lambda")
                                }
                                foundRealType
                            }
                            previousAndCurrentScope["it"] = typeForIt
                            itArgType = typeForIt
                        }

                    }

                    isThisWhileCycle = false
                }
            }

            currentLevel++
            resolve(statement.statements, previousAndCurrentScope, statement)
            currentLevel--
            val lastExpression = statement.statements.last()

            // Add lambda type to code-block itself
            val returnType =
                if (lastExpression is Expression) lastExpression.type!! else Resolver.defaultTypes[InternalTypes.Unit]!!

            val args = statement.inputList.map {
                TypeField(name = it.name, type = it.type!!)
            }.toMutableList()


            if (itArgType != null && args.isEmpty()) {
                if (compare2Types(returnType, itArgType) && metaDataFound != null) {
                    val e = metaDataFound.argTypes[0]
                    if (e.type is Type.Lambda) {
                        returnType.beforeGenericResolvedName = e.type.returnType.name
                    }
                }
                args.add(TypeField("it", itArgType))
            }
            val type = Type.Lambda(
                args = args,
                returnType = returnType, // Тут у return Type before должен быть G
                pkg = currentPackageName
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
                                pkg = currentPackageName
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
                        fields = listOf(),
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
            if (statement.ifBranches.isEmpty()) {
                statement.token.compileError("If must contain at least one branch")
            }

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()


            when (statement) {
                is ControlFlow.IfExpression -> {


                    var firstBranchReturnType: Type? = null

                    statement.ifBranches.forEachIndexed { i, it ->
                        /// resolving if
                        resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
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
                                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
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
                                it.ifExpression.token.compileError(
                                    "In if Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} " +
                                            "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                                )
                            }
                        } else {
                            firstBranchReturnType = it.getReturnTypeOrThrow()
                        }
                    }


                    if (statement.elseBranch == null) {
                        statement.token.compileError("If expression must contain else branch")
                    }


                    resolve(statement.elseBranch, previousAndCurrentScope, statement)
                    val lastExpr = statement.elseBranch.last()
                    if (lastExpr !is Expression) {
                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
                    }
                    val elseReturnType = lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)")
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
                            curTok.compileError("If branch ${curTok.lexeme} on line: ${curTok.line} is not of switching Expr type: ${statement.switch.type!!.name}")
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
                                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
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
                                it.ifExpression.token.compileError(
                                    "In switch Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} "
                                            + "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                                )
                            }
                        } else {
                            firstBranchReturnType = it.getReturnTypeOrThrow()
                        }
                    }


                    if (statement.elseBranch == null) {
                        statement.token.compileError("If expression must contain else branch")
                    }


                    resolve(statement.elseBranch, previousAndCurrentScope, statement)
                    val lastExpr = statement.elseBranch.last()
                    if (lastExpr !is Expression) {
                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
                    }
                    val elseReturnType = lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)")

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

private fun Resolver.resolveExpressionInBrackets(
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


private fun Resolver.compare2Types(type1: Type, type2: Type): Boolean {
    // TODO temp
    if (type1 is Type.Lambda || type2 is Type.Lambda) {
        return true
    }

    return type1.name == type2.name
}

private fun Resolver.findUnaryMessageType(receiverType: Type, selectorName: String, token: Token): Type {
    receiverType.protocols.forEach { (_, v) ->
        val q = v.unaryMsgs[selectorName]

        if (q != null) {
            return q.returnType
        }
    }
    token.compileError("Cant find unary message: $selectorName for type ${receiverType.name}")
}

private fun Resolver.findStaticMessageType(receiverType: Type, selectorName: String, token: Token): Type {
    receiverType.protocols.forEach { (_, v) ->
        val q = v.staticMsgs[selectorName]
        if (q != null) {
            return q.returnType
        }
    }
    token.compileError("Cant find static message: $selectorName for type ${receiverType.name}")
}

private fun Resolver.findBinaryMessageType(receiverType: Type, selectorName: String, token: Token): Type {
    if (receiverType.name.length == 1 && receiverType.name[0].isUpperCase()) {
        throw Exception("Can't receive generic type to find binary method for it")
    }
    receiverType.protocols.forEach { (_, v) ->
        val q = v.binaryMsgs[selectorName]
        if (q != null) {
            return q.returnType
        }
    }
    token.compileError("Cant find binary message: $selectorName for type ${receiverType.name}")
}

private fun Resolver.findKeywordMsgType(receiverType: Type, selectorName: String, token: Token): KeywordMsgMetaData {
    if (receiverType.name.length == 1 && receiverType.name[0].isUpperCase()) {
        throw Exception("Can't receive generic type to find keyword method for it")
    }

    receiverType.protocols.forEach { (_, v) ->
        val q = v.keywordMsgs[selectorName]
        if (q != null) {
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

fun Resolver.addNewType(type: Type, statement: TypeDeclaration?, pkg: Package? = null) {
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
