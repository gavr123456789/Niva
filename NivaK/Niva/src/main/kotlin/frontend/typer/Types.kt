@file:Suppress("unused")

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
    @Suppress("unused")
    val msgSends: List<MsgSend>
)

class UnaryMsgMetaData(
    name: String,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)

class BinaryMsgMetaData(
    name: String,
    argType: Type,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)

class KeywordArg(
    val name: String,
    val type: Type,
)

class KeywordMsgMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    returnType: Type,
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, msgSends)

//class ConstructorMsgMetaData(
//    name: String,
//    returnType: Type,
//    msgSends: List<MsgSend> = listOf()
//) : MessageMetadata(name, returnType, msgSends)

data class TypeField(
    val name: String,
    val type: Type
)


sealed class Type(
    val name: String,
    val `package`: String,
    val isPrivate: Boolean,
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
    var parent: Type? = null // = Resolver.defaultBasicTypes[InternalTypes.Any] ?:
) {


    class Lambda(
        val args: MutableList<TypeField>,
        val returnType: Type,
        `package`: String = "common",
        isPrivate: Boolean = false,
    ) : Type("codeblock${args.map { it.name }} -> ${returnType.name}", `package`, isPrivate)

    sealed class InternalLike(
        typeName: InternalTypes,
        `package`: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol>
    ) : Type(typeName.name, `package`, isPrivate, protocols)

    class InternalType(
        typeName: InternalTypes,
        `package`: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : InternalLike(typeName, `package`, isPrivate, protocols)

    class NullableInternalType(
        name: InternalTypes,
        `package`: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol>
    ) : InternalLike(name, `package`, isPrivate, protocols)

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
    ) : UserLike(name, typeArgumentList, fields, isPrivate, `package`, protocols)

    class GenericType(
        val mainType: Type,
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, `package`, protocols)

    // like List::T
    // not sure if it is needed
//    class UnknownGenericType(
//        `package`: String,
//        typeName: InternalTypes = InternalTypes.Unknown,
//        isPrivate: Boolean = false,
//        protocols: MutableMap<String, Protocol> = mutableMapOf()
//    ) : InternalLike(typeName, `package`, isPrivate, protocols)


    class NullableUserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        `package`: String,
        protocols: MutableMap<String, Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate, `package`, protocols)

}

data class Protocol(
    val name: String,
    val unaryMsgs: MutableMap<String, UnaryMsgMetaData> = mutableMapOf(),
    val binaryMsgs: MutableMap<String, BinaryMsgMetaData> = mutableMapOf(),
    val keywordMsgs: MutableMap<String, KeywordMsgMetaData> = mutableMapOf(),
    val staticMsgs: MutableMap<String, MessageMetadata> = mutableMapOf(),
)

class Package(
    // TODO add protocols
    val packageName: String,
    val declarations: MutableList<Declaration> = mutableListOf(),
    val types: MutableMap<String, Type> = mutableMapOf(),
    val usingPackages: MutableList<Package> = mutableListOf()
)

class Project(
    val name: String,
    val packages: MutableMap<String, Package> = mutableMapOf(),
    val usingProjects: MutableList<Project> = mutableListOf()
)

fun TypeAST.toType(typeTable: Map<TypeName, Type>): Type {

    when (this) {
        is TypeAST.InternalType -> {
            return Resolver.defaultTypes.getOrElse(InternalTypes.valueOf(name)) {
                throw Exception("Can't find type $name ")
                // TODO better inference, depend on context

            }

        }

        is TypeAST.UserType -> {
            return typeTable[name] ?: throw Exception("Can't find type $name ")
        }

        is TypeAST.Lambda -> {
            val lambdaType = Type.Lambda(
                args = inputTypesList.map {
                    TypeField(
                        type = it.toType(typeTable),
                        name = it.name
                    )
                }.toMutableList(),
                returnType = this.returnType.toType(typeTable)
            )
            return lambdaType

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
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!
//        throw Exception("return type of unary message ${this.name} not registered")
    val result = UnaryMsgMetaData(
        name = this.name,
        returnType = returnType,
    )
    return result
}

fun MessageDeclarationBinary.toMessageData(typeTable: MutableMap<TypeName, Type>): BinaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!
//        ?: throw Exception("return type of binary message ${this.name} not registered")

    val argType = this.forType.toType(typeTable)

    val result = BinaryMsgMetaData(
        name = this.name,
        argType = argType,
        returnType = returnType
    )
    return result
}

fun MessageDeclarationKeyword.toMessageData(typeTable: MutableMap<TypeName, Type>): KeywordMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!
//        throw Exception("return type of keyword message ${this.name} not registered")
    val keywordArgs = this.args.map {
        KeywordArg(
            name = it.name,
            type = it.type?.toType(typeTable)
                ?: throw Exception("type of keyword message ${this.name}'s arg ${it.name} not registered")
        )
    }
    val result = KeywordMsgMetaData(
        name = this.name,
        argTypes = keywordArgs,
        returnType = returnType
    )
    return result
}

//fun ConstructorDeclaration.toMessageData(typeTable: MutableMap<TypeName, Type>): ConstructorMsgMetaData {
//    val returnType = this.returnType?.toType(typeTable)
//        ?: throw Exception("return type of constructor message ${this.name} not registered")
//    val result = ConstructorMsgMetaData(
//        name = this.name,
//        returnType = returnType
//    )
//    return result
//}
