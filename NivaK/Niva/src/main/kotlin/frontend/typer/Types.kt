package frontend.typer

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*

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

class ConstructorMsgMetaData(
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
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
) {

    sealed class InternalLike(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : Type(typeName.name, `package`, isPrivate, protocols)

    class InternalType(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : InternalLike(typeName, isPrivate, `package`, protocols)
    class NullableInternalType(
        typeName: InternalTypes,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : InternalLike(typeName, isPrivate, `package`, protocols)

    sealed class UserLike(
        name: String,
        val typeArgumentList: List<Type>,
        val fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : Type(name, `package`, isPrivate, protocols)

    class UserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate,`package`, protocols)

    class NullableUserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate,`package`, protocols)

}

data class Protocol(
    val name: String,
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
    val name: String,
    val packages: MutableMap<String, Package> = mutableMapOf(),
    val usingProjects: MutableList<Project> = mutableListOf()
)

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
        protocols = mutableMapOf()
    )

    return result
}


fun MessageDeclarationUnary.toMessageData(typeTable: MutableMap<TypeName, Type>): UnaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable) ?: throw Exception("retrun type of unary message ${this.name} not registered")
    val result = UnaryMsgMetaData(
        name = this.name,
        returnType = returnType,
        )
    return result
}
fun MessageDeclarationBinary.toMessageData(typeTable: MutableMap<TypeName, Type>): BinaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable) ?: throw Exception("retrun type of unary message ${this.name} not registered")
    val result = BinaryMsgMetaData(
        name = this.name,
        returnType = returnType
    )
    return result
}
fun MessageDeclarationKeyword.toMessageData(typeTable: MutableMap<TypeName, Type>): KeywordMsgMetaData {
    val returnType = this.returnType?.toType(typeTable) ?: throw Exception("retrun type of unary message ${this.name} not registered")
    val result = KeywordMsgMetaData(
        name = this.name,
        returnType = returnType
    )
    return result
}
fun ConstructorDeclaration.toMessageData(typeTable: MutableMap<TypeName, Type>): ConstructorMsgMetaData {
    val returnType = this.returnType?.toType(typeTable) ?: throw Exception("retrun type of unary message ${this.name} not registered")
    val result = ConstructorMsgMetaData(
        name = this.name,
        returnType = returnType
    )
    return result
}