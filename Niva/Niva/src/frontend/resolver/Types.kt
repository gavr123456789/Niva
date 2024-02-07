@file:Suppress("unused")

package frontend.resolver

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.parsing.CodeAttribute
import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.*
import frontend.resolver.Type.RecursiveType.copy
import main.*

import main.utils.isGeneric

data class MsgSend(
    val pkg: String,
    val selector: String,
    val project: String,
    val type: MessageDeclarationType
)

sealed class MessageMetadata(
    val name: String,
    var returnType: Type, // need to change in single expression case
    val pkg: String,
    val pragmas: MutableList<CodeAttribute> = mutableListOf(),
    @Suppress("unused")
    val msgSends: List<MsgSend> = listOf(),
    var forGeneric: Boolean = false // if message declarated for generic, we need to know it to resolve it
) {
    override fun toString(): String {
        return when (this) {
            is BinaryMsgMetaData -> this.toString()
            is KeywordMsgMetaData -> this.toString()
            is UnaryMsgMetaData -> this.toString()
        }
    }
}

class UnaryMsgMetaData(
    name: String,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf(),
    val isGetter: Boolean = false
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends) {
    override fun toString(): String {
        return "$name -> $returnType"
    }
}

class BinaryMsgMetaData(
    name: String,
    val argType: Type,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends) {
    override fun toString(): String {
        return "$name $argType -> $returnType"
    }
}


class KeywordMsgMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    returnType: Type,
    pkg: String,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, pkg, codeAttributes, msgSends) {
    override fun toString(): String {
        val args = argTypes.joinToString(" ") { it.toString() }
        return "$args -> $returnType"
    }
}

//class ConstructorMsgMetaData(
//    name: String,
//    returnType: Type,
//    msgSends: List<MsgSend> = listOf()
//) : MessageMetadata(name, returnType, msgSends)

sealed class FieldWithType(
    val name: String,
    var type: Type,
) {
    override fun toString(): String {
        return "$name: $type"
    }
}

class KeywordArg(
    name: String,
    type: Type,
) : FieldWithType(name, type)

class TypeField(
    name: String,
    type: Type //when generic, we need to reassign it to real type
) : FieldWithType(name, type)


class FieldWithValue(
    val name: String,
    var value: Expression
) {
    override fun toString(): String {
        return "$name: $value"
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


fun MutableList<TypeField>.copy(): MutableList<TypeField> =
    this.map {
        val type = it.type
        TypeField(
            name = it.name,
            type = if (type is Type.UserLike) type.copy() else type
        )
    }.toMutableList()


fun Type.unpackNull(): Type =
    if (this is Type.NullableType) {
        realType
    } else
        this

// when generic, we need to reassign it to AST's Type field, instead of type's typeField

sealed class Type(
    val name: String,
    val pkg: String,
    val isPrivate: Boolean,
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
    var parent: Type? = null,
    var beforeGenericResolvedName: String? = null,
) {
    override fun toString(): String =
        when (this) {
            is InternalLike -> name
            is NullableType -> "$realType?"
            is UserLike -> {
                val genericParam =
                    if (typeArgumentList.count() == 1) "::" + typeArgumentList[0].toString() else if (typeArgumentList.count() > 1) {
                        "(" + typeArgumentList.joinToString(", ") { it.toString() } + ")"
                    } else ""
                val needPkg = if (pkg != "core") "$pkg." else ""
                "$needPkg$name$genericParam"
            }

            else -> "$pkg.$name"

        }

    class TypeType(
        val name: String,
        val fields: MutableMap<String, TypeType> = mutableMapOf(),
        val genericParams: MutableList<TypeType> = mutableListOf()
    )

    // type Person name: String age: Int
    fun toTypeTypeStringRepresentation() = buildString {
        TypeType(
            "Person", mutableMapOf(
                "name" to TypeType("String"),
                "age" to TypeType("Int")
            )
        )
        append("TypeType(")
        when (this@Type) {
            is UserLike -> {
                append("\n")
            }
            is InternalType -> {
                // internal has only name
                append("")
            }

            is Lambda -> TODO()
            is NullableType -> TODO()
            RecursiveType -> TODO()
        }

        append(")")


    }

    class NullableType(
        val realType: Type
    ) : Type(
        realType.name,
        realType.pkg,
        realType.isPrivate,
        createNullableAnyProtocols(realType)
    ) {
        fun getTypeOrNullType(): Type {
            return realType

//            return Resolver.defaultTypes[InternalTypes.Null]!!
        }
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


    sealed class UserLike(
        name: String,
        var typeArgumentList: List<Type>,
        var fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol>,
        var isBinding: Boolean = false
    ) : Type(name, pkg, isPrivate, protocols) {
        fun printConstructor() = fields.joinToString(": value") { it.name } + ": value"
    }

    fun UserLike.copy(): UserLike =
        when (this) {
            is UserType -> UserType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.map { if (it is UserLike) it.copy() else it },
                fields = this.fields.copy(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                protocols = this.protocols.toMutableMap(),
            ).also { it.isBinding = this.isBinding }

            is UserEnumRootType -> UserEnumRootType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.toMutableList(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                branches = this.branches.toList(),
                protocols = this.protocols.toMutableMap(),
            ).also { it.isBinding = this.isBinding }

            is UserUnionRootType -> UserUnionRootType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.toMutableList(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                branches = this.branches.toList(),
                protocols = this.protocols.toMutableMap(),
            ).also { it.isBinding = this.isBinding }

            is UserEnumBranchType -> TODO()
            is UserUnionBranchType -> UserUnionBranchType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.toMutableList(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                root = this.root,
                protocols = this.protocols.toMutableMap(),
            )

            is KnownGenericType -> TODO()
            is UnknownGenericType -> UnknownGenericType(this.name)
            RecursiveType -> TODO()
        }


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


    class UserEnumRootType(
        var branches: List<UserEnumBranchType>,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UserEnumBranchType(
        val root: UserEnumRootType,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<TypeField>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)


    class KnownGenericType(
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
    // generates as import x.y.*
    val imports: MutableSet<String> = mutableSetOf(),
    // generates as import x.y
    val concreteImports: MutableSet<String> = mutableSetOf(),
    val isBinding: Boolean = false,
    val comment: String = "",

    // imports that need to be added if this pkg used(need for bindings)
    val neededImports: MutableSet<String> = mutableSetOf(),
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

fun TypeAST.toType(typeDB: TypeDB, typeTable: Map<TypeName, Type>, selfType: Type.UserLike? = null): Type {

    val replaceToNullableIfNeeded = { type: Type ->
        val isNullable = token.kind == TokenType.NullableIdentifier || token.kind == TokenType.Null

        if (isNullable) {
            Type.NullableType(realType = type)
        } else {
            type
        }
    }

    when (this) {
        is TypeAST.InternalType -> {
            val type = Resolver.defaultTypes.getOrElse(InternalTypes.valueOf(name)) {
                this.token.compileError("Can't find default type: ${YEL}$name")
            }

            return replaceToNullableIfNeeded(type)
        }


        is TypeAST.UserType -> {
            if (name.isGeneric()) {
                return Type.UnknownGenericType(name)
            }
            if (selfType != null && name == selfType.name) return selfType

            if (this.typeArgumentList.isNotEmpty()) {
                // need to know what Generic name(like T), become what real type(like Int) to replace fields types from T to Int


                val typeFromDb = typeTable[name] ?: this.token.compileError("Can't find user type: ${YEL}$name")
                // Type DB
                if (typeFromDb is Type.UserLike) {
                    val copy = typeFromDb.copy()
                    val letterToTypeMap = mutableMapOf<String, Type>()

                    if (this.typeArgumentList.count() != copy.typeArgumentList.count()) {
                        this.token.compileError("Type $YEL${this.name}$RESET has $WHITE${copy.typeArgumentList.count()}$RESET generic params, but you send only $WHITE${this.typeArgumentList.count()}")
                    }
                    val typeArgs = this.typeArgumentList.mapIndexed { i, it ->
                        val typeOfArg = it.toType(typeDB, typeTable, selfType)
                        letterToTypeMap[copy.typeArgumentList[i].name] = typeOfArg
                        typeOfArg
                    }


                    copy.typeArgumentList = typeArgs
                    // replace fields types from T to real
                    copy.fields.forEachIndexed { i, field ->
                        val fieldType = letterToTypeMap[field.type.name]
                        if (fieldType != null) {
                            field.type = fieldType
                        }
                    }
                    return copy
                } else {
                    this.token.compileError("Panic: type: ${YEL}${this.name}${RED} with typeArgumentList cannot but be Type.UserType")
                }
            }
            val type = typeTable[name]
                ?: this.token.compileError("Can't find user type: ${YEL}$name")

            return replaceToNullableIfNeeded(type)
        }

        is TypeAST.Lambda -> {
            val lambdaType = Type.Lambda(
                args = inputTypesList.map {
                    TypeField(
                        type = it.toType(typeDB, typeTable, selfType),
                        name = it.name
                    )
                }.toMutableList(),
                returnType = this.returnType.toType(typeDB, typeTable, selfType),
            )

            return replaceToNullableIfNeeded(lambdaType)
        }


    }

}

fun TypeFieldAST.toTypeField(typeDB: TypeDB, typeTable: Map<TypeName, Type>, selfType: Type.UserLike): TypeField {
    val result = TypeField(
        name = name,
        type = type!!.toType(typeDB, typeTable, selfType)
    )
    return result
}

fun SomeTypeDeclaration.toType(
    pkg: String,
    typeTable: Map<TypeName, Type>,
    typeDB: TypeDB,
    isUnion: Boolean = false,
    isEnum: Boolean = false,
    unionRootType: Type.UserUnionRootType? = null, // if not null, then this is branch
    enumRootType: Type.UserEnumRootType? = null,
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
    else if (isEnum)
        Type.UserEnumRootType(
            branches = listOf(),
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )
    else if (enumRootType != null) {
        Type.UserEnumBranchType(
            root = enumRootType,
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )
    } else if (unionRootType != null)
        Type.UserUnionBranchType(
            root = unionRootType,
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

//    val createTypeAlreadyWithNoFields // than fill it with them

    fields.forEach {
        val astType = it.type
        if (astType != null && astType.name == typeName) {
            // this is recursive type
            val fieldType = TypeField(
                name = it.name,
                type = if (!astType.isNullable) Type.RecursiveType else Type.NullableType(Type.RecursiveType)
            )
            fieldsTyped.add(fieldType)
            unresolvedSelfTypeFields.add(fieldType)

        } else fieldsTyped.add(it.toTypeField(typeDB, typeTable, selfType = result))
    }

    fun getAllGenericTypesFromFields(fields2: List<TypeField>, fields: List<TypeFieldAST>): MutableList<Type.UserLike> {
        val result2 = mutableListOf<Type.UserLike>()
        fields2.forEachIndexed { i, it ->
            val type = it.type

            if (type is Type.UserLike) {
                val unknownGenericTypes = mutableListOf<Type.UserLike>()
                type.typeArgumentList.forEach {
                    if (it.name.isGeneric()) {
                        unknownGenericTypes.add(Type.UnknownGenericType(name = it.name))
                    }
                }


                result2.addAll(unknownGenericTypes)

                if (type.fields.isNotEmpty()) {
                    result2.addAll(getAllGenericTypesFromFields(type.fields, fields))
                }
            }
        }
        return result2
    }

    val typeFields1 = fieldsTyped.asSequence()
        .filter { it.type is Type.UnknownGenericType }
        .map { it.type }
        .distinctBy { it.name }
    val typeFieldsGeneric = getAllGenericTypesFromFields(fieldsTyped, fields)


    val genericTypeFields = (typeFields1 + typeFieldsGeneric).toMutableList()


    unresolvedSelfTypeFields.forEach {
        it.type = if ((it.type !is Type.NullableType)) result else Type.NullableType(result)
    }

    this.genericFields.addAll(genericTypeFields.map { it.name })

    // add already declared generic fields(via `type Sas::T` syntax)
    this.genericFields.forEach {
        if (it.isGeneric() && genericTypeFields.find { x -> x.name == it } == null) {
            genericTypeFields.add(Type.UnknownGenericType(it))
        }
    }

    result.typeArgumentList = genericTypeFields
    result.fields = fieldsTyped

    return result
}


fun MessageDeclaration.toAnyMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isGetter: Boolean = false,
    resolver: Resolver
): MessageMetadata {
    return when (this) {
        is MessageDeclarationUnary -> toMessageData(typeDB, typeTable, pkg, isGetter)
        is MessageDeclarationKeyword -> toMessageData(typeDB, typeTable, pkg)
        is MessageDeclarationBinary -> toMessageData(typeDB, typeTable, pkg)
        is ConstructorDeclaration -> {
            if (this.returnTypeAST == null) {
                this.returnType = forType
            }
            resolver.addStaticDeclaration(this)
        }
    }

}

fun MessageDeclarationUnary.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isGetter: Boolean = false
): UnaryMsgMetaData {
    val returnType = this.returnType ?: this.returnTypeAST?.toType(typeDB, typeTable)
    ?: Resolver.defaultTypes[InternalTypes.Unit]!!
    this.returnType = returnType

    val result = UnaryMsgMetaData(
        name = this.name,
        returnType = returnType,
        pkg = pkg.packageName,
        codeAttributes = pragmas,
        isGetter = isGetter
    )
    return result
}

fun MessageDeclarationBinary.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package
): BinaryMsgMetaData {
    val returnType = this.returnType ?: this.returnTypeAST?.toType(typeDB, typeTable)
    ?: Resolver.defaultTypes[InternalTypes.Unit]!!
    this.returnType = returnType


    val argType = this.forTypeAst.toType(typeDB, typeTable)

    val result = BinaryMsgMetaData(
        name = this.name,
        argType = argType,
        returnType = returnType,
        pkg = pkg.packageName,
        codeAttributes = pragmas
    )
    return result
}

fun MessageDeclarationKeyword.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package
): KeywordMsgMetaData {
    val returnType = this.returnType ?: this.returnTypeAST?.toType(typeDB, typeTable)
    ?: Resolver.defaultTypes[InternalTypes.Unit]!!

    this.returnType = returnType
    val keywordArgs = this.args.map {
        KeywordArg(
            name = it.name,
            type = it.type?.toType(typeDB, typeTable)
                ?: token.compileError("Type of keyword message ${CYAN}${this.name}${RED}'s arg ${WHITE}${it.name}${RED} not registered")
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
