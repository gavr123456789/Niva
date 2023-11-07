@file:Suppress("unused")

package frontend.typer

import frontend.meta.compileError
import frontend.parser.parsing.CodeAttribute
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
    val pkg: String,
    val pragmas: MutableList<CodeAttribute> = mutableListOf(),
    @Suppress("unused")
    val msgSends: List<MsgSend> = listOf()
)

class UnaryMsgMetaData(
    name: String,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf(),
    val isGetter: Boolean = false
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends)

class BinaryMsgMetaData(
    name: String,
    val argType: Type,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends)

class KeywordArg(
    val name: String,
    val type: Type,
)

class KeywordMsgMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends)

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

fun Type.isDescendantOf(type: Type): Boolean {
    if (this !is Type.UserLike || type !is Type.UserLike) {
        return false
    }
    var parent: Type? = this.parent
    while (parent != null) {
        if (compare2Types(type, parent)) {
            return true
        }
        parent = parent.parent
    }
    return false
}

sealed class Type(
    val name: String, // when generic, we need to reassign it to AST's Type field, instead of type's typeField
    val pkg: String,
    val isPrivate: Boolean,
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
    var parent: Type? = null, // = Resolver.defaultBasicTypes[InternalTypes.Any] ?:
    var beforeGenericResolvedName: String? = null,
//    var bind: Boolean = false
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
        var fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol>,
        var isBinding: Boolean = false
    ) : Type(name, pkg, isPrivate, protocols)

    class UserType(
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UserUnionRootType(
        var branches: List<UserUnionBranchType>,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UserUnionBranchType(
        val root: UserUnionRootType,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class KnownGenericType(
//        val mainType: Type,
        name: String,
        typeArgumentList: List<Type>,
        pkg: String,
        fields: MutableList<TypeField> = mutableListOf(),
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UnknownGenericType(
        name: String,
        typeArgumentList: List<Type> = listOf(),
        fields: MutableList<TypeField> = mutableListOf(),
        isPrivate: Boolean = true,
        pkg: String = "common",
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    object RecursiveType : UserLike("RecursiveType", listOf(), mutableListOf(), false, "common", mutableMapOf())


    class NullableUserType(
        name: String,
        typeArgumentList: List<Type>,
        fields: MutableList<TypeField>,
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
    val packageName: String,
    val declarations: MutableList<Declaration> = mutableListOf(),
    val types: MutableMap<String, Type> = mutableMapOf(),
//    val usingPackages: MutableList<Package> = mutableListOf(),
    val currentImports: MutableSet<String> = mutableSetOf(),
    val isBinding: Boolean = false
) {
    override fun toString(): String {
        return packageName
    }
}

class Project(
    val name: String,
    val packages: MutableMap<String, Package> = mutableMapOf(),
    val usingProjects: MutableList<Project> = mutableListOf()
)

fun TypeAST.toType(typeTable: Map<TypeName, Type>, selfType: Type.UserLike? = null): Type {

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
            if (selfType != null && name == selfType.name) return selfType

            if (this.typeArgumentList.isNotEmpty()) {
                // need to know, what Generic name(like T), become what real type(like Int) to replace fields types from T to Int


                val type = typeTable[name] ?: this.token.compileError("Can't find user type: $name")

                if (type is Type.UserLike) {
                    val letterToTypeMap = mutableMapOf<String, Type>()

                    if (this.typeArgumentList.count() != type.typeArgumentList.count()) {
                        throw Exception("Count ${this.name}'s type arguments not the same it's AST version ")
                    }
                    val typeArgs = this.typeArgumentList.mapIndexed { i, it ->
                        val rer = it.toType(typeTable, selfType)
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
                        type = it.toType(typeTable, selfType),
                        name = it.name
                    )
                }.toMutableList(),
                returnType = this.returnType.toType(typeTable, selfType)
            )
            return lambdaType

        }

        is TypeAST.ResursiveType -> return Type.RecursiveType

    }

}

fun TypeFieldAST.toTypeField(typeTable: Map<TypeName, Type>, selfType: Type.UserLike): TypeField {
    val result = TypeField(
        name = name,
        type = type!!.toType(typeTable, selfType)
    )
    return result
}

fun SomeTypeDeclaration.toType(
    pkg: String,
    typeTable: Map<TypeName, Type>,
    isUnion: Boolean = false,
    root: Type.UserUnionRootType? = null
): Type.UserLike {

    val result = if (isUnion)
        Type.UserUnionRootType(
            branches = listOf(),
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )
    else if (root != null)
        Type.UserUnionBranchType(
            root = root,
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )
    else
        Type.UserType(
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )


    val fieldsTyped = mutableListOf<TypeField>()
    val unresolvedSelfTypeFields = mutableListOf<TypeField>()
    val unresolvedSelfTypeGenericFields = mutableMapOf<TypeFieldAST, TypeField>()

//    val createTypeAlreadyWithNoFields // than fill it with them

    fields.forEach {
        val astType = it.type
        if (astType != null && astType.name == typeName) {
            // this is recursive type
            val fieldType = TypeField(
                name = it.name,
                type = Type.RecursiveType
            )
            fieldsTyped.add(fieldType)
            unresolvedSelfTypeFields.add(fieldType)

        }
//        else if (astType != null && astType is TypeAST.UserType && astType.typeArgumentList.find { it.name == typeName } != null) {
//            println()
//            // this field's type contains the type itself
//            val fieldType = TypeField(
//                name = it.name,
//                type = Type.RecursiveType
//            )
//            fieldsTyped.add(fieldType)
//            unresolvedSelfTypeGenericFields[it] = fieldType
//        }
        else fieldsTyped.add(it.toTypeField(typeTable, selfType = result))

//        fieldsTyped.add(it.toTypeField(typeTable))
    }

    fun getAllGenericTypesFromFields(fields2: List<TypeField>, fields: List<TypeFieldAST>): MutableList<Type.UserLike> {
        val result = mutableListOf<Type.UserLike>()
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

                result.addAll(qwe)

                if (type.fields.isNotEmpty()) {
                    result.addAll(getAllGenericTypesFromFields(type.fields, fields))
                }
            }
        }
        return result
    }

    val typeFields1 = fieldsTyped.filter { it.type is Type.UnknownGenericType }.map { it.type }
    val typeFieldsGeneric = getAllGenericTypesFromFields(fieldsTyped, fields)
    val typeFields = typeFields1 + typeFieldsGeneric


    unresolvedSelfTypeFields.forEach {
        it.type = result
    }
//    unresolvedSelfTypeGenericFields.forEach { (astField, field) ->
//        // сопоставить с оригиналом, нет, это просто должна быть хешмапа оригиналов Аст к реальным филдам
//        // найти на каком уровне собственно содержится рекурсивность
//        println(astField)
//        println(field)
//        val astType = astField.type
//
//        if (astType is TypeAST.UserType) {
//            var typeArgList = astType.typeArgumentList
//            val q = typeArgList[0]
//            if (q.name == typeName) {
//                println()
//            }
//
//        }
//
//    }
    result.typeArgumentList = typeFields
    result.fields = fieldsTyped

    this.genericFields.addAll(typeFields.map { it.name })

    return result
}


fun MessageDeclarationUnary.toMessageData(
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isGetter: Boolean = false
): UnaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!

    val result = UnaryMsgMetaData(
        name = this.name,
        returnType = returnType,
        pkg = pkg.packageName,
        codeAttributes = pragmas,
        isGetter = isGetter
    )
    return result
}

fun MessageDeclarationBinary.toMessageData(typeTable: MutableMap<TypeName, Type>, pkg: Package): BinaryMsgMetaData {
    val returnType = this.returnType?.toType(typeTable)
        ?: Resolver.defaultTypes[InternalTypes.Unit]!!


    val argType = this.forTypeAst.toType(typeTable)

    val result = BinaryMsgMetaData(
        name = this.name,
        argType = argType,
        returnType = returnType,
        pkg = pkg.packageName,
        codeAttributes = pragmas
    )
    return result
}

fun MessageDeclarationKeyword.toMessageData(typeTable: MutableMap<TypeName, Type>, pkg: Package): KeywordMsgMetaData {
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
        returnType = returnType,
        codeAttributes = pragmas,
        pkg = pkg.packageName
    )
    return result
}
