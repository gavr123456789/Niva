package frontend.typer

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.InternalTypes
import frontend.parser.types.ast.Statement

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
        packages["NivaCore"] = createNivaCorePackage()
    }
}

class Resolver(
    val projectName: String,
    val statements: List<Statement>,
    val projects: MutableMap<String, Project> = mutableMapOf(),
    val typeTable: MutableMap<UInt, Type> = mutableMapOf(),
    var typeCounter: UInt = 1u
) {
    init {
        val defaultProject = Project()
        defaultProject.packages["main"] = Package()
        defaultProject.packages["niva.core"] = Package()
        projects[projectName] = defaultProject
    }
}


fun createNivaCorePackage(): Package {
    val result = Package()
    val intType = createIntType()
    result.types["Int"] = intType


    return result
}


fun createIntType(): Type.InternalType {
    val intType = Type.InternalType(
        typeName = InternalTypes.Int,
        isPrivate = false,
        `package` = "niva.core",
        protocols = mutableListOf()
    )

    val intProtocols = createIntProtocols(intType)
    intType.protocols.addAll(intProtocols)

    return intType
}

fun createIntProtocols(intType: Type): MutableList<Protocol> {
    val result = mutableListOf<Protocol>()

    val arithmeticProtocol = Protocol(
        unaryMsgs = mutableMapOf(),
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
