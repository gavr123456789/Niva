@file:Suppress("unused")

package frontend.typer

import frontend.meta.compileError
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*

data class MsgSend(
    val pkg: String,
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

class TypeField(
    val name: String,
    var type: Type //when generic, we need to reassign it to real type
) {
    override fun toString(): String {
        return "$name: $type"
    }
}


sealed class Type(
    val name: String, // when generic, we need to reassign it to AST's Type field, instead of type's typeField
    val pkg: String,
    val isPrivate: Boolean,
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
    var parent: Type? = null, // = Resolver.defaultBasicTypes[InternalTypes.Any] ?:
    var beforeGenericResolvedName: String? = null
) {
    override fun toString(): String {
        return "Type: $name"
    }


    class Lambda(
        val args: MutableList<TypeField>,
        val returnType: Type,
        pkg: String = "common",
        isPrivate: Boolean = false,
    ) : Type("[${args.joinToString(", ") { it.type.name }} -> ${returnType.name}]", pkg, isPrivate)

    sealed class InternalLike(
        typeName: InternalTypes,
        pkg: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol>
    ) : Type(typeName.name, pkg, isPrivate, protocols)

    class InternalType(
        typeName: InternalTypes,
        pkg: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : InternalLike(typeName, pkg, isPrivate, protocols)

    class NullableInternalType(
        name: InternalTypes,
        pkg: String,
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol>
    ) : InternalLike(name, pkg, isPrivate, protocols)

    sealed class UserLike(
        name: String,
        var typeArgumentList: List<Type>,
        val fields: List<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol>
    ) : Type(name, pkg, isPrivate, protocols)

    class UserType(
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class KnownGenericType(
        val mainType: Type,
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UnknownGenericType(
        name: String,
        typeArgumentList: List<Type> = listOf(),
        fields: List<TypeField> = listOf(),
        isPrivate: Boolean = true,
        pkg: String = "common",
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    // like List::T
    // not sure if it is needed
//    class UnknownGenericType(
//        pkg: String,
//        typeName: InternalTypes = InternalTypes.Unknown,
//        isPrivate: Boolean = false,
//        protocols: MutableMap<String, Protocol> = mutableMapOf()
//    ) : InternalLike(typeName, pkg, isPrivate, protocols)


    class NullableUserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: List<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol>
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

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

fun TypeAST.toType(typeTable: Map<TypeName, Type>, fieldName: String? = null): Type {

    when (this) {
        is TypeAST.InternalType -> {
            return Resolver.defaultTypes.getOrElse(InternalTypes.valueOf(name)) {
                this.token.compileError("Can't find default type: $name")
                // TODO better inference, depend on context

            }

        }

        is TypeAST.UserType -> {
            if (name.length == 1 && name[0].isUpperCase()) {
                return Type.UnknownGenericType(name)
            }

            if (this.typeArgumentList.isNotEmpty()) {
                // need to know, what Generic name(like T), become what real type(like Int) to replace fields types from T to Int

                val type = typeTable[name] ?: this.token.compileError("Can't find user type: $name")

                if (type is Type.UserType) {
                    val letterToTypeMap = mutableMapOf<String, Type>()

                    if (this.typeArgumentList.count() != type.typeArgumentList.count()) {
                        throw Exception("Count ${this.name}'s type arguments not the same it's AST version ")
                    }
                    val typeArgs = this.typeArgumentList.mapIndexed { i, it ->
                        val rer = it.toType(typeTable)
                        letterToTypeMap[type.typeArgumentList[i].name] = rer
                        rer
                    }


                    type.typeArgumentList = typeArgs
                    // replace fields types from T to real
                    type.fields.forEachIndexed { i, field ->
                        val fieldType = letterToTypeMap[field.type.name]
                        if (fieldType != null) {
                            field.type = fieldType
                        }
                    }
                    return type
                } else {
                    this.token.compileError("Panic: type: ${this.name} with typeArgumentList cannot but be Type.UserType")
                }
            }
            return typeTable[name]
                ?: this.token.compileError("Can't find user type: $name")
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

fun TypeDeclaration.toType(pkg: String, typeTable: Map<TypeName, Type>): Type {

    val fieldsTyped = fields.map { it.toTypeField(typeTable) }

    fun getAllGenericTypesFromFields(fields2: List<TypeField>, fields: List<TypeFieldAST>): MutableList<Type> {
        val result = mutableListOf<Type>()
        fields2.forEachIndexed { i, it ->
            val type = it.type

            if (type is Type.UserLike) {
                val qwe = type.typeArgumentList.mapIndexed { i2, it2 ->
                    val field = fields[i].type
                    val typeName =
                        if (field is TypeAST.UserType) {
                            field.typeArgumentList[i2].name
                        } else {
                            throw Exception("field is not user type")
                        }
                    Type.UnknownGenericType(
                        name = typeName
                    )
                }
//                result.addAll(type.typeArgumentList)
                result.addAll(qwe)

                if (type.fields.isNotEmpty()) {
                    result.addAll(getAllGenericTypesFromFields(type.fields, fields))
                }
            }
        }
        return result
    }

    val typeFields1 = fieldsTyped.filter { it.type is Type.UnknownGenericType }.map { it.type }
    val typeFields2 = getAllGenericTypesFromFields(fieldsTyped, fields)
    val typeFields = typeFields1 + typeFields2

    val result = Type.UserType(
        name = typeName,
        typeArgumentList = typeFields,
        fields = fieldsTyped,
        isPrivate = isPrivate,
        pkg = pkg,
        protocols = mutableMapOf()
    )
    this.typeFields.addAll(typeFields.map { it.name })

    return result
}

fun MessageDeclarationUnary.toMessageData(typeTable: MutableMap<TypeName, Type>): UnaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!

    val result = UnaryMsgMetaData(
        name = this.name,
        returnType = returnType,
    )
    return result
}

fun MessageDeclarationBinary.toMessageData(typeTable: MutableMap<TypeName, Type>): BinaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!


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

    val keywordArgs = this.args.map {
        KeywordArg(
            name = it.name,
            type = it.type?.toType(typeTable)
                ?: token.compileError("Type of keyword message ${this.name}'s arg ${it.name} not registered")
        )
    }
    val result = KeywordMsgMetaData(
        name = this.name,
        argTypes = keywordArgs,
        returnType = returnType
    )
    return result
}
