package frontend.typer

import frontend.meta.TokenType
import frontend.parser.types.ast.*
import frontend.util.removeDoubleQuotes

typealias TypeName = String


class Resolver(
    val projectName: String,
    val statements: MutableList<Statement>,
    val projects: MutableMap<String, Project> = mutableMapOf(),

    // reload when package changed
    val typeTable: MutableMap<TypeName, Type> = mutableMapOf(),
    val unaryForType: MutableMap<TypeName, MessageDeclarationUnary> = mutableMapOf(),
    val binaryForType: MutableMap<TypeName, MessageDeclarationBinary> = mutableMapOf(),
    val keywordForType: MutableMap<TypeName, MessageDeclarationKeyword> = mutableMapOf(),

    val topLevelStatements: MutableList<Statement> = mutableListOf(),
    var currentLevel: Int = 0,

//    var currentSelf: Type = Resolver.defaultBasicTypes[InternalTypes.Unit]!!,

    var currentProjectName: String = "common",
    var currentPackageName: String = "common",
    var currentProtocolName: String = "common",
) {
    companion object {

        val defaultBasicTypes: Map<InternalTypes, Type.InternalType> = mapOf(
            InternalTypes.Int to Type.InternalType(
                typeName = InternalTypes.Int,
                isPrivate = false,
                `package` = "common",
            ),
            InternalTypes.String to Type.InternalType(
                typeName = InternalTypes.String,
                isPrivate = false,
                `package` = "common",
            ),
            InternalTypes.Float to Type.InternalType(
                typeName = InternalTypes.Float,
                isPrivate = false,
                `package` = "common",
            ),
            InternalTypes.Boolean to Type.InternalType(
                typeName = InternalTypes.Boolean,
                isPrivate = false,
                `package` = "common",
            ),
            InternalTypes.Unit to Type.InternalType(
                typeName = InternalTypes.Unit,
                isPrivate = false,
                `package` = "common",
            ),
            InternalTypes.Project to Type.InternalType(
                typeName = InternalTypes.Project,
                isPrivate = false,
                `package` = "common",
            ),
        )

        init {
            val intType = defaultBasicTypes[InternalTypes.Int]!!
            val stringType = defaultBasicTypes[InternalTypes.String]!!
            val floatType = defaultBasicTypes[InternalTypes.Float]!!
            val boolType = defaultBasicTypes[InternalTypes.Boolean]!!
            val unitType = defaultBasicTypes[InternalTypes.Unit]!!


            intType.protocols.putAll(createIntProtocols(intType, stringType, unitType))
            // TODO add default protocols for other types
        }

    }

    init {
        Resolver.defaultBasicTypes.forEach { k, v ->
            typeTable[k.name] = v
        }

        val defaultProject = Project("common")
        defaultProject.packages["common"] = Package("common")
        val corePackage = Package("core")
        defaultProject.packages["core"] = corePackage

        // add all default types to core package
        defaultBasicTypes.forEach { (k, v) ->
            corePackage.types[k.name] = v
        }

        projects[projectName] = defaultProject
    }
}


// нужен механизм поиска типа, чтобы если не нашли метод в текущем типе, то посмотреть в Any
fun Resolver.resolve(
    statements: List<Statement>,
    previousScope: MutableMap<String, Type>,
): List<Statement> {
    val currentScope = mutableMapOf<String, Type>()

    statements.forEachIndexed { i, statement ->

        resolveStatement(
            statement,
            currentScope,
            previousScope,
            i
        )
    }
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
        is ConstructorDeclaration -> TODO()

        is MessageDeclaration -> {
            // check if the type already registered
            // if no then error
            val forType = typeTable[statement.forType.name]
                ?: throw Exception("type ${statement.forType.name} is not registered")

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

                is ConstructorDeclaration -> TODO()

            }

            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            previousAndCurrentScope["self"] = forType
            val body = this.resolve(statement.body, previousAndCurrentScope)

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
    i: Int
) {

    val resolveTypeForMessageSend = { statement2: MessageSend ->
        if (statement2.receiver.str != "Project") {
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
            this.resolve(
                statement2.messages,
                previousAndCurrentScope
            )

            // TODO check then return parameter of each send match the next input parameter
            statement2.type =
                statement2.messages.last().type
                    ?: throw Exception("Not all messages of ${statement2.str} has types")

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
            resolve(listOf(statement.value), previousAndCurrentScope)

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

            if (currentLevel == 0) topLevelStatements.add(statement)

        }


        is KeywordMsg -> {


            // check for constructor
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                resolve(listOf(statement.receiver), previousAndCurrentScope)
            }
            val receiverType =
                statement.receiver.type ?: throw Exception("Can't infer receiver ${statement.receiver.str} type")


            // check that receiver is real type
            // Person name: "sas"
            val receiverText = statement.receiver.str
            val q = typeTable[receiverText]
            if (receiverText == "Project") {
                // this is project setter
                throw Error("We cant get here, type Project are ignored")
            } else if (q == null) {
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
        }

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
            val forType =
                statement.receiver.type?.name// ?: throw Exception("${statement.selectorName} hasn't type")
            val receiver = statement.receiver

            receiver.type = when (receiver) {
                is CodeBlock -> TODO()
                is ListCollection -> TODO()
                is BinaryMsg -> TODO()
                is KeywordMsg -> TODO()
                is UnaryMsg -> TODO()
                is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)
                is LiteralExpression.FalseExpr -> Resolver.defaultBasicTypes[InternalTypes.Boolean]
                is LiteralExpression.TrueExpr -> Resolver.defaultBasicTypes[InternalTypes.Boolean]
                is LiteralExpression.FloatExpr -> Resolver.defaultBasicTypes[InternalTypes.Float]
                is LiteralExpression.IntExpr -> Resolver.defaultBasicTypes[InternalTypes.Int]
                is LiteralExpression.StringExpr -> Resolver.defaultBasicTypes[InternalTypes.String]
            }
            val receiverType = receiver.type!!
            // check for getter
            if (receiverType is Type.UserType) {

                val fieldWithSameName = receiverType.fields.find { it.name == statement.selectorName }

                if (fieldWithSameName != null) {
                    statement.kind = UnaryMsgKind.Getter
                    statement.type = fieldWithSameName.type
                }
            } else {
                // find this message
                val messageReturnType = findUnaryMessageType(receiverType, statement.selectorName)
                statement.kind = UnaryMsgKind.Unary
                statement.type = messageReturnType
            }


        }

        is MessageSend -> {
            resolveTypeForMessageSend(statement)
            if (currentLevel == 0) topLevelStatements.add(statement)

        }


        is IdentifierExpr -> {
            statement.type = typeTable[statement.str]
                ?: previousScope[statement.str]
                        ?: currentScope[statement.str]!!

        }

        is CodeBlock -> {


            if (currentLevel == 0) {
                topLevelStatements.add(statement)
            }
        }

        is ListCollection -> {


        }

        is LiteralExpression.FloatExpr ->
            statement.type = Resolver.defaultBasicTypes[InternalTypes.Float]

        is LiteralExpression.IntExpr ->
            statement.type = Resolver.defaultBasicTypes[InternalTypes.Int]

        is LiteralExpression.StringExpr ->
            statement.type = Resolver.defaultBasicTypes[InternalTypes.String]

        is LiteralExpression.TrueExpr ->
            statement.type = Resolver.defaultBasicTypes[InternalTypes.Boolean]

        is LiteralExpression.FalseExpr ->
            statement.type = Resolver.defaultBasicTypes[InternalTypes.Boolean]

        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType -> {}

        else -> {

        }
    }
}

private fun Resolver.findUnaryMessageType(receiverType: Type, selectorName: String): Type {
    receiverType.protocols.forEach { (k, v) ->
        val q = v.unaryMsgs[selectorName]
        if (q != null) {
            return q.returnType
        }
    }
    throw Error("Cant find unary message: $selectorName for type ${receiverType.name}")
}

fun Resolver.getCurrentPackage(): Package {
    val p = this.projects[currentProjectName] ?: throw Exception("there are no such project: $currentProjectName")
    val pack = p.packages[currentPackageName] ?: throw Exception("there are no such package: $currentPackageName")
    return pack
}

fun Resolver.getCurrentProtocol(typeName: String): Protocol {
    val pack = getCurrentPackage()
    val type2 = pack.types[typeName]
        ?: throw Exception("there are no such type: ${typeName} in package $currentPackageName in project: $currentProjectName")
    val protocol =
        type2.protocols[currentProtocolName] //?: throw Exception("there no such protocol: $currentProtocolName in type: ${type2.name} in package $currentPackageName in project: $currentProjectName")
    if (protocol == null) {
        val newProtocol = Protocol(currentProtocolName)
        type2.protocols[currentProtocolName] = newProtocol
        return newProtocol
    }
    return protocol
}


fun Resolver.addNewUnaryMessage(statement: MessageDeclarationUnary) {
    unaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.unaryMsgs[statement.name] = messageData

}

fun Resolver.addNewBinaryMessage(statement: MessageDeclarationBinary) {
    binaryForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.binaryMsgs[statement.name] = messageData

}

fun Resolver.addNewKeywordMessage(statement: MessageDeclarationKeyword) {
    keywordForType[statement.name] = statement // will be reloaded when package changed

    val protocol = getCurrentProtocol(statement.forType.name)
    val messageData = statement.toMessageData(typeTable)
    protocol.keywordMsgs[statement.name] = messageData

}


fun Resolver.addNewType(type: Type, statement: TypeDeclaration) {
    val pack = getCurrentPackage()
    if (pack.types.containsKey(type.name)) {
        throw Exception("Type ${type.name} already registered in project: $currentProjectName in package: $currentPackageName")
    }

    pack.declarations.add(statement)

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

fun Resolver.registerNewMethod(type: Type) {
    // register method in a current project, package, protocol
}

fun Resolver.lookForTypeInImportedProjects(): Type {
    TODO()
}

fun Resolver.getTypeForIdentifier(
    x: IdentifierExpr,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Type =
    typeTable[x.str]
        ?: currentScope[x.str]
        ?: previousScope[x.str]
        ?: throw Exception("Can't find type ${x.str}")
