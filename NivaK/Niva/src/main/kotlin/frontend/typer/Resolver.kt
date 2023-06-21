package frontend.typer

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*

typealias TypeName = String


class Resolver(
    val projectName: String,
    val statements: MutableList<Statement>,
    val projects: MutableMap<String, Project> = mutableMapOf(),
    val typeTable: MutableMap<TypeName, Type> = mutableMapOf(),
    val unaryForType: MutableMap<TypeName, MessageDeclarationUnary> = mutableMapOf(),

    //
//    val previousScope: MutableMap<String, Type> = mutableMapOf(),
//    val currentScope: MutableMap<String, Type> = mutableMapOf(),
    var currentSelf: Type = Resolver.defaultBasicTypes[InternalTypes.Unit]!!,

    var currentProjectName: String = "common",
    var currentPackageName: String = "common",
    var currentProtocolName: String = "common",

    val locals: MutableMap<String, String> = mutableMapOf()
) {
    companion object {
        val defaultBasicTypes: Map<InternalTypes, Type.InternalType> = mapOf(
            InternalTypes.Int to Type.InternalType(
                typeName = InternalTypes.Int,
                isPrivate = false,
                `package` = "common",
                protocols = mutableListOf()
            ),
            InternalTypes.String to Type.InternalType(
                typeName = InternalTypes.String,
                isPrivate = false,
                `package` = "common",
                protocols = mutableListOf()
            ),
            InternalTypes.Float to Type.InternalType(
                typeName = InternalTypes.Float,
                isPrivate = false,
                `package` = "common",
                protocols = mutableListOf()
            ),
            InternalTypes.Boolean to Type.InternalType(
                typeName = InternalTypes.Boolean,
                isPrivate = false,
                `package` = "common",
                protocols = mutableListOf()
            ),
            InternalTypes.Unit to Type.InternalType(
                typeName = InternalTypes.Unit,
                isPrivate = false,
                `package` = "common",
                protocols = mutableListOf()
            )
        )

        init {
            val intType = defaultBasicTypes[InternalTypes.Int]!!
            val stringType = defaultBasicTypes[InternalTypes.String]!!
            val floatType = defaultBasicTypes[InternalTypes.Float]!!
            val boolType = defaultBasicTypes[InternalTypes.Boolean]!!
            val unitType = defaultBasicTypes[InternalTypes.Unit]!!


            intType.protocols.addAll(createIntProtocols(intType, stringType, unitType))
            // TODO add default protocols
        }

    }

    init {
        Resolver.defaultBasicTypes.forEach { k, v ->
            typeTable[k.name] = v
        }

        val defaultProject = Project()
        defaultProject.packages["main"] = Package()
        defaultProject.packages["niva.core"] = Package()
        projects[projectName] = defaultProject
    }
}

fun TypeAST.toType(typeTable: Map<TypeName, Type>): Type {
    return when (name) {
        "Int" ->
            Resolver.defaultBasicTypes[InternalTypes.Int]!!

        "String" ->
            Resolver.defaultBasicTypes[InternalTypes.String]!!

        "Float" ->
            Resolver.defaultBasicTypes[InternalTypes.Float]!!

        "Boolean" ->
            Resolver.defaultBasicTypes[InternalTypes.Boolean]!!

        "Unit" ->
            Resolver.defaultBasicTypes[InternalTypes.Boolean]!!

        else -> {
            typeTable[name] ?: throw Exception("type $name not registered")
        }
    }
}

fun TypeFieldAST.toTypeField(typeTable: Map<TypeName, Type>): TypeField {
    val result = TypeField(
        name = name,
        type = type!!.toType(typeTable)
    )
    return result
}

fun TypeDeclaration.toType(packagge: String, typeTable: Map<TypeName, Type>): Type {

    val result = Type.UserType(
        name = typeName,
        typeArgumentList = listOf(), // for now it's impossible to declare msg for List<Int>
        fields = fields.map { it.toTypeField(typeTable) },
        isPrivate = isPrivate,
        `package` = packagge,
        protocols = mutableListOf()
    )

    return result
}

fun Resolver.resolve(
    statements: List<Statement>,
    currentNode: Int,
    depth: Int,
    previousScope: MutableMap<String, Type> = mutableMapOf(),
    currentSelf: Type = Resolver.defaultBasicTypes[InternalTypes.Unit]!!
): List<Statement> {
    val currentScope = mutableMapOf<String, Type>()
    statements.forEachIndexed { i, statement ->
        resolveStatement(statement, currentNode, depth, currentScope, previousScope, i)
    }
    return statements
}

private fun Resolver.resolveStatement(
    statement: Statement,
    currentNode: Int,
    depth: Int,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
    i: Int
) {
    when (statement) {

        is VarDeclaration -> {
            statement
            resolve(listOf(statement.value), currentNode, depth + 1)
            val valueType = statement.value.type
            val statementDeclaredType = statement.valueType
            if (valueType == null) {
                throw Exception("In var declaration ${statement.name} value doesn't got type")
            }

            if (statementDeclaredType != null) {
                if (statementDeclaredType.name != valueType.name) {
                    val text = "${statementDeclaredType.name} != ${valueType.name}"
                    throw Exception("Type declared for ${statement.name} is not equal for it's value type($text)")
                }
            }

            currentScope[statement.name] = valueType
            println()

        }

        is TypeDeclaration -> {
            val typeName = statement.typeName
            // check if it was already added
            val isAlreadyAdded = typeTable.containsKey(typeName)
            // if it was then error
            if (isAlreadyAdded) {
                throw Exception("type $typeName is already added")
            }
            // if not then add
            typeTable[typeName] = statement.toType(currentPackageName, typeTable)
            println()
        }

        is UnionDeclaration -> {}

        // in the top side, like msg declaration, send i, in other case currentNode
        is MessageDeclarationUnary -> {
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

            // register unary
            unaryForType[statement.name] = statement

            // TODO resolve body
            val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

            val realI = if (depth == 0) i else currentNode
            this.resolve(statement.body, realI, depth + 1, previousAndCurrentScope, forType)

        }

        is MessageDeclarationBinary -> {}
        is MessageDeclarationKeyword -> {}

        is ConstructorDeclaration -> {}


        is KeywordMsg -> {
            // check for constructor
            if (statement.receiver.type == null) {
                val realI = if (depth == 0) i else currentNode
                resolve(listOf(statement.receiver), 0, realI)
            }
            val statementType =
                statement.receiver.type ?: throw Exception("Can't infer receiver ${statement.receiver.str} type")

            val q = typeTable[statementType.name]
            if (q == null) {
                println("${statement.selectorName} usual keyword msg")
            } else {
                // replace current node to already typed constructor
//                val constructorMsg = ConstructorMsg(
//                    receiver = statement.receiver,
//                    selectorName = statement.selectorName,
//                    type = statementType,
//                    token = statement.token,
//                    args = statement.args
//                )
//                this.statements[currentNode] = constructorMsg
                statement.kind = KeywordLikeType.Constructor
                statement.type = statementType
            }

        }

        is BinaryMsg -> {}

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
            val forType =
                statement.receiver.type?.name// ?: throw Exception("${statement.selectorName} hasn't type")
            val receiver = statement.receiver

            receiver.type = when (receiver) {
                is CodeBlock -> TODO()
                is ListCollection -> TODO()
                is BinaryMsg -> TODO()
//                is ConstructorMsg -> TODO()
                is KeywordMsg -> TODO()
//                is SetterMsg -> TODO()
//                is GetterMsg -> TODO()
                is UnaryMsg -> TODO()
                is IdentifierExpr -> TODO()
                is LiteralExpression.FalseExpr -> Resolver.defaultBasicTypes[InternalTypes.Boolean]
                is LiteralExpression.TrueExpr -> Resolver.defaultBasicTypes[InternalTypes.Boolean]
                is LiteralExpression.FloatExpr -> Resolver.defaultBasicTypes[InternalTypes.Float]
                is LiteralExpression.IntExpr -> Resolver.defaultBasicTypes[InternalTypes.Int]
                is LiteralExpression.StringExpr -> Resolver.defaultBasicTypes[InternalTypes.String]
            }
            statement
            println()

        }

        is MessageSendBinary -> {
            val realI = if (depth == 0) i else currentNode
            this.resolve(
                statement.messages,
                realI,
                depth + 1
            )
        }

        is MessageSendKeyword -> {
            val realI = if (depth == 0) i else currentNode

            val resolved = this.resolve(
                statement.messages,
                realI,
                depth + 1
            ) as List<KeywordMsg>
            statement.type = statement.messages.last().type
            println()
        }

        is MessageSendUnary -> {
            val realI = if (depth == 0) i else currentNode
            this.resolve(
                statement.messages,
                realI,
                depth + 1
            )
        }

        is IdentifierExpr -> {
            statement.type = typeTable[statement.str]!!
        }

        is CodeBlock -> {}
        is ListCollection -> {}
        is LiteralExpression.FalseExpr -> {}
        is LiteralExpression.FloatExpr -> {}
        is LiteralExpression.IntExpr -> {}
        is LiteralExpression.StringExpr -> {}
        is LiteralExpression.TrueExpr -> {}

        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType -> {}

        else -> {

        }
    }
}


data class MsgSend(
    val `package`: String,
    val selector: String,
    val project: String,
    val type: MessageDeclarationType
)

sealed class MessageMetadata(
    val name: String,
    val returnType: Type,
    val msgSends: List<MsgSend>
)

class UnaryMsgMetaData(
    name: String,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)

class BinaryMsgMetaData(
    name: String,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)

class KeywordMsgMetaData(
    name: String,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)


////////////

data class TypeField(
    val name: String,
    val type: Type
)

sealed class Type(
    val name: String,
    val `package`: String,
    val isPrivate: Boolean,
    val protocols: MutableList<Protocol> = mutableListOf(),
) {

    class InternalType(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : Type(typeName.name, `package`, isPrivate, protocols)

    class UserType(
        name: String,
        val typeArgumentList: List<Type>,
        val fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : Type(name, `package`, isPrivate, protocols)

}

data class Protocol(
    val unaryMsgs: MutableMap<String, UnaryMsgMetaData> = mutableMapOf(),
    val binaryMsgs: MutableMap<String, BinaryMsgMetaData> = mutableMapOf(),
    val keywordMsgs: MutableMap<String, KeywordMsgMetaData> = mutableMapOf(),
)

class Package(
    // TODO add protocols
    val types: MutableMap<String, Type> = mutableMapOf(),
    val usingPackages: MutableList<Package> = mutableListOf()
)


class Project(
    val packages: MutableMap<String, Package> = mutableMapOf(),
    val usingProjects: MutableList<Project> = mutableListOf()
) {
    init {
//        packages["NivaCore"] = createNivaCorePackage()
    }
}

fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType
): MutableList<Protocol> {
    val result = mutableListOf<Protocol>()

    val arithmeticProtocol = Protocol(
        unaryMsgs = mutableMapOf(
            "str" to UnaryMsgMetaData(
                name = "str",
                returnType = stringType
            ),
            "echo" to UnaryMsgMetaData(
                name = "echo",
                returnType = unitType
            )
        ),
        binaryMsgs = mutableMapOf(
            "+" to BinaryMsgMetaData(
                name = "+",
                returnType = intType
            ),
            "-" to BinaryMsgMetaData(
                name = "-",
                returnType = intType
            ),
            "*" to BinaryMsgMetaData(
                name = "*",
                returnType = intType
            ),
            "/" to BinaryMsgMetaData(
                name = "/",
                returnType = intType
            )
        ),
        keywordMsgs = mutableMapOf(),
    )
    result.add(arithmeticProtocol)


    return result
}
