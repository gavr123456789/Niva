package frontend.typer

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.InternalTypes
import frontend.parser.types.ast.TypeAST
import frontend.parser.types.ast.TypeDeclaration
import frontend.parser.types.ast.TypeFieldAST

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

    sealed class InternalLike(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : Type(typeName.name, `package`, isPrivate, protocols)

    class InternalType(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : InternalLike(typeName, isPrivate, `package`, protocols)
    class NullableInternalType(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : InternalLike(typeName, isPrivate, `package`, protocols)

    sealed class UserLike(
        name: String,
        val typeArgumentList: List<Type>,
        val fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : Type(name, `package`, isPrivate, protocols)

    class UserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate,`package`, protocols)

    class NullableUserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableList<Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate,`package`, protocols)

}

data class Protocol(
    val unaryMsgs: MutableMap<String, UnaryMsgMetaData> = mutableMapOf(),
    val binaryMsgs: MutableMap<String, BinaryMsgMetaData> = mutableMapOf(),
    val keywordMsgs: MutableMap<String, KeywordMsgMetaData> = mutableMapOf(),
)

class Package(
    // TODO add protocols
    val packageName: String,
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
