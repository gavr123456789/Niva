@file:Suppress("unused")

package frontend.resolver

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.Pragma
import frontend.resolver.Type.RecursiveType.copy
import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.CYAN
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL

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
    val pragmas: MutableList<Pragma> = mutableListOf(),
    @Suppress("unused")
    val msgSends: List<MsgSend> = listOf(),
    var forGeneric: Boolean = false, // if message declarated for generic, we need to know it to resolve it
    var errors: MutableSet<Type.Union>? = null
) {
    fun addErrors(errors: MutableSet<Type.Union>) {
        val errs = this.errors
        if (errs != null) {
            errs.addAll(errors)
        } else {
            this.errors = errors
        }
    }

    fun addError(err: Type.Union) {
        val errors = errors
        if (errors != null)
            errors.add(err)
        else
            this.errors = mutableSetOf(err)
    }

    override fun toString(): String {
        return when (this) {
            is BinaryMsgMetaData -> this.toString()
            is KeywordMsgMetaData -> this.toString()
            is UnaryMsgMetaData -> this.toString()
            is BuilderMetaData -> this.toString()
        }
    }

    fun toLambda(receiverType: Type): Type.Lambda {
        val extensionArg = KeywordArg("this", receiverType)
        val createLambda = { args: MutableList<KeywordArg> ->
            Type.Lambda(
                args = args,
                returnType = returnType,
                pkg = pkg,
                extensionOfType = receiverType
            )
        }
        return when (this) {
            is UnaryMsgMetaData -> {
                createLambda(mutableListOf(extensionArg))
            }

            is KeywordMsgMetaData -> {
                val extensionArg2 = KeywordArg("this", receiverType)
                val args: MutableList<KeywordArg> = argTypes.toMutableList()
                args.addFirst(extensionArg2)
                createLambda(args)
            }

            is BuilderMetaData -> {
                val extensionArg2 = KeywordArg("this", receiverType)
                val args: MutableList<KeywordArg> = argTypes.toMutableList()
                args.addFirst(extensionArg2)
                createLambda(args)
            }


            is BinaryMsgMetaData -> {
                val arg = KeywordArg(name, argType)
                createLambda(mutableListOf(extensionArg, arg))
            }
        }

    }
}

class UnaryMsgMetaData(
    name: String,
    returnType: Type,
    pkg: String,
    pragmas: MutableList<Pragma> = mutableListOf(),
    msgSends: List<MsgSend> = listOf(),
    val isGetter: Boolean = false
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends) {
    override fun toString(): String {
        return "$name -> $returnType"
    }
}

class BinaryMsgMetaData(
    name: String,
    val argType: Type,
    returnType: Type,
    pkg: String,
    pragmas: MutableList<Pragma> = mutableListOf(),
    msgSends: List<MsgSend> = listOf()
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends) {
    override fun toString(): String {
        return "$name $argType -> $returnType"
    }
}


class KeywordMsgMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    returnType: Type,
    pkg: String,
    pragmas: MutableList<Pragma> = mutableListOf(),
    msgSends: List<MsgSend> = listOf(),
    val isSetter: Boolean = false
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends) {
    override fun toString(): String {
        val args = argTypes.joinToString(" ") { it.toString() }
        return "$args -> $returnType"
    }
}

class BuilderMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    returnType: Type,
    pkg: String,
    pragmas: MutableList<Pragma> = mutableListOf(),
    msgSends: List<MsgSend> = listOf(),
    val isSetter: Boolean = false,
    val defaultAction: CodeBlock?
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends) {
    override fun toString(): String {
        val args = argTypes.joinToString(" ") { it.toString() }
        return "builder $args -> $returnType"
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


class KeywordArgAst(
    val name: String,
    val keywordArg: Expression
)


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


fun MutableList<KeywordArg>.copy(): MutableList<KeywordArg> =
    this.map {
        val type = it.type
        KeywordArg(
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
    var isMutable: Boolean = false,
    var errors: MutableSet<Union>? = null
) {

    fun copyAnyType(): Type =
        when (this) {
            is UserLike -> {
                this.copy()
            }

            is InternalType -> {
                InternalType(
                    typeName = InternalTypes.valueOf(name),
                    pkg = pkg,
                    isPrivate = isPrivate,
                    protocols = protocols
                )
            }

            is UnresolvedType -> {
                UnresolvedType(
                    realType = realType
                )
            }

            is NullableType -> {
                NullableType(
                    realType = realType
                )
            }

            is Lambda -> TODO()
        }


    fun addError(err: Union) {
        assert(this.errors == null)
        val typeCopy = this.copyAnyType()
        val errs = typeCopy.errors

        if (errs != null) {
            errs.add(err)
        } else
            typeCopy.errors = mutableSetOf(err)
    }

    fun addErrors(errors2: MutableSet<Union>): Type {
        // создать настоящее копирование для всех типов
        // копировать текущий тип и только потом добавлять к нему ерроры
        assert(this.errors == null)

        val typeCopy = this.copyAnyType()
        val errs = typeCopy.errors

        if (errs != null) {
            errs.addAll(errors2)
        } else
            typeCopy.errors = errors2

        return typeCopy
    }

    override fun toString(): String = when (this) {
        is InternalLike -> name
        is NullableType -> "$realType?"
        is UnresolvedType -> "?unresolved type?"
        is UserLike -> {
            val toStringWithRecursiveCheck = { x: Type, currentTypeName: String, currentTypePkg: String ->
                if (x.name == currentTypeName && x.pkg == currentTypePkg) {
                    x.name
                } else {
                    x.toString()
                }
            }
            val genericParam =
                if (typeArgumentList.count() == 1) {
                    "::" + toStringWithRecursiveCheck(typeArgumentList[0], this.name, this.pkg)
                } else if (typeArgumentList.count() > 1) {
                    "(" + typeArgumentList.joinToString(", ") {
                        toStringWithRecursiveCheck(
                            it,
                            this.name,
                            this.pkg
                        )
                    } + ")"
                } else ""
            val needPkg = if (pkg != "core" && pkg != "common") "$pkg." else ""
            "$needPkg$name$genericParam"
        }

        is Lambda -> name
    }

    fun toKotlinString(needPkgName: Boolean): String = when (this) {
        is InternalLike, is UnknownGenericType -> name
        is NullableType -> "${realType.toKotlinString(needPkgName)}?"
        is UnresolvedType -> {
            throw Exception("Compiler bug, attempt to generate code for unresolved type")
        }

        is UserLike -> {
            val genericParam =
                if (typeArgumentList.isNotEmpty()) {
                    "<" + typeArgumentList.joinToString(", ") { it.toString() } + ">"
                } else ""
            val needPkg = if (needPkgName && pkg != "core") "$pkg." else ""
            "$needPkg$name$genericParam"
        }

        is Lambda -> buildString {
            val realArgs = if (extensionOfType != null) {
                // fun sas(x: ^Int.(Int) -> String) =
                val kotlinExtType = extensionOfType.toKotlinString(needPkgName)
                append(kotlinExtType, ".")

                args.drop(1).map { it.type }
            } else args.map { it.type }

            append("(")
            realArgs.forEach {
                append(it.toKotlinString(needPkgName), ",")
            }
            append(") -> ")


            append(returnType.toKotlinString(needPkgName))
        }

    }

    class TypeType(
        val name: String,
        val fields: MutableMap<String, TypeType> = mutableMapOf(),
        val genericParams: MutableList<TypeType> = mutableListOf()
    )

    // type Person name: String age: Int
    fun toTypeTypeStringRepresentation() = buildString {
//        TypeType(
//            "Person", mutableMapOf(
//                "name" to TypeType("String"),
//                "age" to TypeType("Int")
//            )
//        )
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
            is UnresolvedType -> TODO()
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
        init {
            if (realType is NullableType) {
                throw Exception("Compiler but $realType boxing into Nullable, but its nullable already")
            }
        }

        fun getTypeOrNullType(): Type {
            return realType

//            return Resolver.defaultTypes[InternalTypes.Null]!!
        }
    }

    class UnresolvedType(
        val realType: () -> Type = { TODO("Compiler bug") }
    ) : Type(
        "???",
        "???",
        false,
//        realType.name,
//        realType.pkg,
//        realType.isPrivate,
//        createNullableAnyProtocols(realType)
    )


    class Lambda(
        val args: MutableList<KeywordArg>,
        val returnType: Type,
        pkg: String = "common",
        isPrivate: Boolean = false,
        var specialFlagForLambdaWithDestruct: Boolean = false,
        val extensionOfType: Type? = null,
        var alias: String? = null
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
        var fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol>,
        var isBinding: Boolean = false
    ) : Type(name, pkg, isPrivate, protocols) {
        fun printConstructorExample() = fields.joinToString(": value") { it.name } + ": value"
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

            is EnumRootType -> EnumRootType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.copy(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                branches = this.branches.toList(),
                protocols = this.protocols.toMutableMap(),
            ).also {
                it.isBinding = this.isBinding
                it.parent = this.parent
            }

            is UnionRootType -> UnionRootType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.copy(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                branches = this.branches.toList(),
                protocols = this.protocols.toMutableMap(),
                isError = this.isError
            ).also {
                it.isBinding = this.isBinding
                it.parent = this.parent
            }

            is EnumBranchType -> TODO()
            is UnionBranchType -> UnionBranchType(
                name = this.name,
                typeArgumentList = this.typeArgumentList.toList(),
                fields = this.fields.copy(),
                isPrivate = this.isPrivate,
                pkg = this.pkg,
                root = this.root,
                protocols = this.protocols.toMutableMap(),
                isError = this.isError
            ).also {
                it.isBinding = this.isBinding
                it.parent = this.parent
            }

            is KnownGenericType -> TODO()
            is UnknownGenericType -> UnknownGenericType(this.name)
            RecursiveType -> TODO()
        }


    class UserType(
        name: String,
        typeArgumentList: List<Type> = listOf(), // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

//    class ErrorType(
//        var branches: List<ErrorType>,
//        name: String,
//        fields: MutableList<KeywordArg>,
//        isPrivate: Boolean = false,
//        pkg: String,
//        protocols: MutableMap<String, Protocol> = mutableMapOf(),
//        val isRoot: Boolean
//    ) : UserLike(name, listOf(), fields, isPrivate, pkg, protocols)


    // Union -> Error, User
    // User -> Root, Branch
    // Error -> Root, Branch
    sealed class Union(
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        val isError: Boolean
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class UnionRootType(
        var branches: List<Union>, // can be union or branch
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        isError: Boolean
    ) : Union(name, typeArgumentList, fields, isPrivate, pkg, protocols, isError) {
//        fun getBranches1(): List<Type.UserUnionBranchType> =
    }

    class UnionBranchType(
        val root: UnionRootType,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        isError: Boolean
    ) : Union(name, typeArgumentList, fields, isPrivate, pkg, protocols, isError)


    class EnumRootType(
        var branches: List<EnumBranchType>,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class EnumBranchType(
        val root: EnumRootType,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        isPrivate: Boolean = false,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)


    sealed class GenericType(
        name: String,
        typeArgumentList: List<Type>,
        pkg: String,
        fields: MutableList<KeywordArg> = mutableListOf(),
        isPrivate: Boolean = false,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : UserLike(name, typeArgumentList, fields, isPrivate, pkg, protocols)

    class KnownGenericType(
        name: String,
        typeArgumentList: List<Type>,
        pkg: String = "common",
    ) : GenericType(name, typeArgumentList, pkg)

    class UnknownGenericType(
        name: String,
        typeArgumentList: List<Type> = listOf(),
        pkg: String = "common",
    ) : GenericType(name, typeArgumentList, pkg) {
        override fun toString(): String {
            return name
        }
    }

    object RecursiveType : UserLike("RecursiveType", listOf(), mutableListOf(), false, "common", mutableMapOf())


}

data class Protocol(
    val name: String,
    val unaryMsgs: MutableMap<String, UnaryMsgMetaData> = mutableMapOf(),
    val binaryMsgs: MutableMap<String, BinaryMsgMetaData> = mutableMapOf(),
    val keywordMsgs: MutableMap<String, KeywordMsgMetaData> = mutableMapOf(),
    val staticMsgs: MutableMap<String, MessageMetadata> = mutableMapOf(),
//    val builders: MutableMap<String, BuilderMetaData> = mutableMapOf(),
)

class Package(
    val packageName: String,
    val declarations: MutableList<Declaration> = mutableListOf(),
    val types: MutableMap<String, Type> = mutableMapOf(),
    val builders: MutableMap<String, BuilderMetaData> = mutableMapOf(),

//    val usingPackages: MutableList<Package> = mutableListOf(),
    // generates as import x.y.*
    val imports: MutableSet<String> = mutableSetOf(),
    // generates as import x.y
    val concreteImports: MutableSet<String> = mutableSetOf(),
    val isBinding: Boolean = false,
    val comment: String = "",

    // imports that need to be added if this pkg used(need for bindings)
    val neededImports: MutableSet<String> = mutableSetOf(),
    val plugins: MutableSet<String> = mutableSetOf(),

    ) {
    override fun toString(): String {
        return packageName
    }

    fun addBuilder(b: BuilderMetaData, token: Token) {
        // check if builder with such name already exists
        if (builders.contains(b.name)) {
            token.compileError("Sorry, but u can't register more than one builder with the same name, and ${b.name} already exists in ${this.packageName} package")
        } else {
            builders[b.name] = b
        }
    }
}

class Project(
    val name: String,
    val packages: MutableMap<String, Package> = mutableMapOf(),
    val usingProjects: MutableList<Project> = mutableListOf()
)

// if parentType not null, then we are resolving its field
fun TypeAST.toType(
    typeDB: TypeDB,
    typeTable: Map<TypeName, Type>,
    parentType: Type.UserLike? = null,
    resolvingFieldName: String? = null,
    typeDeclaration: SomeTypeDeclaration? = null
): Type {

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

                return if (!isNullable)
                    Type.UnknownGenericType(name)
                else
                    Type.NullableType(Type.UnknownGenericType(name))
            }
//            if (selfType != null && name == selfType.name) return selfType

            if (this.typeArgumentList.isNotEmpty()) {
                // need to know what Generic name(like T), become what real type(like Int) to replace fields types from T to Int


                val typeFromDb = typeTable[name] ?: this.token.compileError("Can't find user type: ${YEL}$name")
                // Type DB
                if (typeFromDb is Type.UserLike) {
                    val copy = typeFromDb.copy()
                    val letterToTypeMap = mutableMapOf<String, Type>()

                    if (this.typeArgumentList.count() != copy.typeArgumentList.count()) {
                        this.token.compileError("Type ${YEL}${this.name}${RESET} has ${WHITE}${copy.typeArgumentList.count()}${RESET} generic params, but you send only ${WHITE}${this.typeArgumentList.count()}")
                    }
                    val typeArgs = this.typeArgumentList.mapIndexed { i, it ->
                        val typeOfArg = it.toType(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration)
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
            if (type == null) {

                if (parentType == null || resolvingFieldName == null || typeDeclaration == null) {
                    // we are not resolving type fields of different type
                    this.token.compileError("Can't find user type: ${YEL}$name")
                }

                typeDB.unresolvedTypes[name] =
                    FieldNameAndParent(resolvingFieldName, parentType, typeDeclaration = typeDeclaration)
                return Type.UnresolvedType()
//                TODO()
            }

            val type2 = if (mutable) {
                (type as Type.UserLike).copy().also { it.isMutable = true }
            } else type

            return replaceToNullableIfNeeded(type2)
        }

        is TypeAST.Lambda -> {

            val extensionOfType = if (this.extensionOfType != null) {
                extensionOfType.toType(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration)
            } else null


            val args = if (extensionOfType != null) {
                inputTypesList.drop(1).map {
                    KeywordArg(
                        type = it.toType(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration),
                        name = it.name
                    )
                }.toMutableList().also {
                    it.addFirst(
                        KeywordArg(type = extensionOfType, name = "this")
                    )
                }
            } else inputTypesList.map {
                KeywordArg(
                    type = it.toType(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration), name = it.name
                )
            }.toMutableList()

            val returnType =
                this.returnType.toType(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration)

            if (returnType is Type.UnresolvedType) {
                val q = typeDB.unresolvedTypes[this.returnType.name]!!
                q.ast = this
//                TODO("add the same for arguments")
            }

            val lambdaType = Type.Lambda(
                args = args,
                extensionOfType = extensionOfType,
                returnType = returnType,
            )

            return replaceToNullableIfNeeded(lambdaType)
        }


    }

}

//fun TypeFieldAST.tryToTypeField(
//    typeDB: TypeDB,
//    typeTable: Map<TypeName, Type>,
//    recursiveType: Type.UserLike
//): KeywordArg? {
//    val r = typeAST!!
//    val w = try {
//        r.toType(typeDB, typeTable, recursiveType)
//    } catch (_: Throwable) {
//        return null
//    }
//    val result = KeywordArg(
//        name = name,
//        type = w
//    )
//    return result
//}

fun TypeFieldAST.toTypeField(
    typeDB: TypeDB,
    typeTable: Map<TypeName, Type>,
    parentType: Type.UserLike,
    typeDeclaration: SomeTypeDeclaration
): KeywordArg {
    val result = KeywordArg(
        name = name,
        type = typeAST!!.toType(
            typeDB,
            typeTable,
            parentType,
            resolvingFieldName = name,
            typeDeclaration = typeDeclaration
        )
    )
    return result
}

fun SomeTypeDeclaration.toType(
    pkg: String,
    typeTable: Map<TypeName, Type>,
    typeDB: TypeDB,
    isUnion: Boolean = false,
    isEnum: Boolean = false,
    isError: Boolean = false,
    unionRootType: Type.UnionRootType? = null, // if not null, then this is branch
    enumRootType: Type.EnumRootType? = null,
): Type.UserLike {

//    listOf("").find {  }
    val result = if (isUnion)
        Type.UnionRootType(
            branches = listOf(),
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf(),
            isError = isError
        )
    else if (isEnum)
        Type.EnumRootType(
            branches = listOf(),
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )
    else if (enumRootType != null) {
        Type.EnumBranchType(
            root = enumRootType,
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        ).also { it.parent = unionRootType }
    } else if (unionRootType != null) {
        Type.UnionBranchType(
            root = unionRootType,
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf(),
            isError = isError,

            ).also {
            it.parent = unionRootType
        }
    } else
        Type.UserType(
            name = typeName,
            typeArgumentList = listOf(),
            fields = mutableListOf(),
            isPrivate = isPrivate,
            pkg = pkg,
            protocols = mutableMapOf()
        )


    val fieldsTyped = mutableListOf<KeywordArg>()
    val unresolvedSelfTypeFields = mutableListOf<KeywordArg>()

    fields.forEach {
        val astType = it.typeAST
        if (astType != null && astType.name == typeName) {
            // this is recursive type
            val fieldType = KeywordArg(
                name = it.name,
                type = if (!astType.isNullable) Type.RecursiveType else Type.NullableType(Type.RecursiveType)
            )
            fieldsTyped.add(fieldType)
            unresolvedSelfTypeFields.add(fieldType)


        } else {
            // Если тип не найдет, то добавляем текущий тип в мапу нерезолвнутых типов к их нерезолвнутым полям
            // в конце пробуем их резолвнуть, и вот только тогда если не получается то пишем об ошибке

//            val wf = it.tryToTypeField(typeDB, typeTable, recursiveType = result)
//            if (wf == null) {
//
//                TODO("it = $it")
//            }
            fieldsTyped.add(it.toTypeField(typeDB, typeTable, parentType = result, typeDeclaration = this))
        }
    }

    fun getAllGenericTypesFromFields(
        fields2: List<KeywordArg>,
        fields: List<TypeFieldAST>,
        setOfCheckedFields: MutableSet<Type>
    ): MutableList<Type.UserLike> {
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

                if (type !in setOfCheckedFields && type.fields.isNotEmpty()) {
                    setOfCheckedFields.add(type)
                    result2.addAll(getAllGenericTypesFromFields(type.fields, fields, setOfCheckedFields))
                }
            }

        }
        return result2
    }

    val genericsDeclarated = fieldsTyped.asSequence()
        .filter { it.type is Type.UnknownGenericType }
        .map { it.type }
        .distinctBy { it.name }
    val genericsFromFieldsTypes = getAllGenericTypesFromFields(fieldsTyped, fields, mutableSetOf())


    val genericTypeFields = (genericsDeclarated + genericsFromFieldsTypes).toMutableList()


    unresolvedSelfTypeFields.forEach {
        it.type = if ((it.type !is Type.NullableType)) result else Type.NullableType(result)
    }

    this.genericFields.addAll(genericTypeFields.map { it.name })

    // add already declared generic fields(via `type Sas::T` syntax)
    this.genericFields.forEach {
        if (it.isGeneric() && genericTypeFields.find { x -> x.name == it } == null) {
            genericTypeFields.add(Type.UnknownGenericType(it))
            // add to recursive ast types of fields generic params

        }
    }
    // add generics params to astTypes of fields
    fields.asSequence()
        .filterIsInstance<TypeAST.UserType>() // get recursive
        .filter { it.names.first() == this.typeName }
        .forEach { field ->
            field.typeArgumentList.addAll(
                genericTypeFields.map {
                    TypeAST.UserType(
                        name = it.name,
                        token = field.token,
                    )
                })
        }


    // fill fields with real types
    fields.forEachIndexed { i, it ->
        val type = fieldsTyped[i].type
        it.type = type

        val unpackedNull = type.unpackNull()
        if (unpackedNull is Type.UserLike && unpackedNull.typeArgumentList.isNotEmpty()) {
            genericTypeFields.addAll(unpackedNull.typeArgumentList.filter { it.name.isGeneric() })
        }
    }

    result.typeArgumentList = genericTypeFields.distinctBy { it.name }
    result.fields = fieldsTyped

    this.receiver = result
    return result
}


fun MessageDeclaration.toAnyMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isGetter: Boolean = false,
    isSetter: Boolean = false,// only for bindings of fields, we cant add new field, it will break the constructor, so we add msgs
    resolver: Resolver
): MessageMetadata {
    return when (this) {
        is MessageDeclarationKeyword -> toMessageData(typeDB, typeTable, pkg, isSetter)
        is MessageDeclarationUnary -> toMessageData(typeDB, typeTable, pkg, isGetter)
        is MessageDeclarationBinary -> toMessageData(typeDB, typeTable, pkg)
        is ConstructorDeclaration -> {
            val constructorForType = forType
            if (constructorForType is Type.UserLike && constructorForType.isBinding && body.isNotEmpty()) {
                this.token.compileError("Can't create custom constructors for binding, that require companion object in Kotlin(wait for static extension feature)")
            }
            if (this.returnTypeAST == null) {
                this.returnType = forType
            }
            resolver.addStaticDeclaration(this)
        }

        is StaticBuilderDeclaration -> {
            toMessageData(typeDB, typeTable, pkg)
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
        pragmas = pragmas,
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


    val argType =
        this.arg.typeAST?.toType(typeDB, typeTable) ?: this.token.compileError("Type for binary msg not specified")

    val result = BinaryMsgMetaData(
        name = this.name,
        argType = argType,
        returnType = returnType,
        pkg = pkg.packageName,
        pragmas = pragmas
    )
    return result
}

fun StaticBuilderDeclaration.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
): BuilderMetaData {
    val x = this.msgDeclaration.toMessageData(typeDB, typeTable, pkg)

    return BuilderMetaData(
        name = x.name,
        argTypes = x.argTypes,
        returnType = x.returnType,
        pkg = x.pkg,
        pragmas = x.pragmas,
        msgSends = x.msgSends,
        defaultAction = defaultAction
    )
}

fun MessageDeclarationKeyword.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isSetter: Boolean = false
): KeywordMsgMetaData {
    val returnType = this.returnType ?: this.returnTypeAST?.toType(typeDB, typeTable)
    ?: Resolver.defaultTypes[InternalTypes.Unit]!!

    this.returnType = returnType
    val keywordArgs = this.args.map { kwDeclArg ->
        val type = kwDeclArg.typeAST?.toType(typeDB, typeTable)
            ?: token.compileError("Type of keyword message ${CYAN}${this.name}${RED}'s arg ${WHITE}${kwDeclArg.name}${RED} not registered")

        // lambda can contain generic params, and we need add them to typeArgs
        if (type is Type.Lambda) {
            this.typeArgs.addAll(
                type.args.asSequence().map { it.type }.filterIsInstance<Type.UnknownGenericType>().map { it.name })
            if (type.returnType is Type.UnknownGenericType) this.typeArgs.add(type.returnType.name)
        }

        KeywordArg(
            name = kwDeclArg.name,
            type = type
        )
    }


    val result = KeywordMsgMetaData(
        name = this.name,
        argTypes = keywordArgs,
        returnType = returnType,
        pragmas = pragmas,
        pkg = pkg.packageName,
        isSetter = isSetter
    )
    return result
}
