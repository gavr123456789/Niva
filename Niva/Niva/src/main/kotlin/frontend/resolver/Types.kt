@file:Suppress("unused")

package frontend.resolver

import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.Pragma
import frontend.parser.types.ast.SingleWordPragma
import main.codogen.Pragmas

import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.typer.replaceCollectionWithMutable
import main.utils.*


sealed class MessageMetadata(
    val name: String,
    var returnType: Type, // need to change in single expression case
    val pkg: String,
    val pragmas: MutableList<Pragma> = mutableListOf(),
    val msgSends: MutableList<Message> = mutableListOf(),
    var forGeneric: Boolean = false, // if message declarated for generic, we need to know it to resolve it
    private var _errors: MutableSet<Type.Union>? = null,
    val declaration: MessageDeclaration?,
    var docComment: DocComment? = null,
    var forMutableType: Boolean = false,
    var forType: Type? = null,
) {
    val errors: Set<Type.Union>?
        get() = _errors?.toSet()

    fun clearErrors(branches: List<Type.Union>) {
        val localErrors = _errors
        if (localErrors != null) {
            localErrors.removeAll(branches.toSet())
            if (localErrors.isEmpty()) {
                _errors = null
            }
        }
    }

    fun addErrors(errors: Set<Type.Union>) {
//        assert(errors.isNotEmpty())
        val errs = this._errors
        if (errs != null) {
            errs.addAll(errors)
        } else {
            this._errors = errors.toMutableSet()
        }
    }

    fun addError(err: Type.Union) {
        val errors = _errors
        if (errors != null)
            errors.add(err)
        else
            this._errors = mutableSetOf(err)
    }

    override fun toString(): String {
        return when (this) {
            is BinaryMsgMetaData -> this.toString()
            is KeywordMsgMetaData -> this.toString()
            is UnaryMsgMetaData -> this.toString()
            is BuilderMetaData -> this.toString()
        }
    }

    fun toLambda(receiverType: Type, withReceiverAsFirstArg: Boolean): Type.Lambda {
        val extensionArg = KeywordArg("this", receiverType)
        val createLambda = { args: MutableList<KeywordArg> ->
            Type.Lambda(
                args = if (withReceiverAsFirstArg) args else mutableListOf(),
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
    msgSends: MutableList<Message> = mutableListOf(),
    val isGetter: Boolean = false,
    declaration: MessageDeclaration?,
    docComment: DocComment? = null
) : MessageMetadata(name, returnType, pkg,  pragmas, msgSends, declaration = declaration, docComment = docComment) {
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
    msgSends: MutableList<Message> = mutableListOf(),
    declaration: MessageDeclaration?,
    docComment: DocComment? = null
) : MessageMetadata(name, returnType, pkg,  pragmas, msgSends, declaration = declaration, docComment = docComment) {
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
    msgSends: MutableList<Message> = mutableListOf(),
    val isSetter: Boolean = false,
    declaration: MessageDeclaration?,
    docComment: DocComment? = null
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends, declaration = declaration, docComment = docComment) {
    override fun toString(): String {
        val args = argTypes.joinToString(" ") { it.toString() }
        return "$args -> $returnType"
    }
}

class BuilderMetaData(
    name: String,
    val argTypes: List<KeywordArg>,
    forType: Type,
    val receiverType: Type?, // Surface(receiverType) builder Card(forType) =[]
    returnType: Type,
    pkg: String,
    pragmas: MutableList<Pragma> = mutableListOf(),
    msgSends: MutableList<Message> = mutableListOf(),
    val isSetter: Boolean = false,
    val defaultAction: CodeBlock?,
    declaration: MessageDeclaration,
    docComment: DocComment? = null
) : MessageMetadata(name, returnType, pkg, pragmas, msgSends, declaration = declaration, docComment = docComment, forType = forType) {
    override fun toString(): String {
        val args = argTypes.joinToString(" ") { it.toString() }
        return "builder $args -> $returnType"
    }
}

//class ConstructorMsgMetaData(
//    name: String,
//    returnType: Type,
//    msgSends: List<MsgSend> = emptyList()
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
) : FieldWithType(name, type) {
    override fun toString(): String =
        "$name: $type"
}


class KeywordArgAst(
    val name: String,
    val keywordArg: Expression
)


//fun Type.isDescendantOf(type: Type): Boolean {
//    if (this !is Type.UserLike || type !is Type.UserLike) {
//        return false
//    }
//    var parent: Type? = this.parent
//    while (parent != null) {
//        if (compare2Types(type, parent, createFakeToken())) {
//            return true
//        }
//        parent = parent.parent
//    }
//    return false
//}


fun MutableList<KeywordArg>.copy(thisType: Type): MutableList<KeywordArg> =
    this.map {
        val itType = it.type
        KeywordArg(
            name = it.name,
            type = if (itType is Type.UserLike)
                if (itType.typeArgumentList.contains(thisType))
                    itType
                else
                    itType.copy()
            else
                itType
        )
    }.toMutableList()


fun Type.unpackNull(): Type =
    if (this is Type.NullableType) {
        realType
    } else
        this

// when generic, we need to reassign it to AST's Type field, instead of type's typeField
fun generateGenerics(x: Type, sb: StringBuilder): String {
    val isNullable = if (x is Type.NullableType) "?" else ""
    val isMut = if (x.isMutable) "mut " else ""
    val toStringWithRecursiveCheck = { x: Type, currentTypeName: String, currentTypePkg: String ->
//        if (x.name == currentTypeName && x.pkg == currentTypePkg) {
//            x.name
//        } else {
//            x.toString()

            isMut + x.name + isNullable
//        }
    }
    if (x is Type.UserLike) {

        val str = if (x.typeArgumentList.count() == 1) {
//        sb.append("::", toStringWithRecursiveCheck(x.typeArgumentList[0], x.name, x.pkg))
            "::" + toStringWithRecursiveCheck(x.typeArgumentList[0], x.name, x.pkg)
//                    generateGenerics(this, StringBuilder())

        } else if (x.typeArgumentList.count() > 1) {
            "(" + x.typeArgumentList.joinToString(", ") {
                toStringWithRecursiveCheck(
                    it,
                    x.name,
                    x.pkg
                )
            } + ")"
        } else ""

        sb.append(str)

        if (x.typeArgumentList.isNotEmpty()) {
            val first = x.typeArgumentList[0]
            if (first is Type.UserLike && first.typeArgumentList.isNotEmpty()) {
                generateGenerics(first, sb)
            }
        }

        return sb.toString()
    } else {
        return "::$x"// toStringWithRecursiveCheck(x.typeArgumentList[0], x.name, x.pkg)
    }
}

fun isCollection(name: String) = when (name) {
    "List", "MutableList" -> true
    "Map", "MutableMap" -> true
    "Set", "MutableSet" -> true
    else -> false
}

sealed class Type(
    val name: String,
    val pkg: String,
    val protocols: MutableMap<String, Protocol> = mutableMapOf(),
    var parent: Type? = null,
    var beforeGenericResolvedName: String? = null,
    var isMutable: Boolean = false,
    var isVarMutable: Boolean = false,
    var errors: MutableSet<Union>? = null,
    var isAlias: Boolean = false,
    var isCopy: Boolean = false
) {

    fun cloneAndChangeBeforeGeneric(newValue: String): Type {
        // we need to copy only when its internal type because bug exist only in LSP
        // because LSP does not recreate internal types
        // because they are created inside companion object
        return this.copyAnyType().also { it.beforeGenericResolvedName = newValue }
//        return if (this is InternalType)
//            this.copyAnyType().also { it.beforeGenericResolvedName = newValue }
//        else
//            this.also { it.beforeGenericResolvedName = newValue }
    }
    fun copyAnyType(): Type =
        (when (this) {
            is UserLike -> {
                this.copy()
            }

            is InternalType -> {
                InternalType(
                    typeName = InternalTypes.valueOf(name),
                    pkg = pkg,
                    protocols = protocols,
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

            is Lambda -> {


                Lambda(
                    args = args.map {
                        KeywordArg(
                            name = it.name,
                            type = it.type.copyAnyType()
                        )
                    }.toMutableList(),
                    returnType = returnType.copyAnyType(),
                    pkg = pkg,
                    extensionOfType = extensionOfType,
                    specialFlagForLambdaWithDestruct = specialFlagForLambdaWithDestruct,
                    alias = alias
                )
            }
        }).also {
            it.errors = errors
            it.parent = parent
            it.isMutable = isMutable
            it.isVarMutable = isVarMutable
            it.isAlias = isAlias
        }

    fun copyAndAddErrors(errors2: Set<Union>): Type {
        // copy the current type and add errors to it

        assert(this.errors == null)

        val typeCopy = this.copyAnyType()
        val errs = typeCopy.errors

        if (errs != null) {
            errs.addAll(errors2)
        } else
            typeCopy.errors = errors2.toMutableSet()

//        val errors2 = typeCopy.errors
//        if (errors2 != null ) {
//            assert(errors2.isNotEmpty())
//        }
        return typeCopy
    }

    private fun possibleErrors(): String {
        val errors = errors
        return if (errors == null) "" else {
            "!{" + errors.joinToString(",") + "}"
        }
    }

    // needed for comparison
    fun toStringWithoutErrors(): String {
        fun removeAfterExclamation(s: String): String {
            val index = s.indexOf('!')
            return if (index != -1) s.take(index) else s
        }
        return removeAfterExclamation(this.toString().replace("mut ", ""))
    }

    override fun toString(): String = (if(isMutable) "mut " else "") + when (this) {
        is InternalType -> name + possibleErrors()
        is NullableType -> "$realType?"
        is UnresolvedType -> "?unresolved type?"
        is UserLike -> {
            val genericParam = generateGenerics(this, StringBuilder())
            val needPkg = if (pkg != "core" && pkg != "common") "$pkg." else ""
            "$needPkg$name$genericParam${possibleErrors()}"
        }

        is Lambda -> "[${args.joinToString(", ") { it.type.toString() }} -> ${returnType}]"//name
    }

    fun toKotlinString(needPkgName: Boolean): String = when (this) {
        is InternalType, is UnknownGenericType -> name
        is NullableType -> "${realType.toKotlinString(needPkgName)}?"
        is UnresolvedType -> {
            throw Exception("Compiler bug, attempt to generate code for unresolved type")
        }

        is UserLike -> {
            val genericParam =
                if (typeArgumentList.isNotEmpty()) {
                    "<" + typeArgumentList.joinToString(", ") {
                        it.toKotlinString(needPkgName)

                    } + ">"
                } else ""
            val needPkg = if (needPkgName && pkg != "core") "$pkg." else ""

            val realEmit = if (isMutable)
                replaceCollectionWithMutable(emitName)
            else
                emitName

            "$needPkg$realEmit$genericParam"
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

    /// replace List::List::Int to List::T
    fun replaceInitializedGenericToUnInitialized(resolver: Resolver, token: Token): Type {
        val x = this
        if (x !is UserLike) return x
        if (x.typeArgumentList.isEmpty()) return x

        // check that we deal with complex generic type like Box::Box::T, not Box::T
        val first = x.typeArgumentList.first()
        if (first is UnknownGenericType || first.name.isGeneric()) return x
        // now find the actual type in db
        val errorText = "Unknown type ${x.name}, its not declared anywhere"

        val typeName = x.name
        val names = listOf(typeName)
        val ident = IdentifierExpr(
            name = typeName,
            names = names,
            token = token
        )
        val unInitializedTypeFromDB = resolver.typeDB.getTypeOfIdentifierReceiver(
            typeName,
            ident,
            resolver.getCurrentImports(token),
            resolver.currentPackageName,
            names = names
        ) ?: token.compileError(errorText)

        return if (x.errors?.isNotEmpty() == true) unInitializedTypeFromDB.copyAnyType().also { it.errors = x.errors } else unInitializedTypeFromDB

    }

    class NullableType(
        val realType: Type
    ) : Type(
        realType.name,
        realType.pkg,
        createNullableAnyProtocols(realType)
    ) {
        init {
            if (realType is NullableType) {
                throw Exception("Compiler but $realType boxing into Nullable, but its nullable already")
            }
        }

        fun getTypeOrNullType(): Type =
            realType

    }

    class UnresolvedType(
        val realType: () -> Type = { TODO("Compiler bug") }
    ) : Type(
        "???",
        "???",
//        realType.name,
//        realType.pkg,
//        realType,
//        createNullableAnyProtocols(realType)
    )


    class Lambda(
        val args: MutableList<KeywordArg>,
        var returnType: Type,
        pkg: String = "common",
        var specialFlagForLambdaWithDestruct: Boolean = false,
        val extensionOfType: Type? = null,
        var alias: String? = null
    ) : Type("[${args.joinToString(", ") { it.type.toString() }} -> $returnType]", pkg)


    class InternalType(
        typeName: InternalTypes,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf()
    ) : Type(typeName.name, pkg, protocols) //: InternalLike(typeName, pkg,, protocols)

    sealed class UserLike(
        name: String,
        typeArguments: List<Type> = emptyList(),
        var fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol>,
        var isBinding: Boolean = false,
        val typeDeclaration: SomeTypeDeclaration?, // for example List doesn't have type decl

        var needGenerateDynamic: Boolean = false, // for backend, need to generate
        var noGetters: Boolean = false
    ) : Type(name, pkg, protocols) {

        var emitName: String

        // Приватная мутабельная реализация
        private val _typeArgumentList: MutableList<Type> = typeArguments.toMutableList()

        // Публичное API — только чтение
        val typeArgumentList: List<Type>
            get() = _typeArgumentList

        // Метод для добавления аргумента типа
        fun addTypeArgument(type: Type) {
            if (_typeArgumentList.none { it.name == type.name }) {
                _typeArgumentList.add(type)
            }
        }

        fun addAllTypeArguments(types: Collection<Type>) {
            for (type in types) {
                addTypeArgument(type)
            }
        }

        fun replaceTypeArguments(types: Collection<Type>) {
            if (types.isEmpty())
                return

            _typeArgumentList.clear()
            val seenNames = mutableSetOf<String>()
            for (type in types) {
                val isNew = seenNames.add(type.name)
                if (isNew || !type.name.isGeneric()) {
                    _typeArgumentList.add(type)
                } else {
                    throw Exception("allo, 2 same generics $seenNames")
                }
            }
        }

        init {
            val decl = typeDeclaration
            if (decl != null) {

                // RENAME
                val renamePragmas = decl.pragmas.filterIsInstance<KeyPragma>().filter { it.name == Pragmas.RENAME.v }
                if (renamePragmas.isNotEmpty()) {
                    if (renamePragmas.size > 1) decl.token.compileError("You can't have more than one rename pragma, its pointless")
                    val value = renamePragmas.first().value
                    emitName = (value as? LiteralExpression.StringExpr)?.toString()
                        ?: decl.token.compileError("'rename' pragma value must be a string")
                } else {
                    emitName = name.replace("-", "_dash_")
                }

                // NO GETTERS
                val noGettersPragmas = decl.pragmas.filterIsInstance<SingleWordPragma>().filter { it.name == Pragmas.NO_GETTER.v }
                if (noGettersPragmas.isNotEmpty()) {
                    noGetters = true
                }
            } else {
                emitName = name.replace("-", "_dash_")
            }
        }

        // will get T from types like List::List::T
        // takes mutable list and return it
        fun collectGenericParamsRecursively(x: MutableSet<String>) {
            typeArgumentList.forEach {
                if (it.name.isGeneric()) {
                    var name = it.name
                    if (it !is NullableType) {
                        name += ": Any" // help the Kotlin compiler
                    }
                    x.add(name)
                }
                if (it is UserLike && it.typeArgumentList.isNotEmpty()) {
                    it.collectGenericParamsRecursively(x)
                }
            }
        }
        // use real types instead of strings
        fun collectGenericParamsRecursivelyFRFR(x: MutableSet<UnknownGenericType>) {
            typeArgumentList.forEach {
                if (it is UnknownGenericType) {
                    x.add(it)
                } else if (it is UserLike && it.typeArgumentList.isNotEmpty()) {
                    it.collectGenericParamsRecursivelyFRFR(x)
                }
            }
        }

        fun printConstructorExample() = fields.joinToString(": value") { it.name } + ": value"

        fun copy(withDifferentPkg: String? = null): UserLike =
            (when (this) {
                is UserType -> {
                    UserType(
                        name = this.name,
                        typeArgumentList = this.typeArgumentList.map {
                            if (it is UserLike) {
                                it.copy()
                            } else
                                it
                        }.toMutableList(),
                        fields = this.fields.copy(this),
                        
                        pkg = withDifferentPkg ?: this.pkg,
                        protocols = this.protocols.toMutableMap(),
                        typeDeclaration = this.typeDeclaration,
                    )
                }

                is EnumRootType -> EnumRootType(
                    name = this.name,
                    typeArgumentList = this.typeArgumentList,
                    fields = this.fields.copy(this),
                    
                    pkg = withDifferentPkg ?: this.pkg,
                    branches = this.branches.toList(),
                    protocols = this.protocols.toMutableMap(),
                    typeDeclaration = this.typeDeclaration
                )

                is UnionRootType -> UnionRootType(
                    name = this.name,
                    typeArgumentList = this.typeArgumentList,
                    fields = this.fields.copy(this),
                    
                    pkg = withDifferentPkg ?: this.pkg,
                    branches = this.branches.toList(),
                    protocols = this.protocols.toMutableMap(),
                    isError = this.isError,
                    typeDeclaration = this.typeDeclaration
                )

                is EnumBranchType -> TODO()
                is UnionBranchType -> UnionBranchType(
                    name = this.name,
                    typeArgumentList = this.typeArgumentList,
                    fields = this.fields.copy(this),
                    
                    pkg = withDifferentPkg ?: this.pkg,
                    root = this.root,
                    protocols = this.protocols.toMutableMap(),
                    isError = this.isError,
                    typeDeclaration = this.typeDeclaration
                )

                is UnknownGenericType -> UnknownGenericType(this.name)
            }).also {
                it.isBinding = this.isBinding
                it.parent = this.parent
                it.errors = this.errors?.toMutableSet()
                it.emitName = this.emitName
                it.isMutable = isMutable
                it.isVarMutable = isVarMutable
            }
    }

    class UserType(
        name: String,
        typeArgumentList: MutableList<Type> = mutableListOf(), // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        typeDeclaration: SomeTypeDeclaration?
    ) : UserLike(name, typeArgumentList, fields, pkg, protocols, typeDeclaration = typeDeclaration,)

    // Union -> Error, User
    // User -> Root, Branch
    // Error -> Root, Branch
    sealed class Union(
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        val isError: Boolean,
        typeDeclaration: SomeTypeDeclaration?
    ) : UserLike(name, typeArgumentList, fields, pkg, protocols, typeDeclaration = typeDeclaration,)

    class UnionRootType(
        var branches: List<Union>, // can be union or branch
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        isError: Boolean,
        typeDeclaration: SomeTypeDeclaration?
    ) : Union(name, typeArgumentList, fields, pkg, protocols, isError, typeDeclaration = typeDeclaration) {
        fun allBranchesDeep(): Set<Union> {
            return branches.flatMap { it.unpackUnionToAllBranches(mutableSetOf(), null) }.toSet()
        }

        fun allBranchesTopLevel(): Set<Union> {
            return branches.toSet()
        }

        fun stringAllBranches(ident: String, deep: Boolean): String {
            val unions = if (deep) this.allBranchesDeep() else this.allBranchesTopLevel()
            return buildString {
                // | ident
                append("| $ident\n")
                unions.forEachIndexed { i, union ->
                    append("| ", union.name, " => ", "[]")
                    if (i != unions.count() - 1) append("\n")
                }
            }
        }
    }

    class UnionBranchType(
        val root: UnionRootType,
        name: String,
        typeArgumentList: List<Type> = mutableListOf(), // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        isError: Boolean = false,
        typeDeclaration: SomeTypeDeclaration? = null
    ) : Union(name, typeArgumentList, fields, pkg, protocols, isError, typeDeclaration = typeDeclaration)


    class EnumRootType(
        var branches: List<EnumBranchType>,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        typeDeclaration: SomeTypeDeclaration?
    ) : UserLike(name, typeArgumentList, fields, pkg, protocols, typeDeclaration = typeDeclaration,)

    class EnumBranchType(
        val root: EnumRootType,
        name: String,
        typeArgumentList: List<Type>, // for <T, G>
        fields: MutableList<KeywordArg>,
        pkg: String,
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        typeDeclaration: SomeTypeDeclaration?
    ) : UserLike(name, typeArgumentList, fields, pkg, protocols, typeDeclaration = typeDeclaration,)


    sealed class GenericType(
        name: String,
        typeArgumentList: List<Type>,
        pkg: String,
        fields: MutableList<KeywordArg> = mutableListOf(),
        protocols: MutableMap<String, Protocol> = mutableMapOf(),
        typeDeclaration: TypeDeclaration?
    ) : UserLike(name, typeArgumentList, fields, pkg, protocols, typeDeclaration = typeDeclaration,)

    class UnknownGenericType(
        name: String,
        typeArgumentList: List<Type> = mutableListOf(),
        pkg: String = "common",
    ) : GenericType(name, typeArgumentList, pkg, typeDeclaration = null) {
        override fun toString(): String {
            return name
        }
    }
}

data class Protocol(
    val name: String,
    val unaryMsgs: MutableMap<String, UnaryMsgMetaData> = mutableMapOf(),
    val binaryMsgs: MutableMap<String, BinaryMsgMetaData> = mutableMapOf(),
    val keywordMsgs: MutableMap<String, KeywordMsgMetaData> = mutableMapOf(),
    val builders: MutableMap<String, BuilderMetaData> = mutableMapOf(),
    val staticMsgs: MutableMap<String, MessageMetadata> = mutableMapOf(),
)

class Package(
    val packageName: String,
    val declarations: MutableList<Declaration> = mutableListOf(),
    val types: MutableMap<String, Type> = mutableMapOf(),
    val builders: MutableMap<String, BuilderMetaData> = mutableMapOf(),

    // generates as import x.y.*
    val imports: MutableSet<String> = mutableSetOf(),
    val importsFromUse: MutableSet<String> = mutableSetOf(),
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

//fun TypeAST.toType(
//    typeDB: TypeDB,
//    typeTable: Map<TypeName, Type>,
//    parentType: Type.UserLike? = null,
//    resolvingFieldName: String? = null,
//    typeDeclaration: SomeTypeDeclaration? = null,
//    realParentAstFromGeneric: TypeAST? = null,
//    customPkg: String? = null
//): Type {
//    val result = this.toType2(typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration,
//        realParentAstFromGeneric, customPkg)
//    return result
//}


/// find the error types in db
fun TypeAST.getRealErrorsTypes(typeTable: Map<TypeName, Type>): Set<Type.Union> {
    if (this.errors != null) {
        val realTypes = this.errors.map { errorName ->
            val realType = typeTable[errorName]
            if (realType != null) {
                if (realType !is Type.Union || !realType.isError) token.compileError("This is not error type, use only types from errordomain declaration")
                realType
            } else {
                token.compileError("Can't find error type $realType, sorry no forvard declaration for type alias with errors, because historical reasons, need resolver here, but it would be too big refactoring, hi Seggan ^_^")
            }
        }

        return realTypes.toSet()
    }
    return emptySet()
}

// if parentType not null, then we are resolving its field
fun TypeAST.toType(
    typeDB: TypeDB,
    typeTable: Map<TypeName, Type>,
    parentType: Type.UserLike? = null,
    resolvingFieldName: String? = null,
    typeDeclaration: SomeTypeDeclaration? = null,
    realParentAstFromGeneric: TypeAST? = null,
    customPkg: String? = null,
    resolver: Resolver? = null
): Type {
    val replaceToNullableAndAddErrorsIfNeeded = { type: Type ->
        val isNullable = this.isNullable || token.kind == TokenType.NullableIdentifier || token.kind == TokenType.Null

        if (isNullable) {
            Type.NullableType(realType = type)
        } else if (errors != null) {
            val realTypes = this.getRealErrorsTypes(typeTable)
            type.copyAndAddErrors(realTypes)
        } else {
            type
        }
    }

    when (this) {
        is TypeAST.InternalType -> {
            val type = Resolver.defaultTypes.getOrElse(InternalTypes.valueOf(name)) {
                token.compileError("Can't find default type: ${YEL}$name")
            }

            return if (this.errors != null)
                replaceToNullableAndAddErrorsIfNeeded(type).copyAndAddErrors(getRealErrorsTypes(typeTable))
            else
                replaceToNullableAndAddErrorsIfNeeded(type)
        }


        is TypeAST.UserType -> {
            if (name.isGeneric()) {
                val result = (if (!isNullable)
                    Type.UnknownGenericType(name)
                else
                    Type.NullableType(Type.UnknownGenericType(name))).let {
                        if (this.errors != null) {
                            it.copyAndAddErrors(getRealErrorsTypes(typeTable))
                        } else
                            it
                }
                validateAstTypeHasGenericsDeclared(result)
                return result
            }

            if (this.typeArgumentList.isNotEmpty()) {
                // need to know what Generic name(like T), become what real type(like Int) to replace fields types from T to Int
                val typeFromDb =
                    if (resolver != null)
                        resolver.getAnyType(name,mutableMapOf(), mutableMapOf(), null, this.token )
                    else
                        typeTable[name]
                            //?: this.token.compileError("Can't find user type: ${YEL}$name")
                if (typeFromDb == null) {

                    if (parentType == null || resolvingFieldName == null || typeDeclaration == null) {
                        // we are not resolving type fields of different type
                        // yes, these 3 are not null when we are doing that
                        this.token.compileError("Can't find user type: ${YEL}$name")
                    }

                    typeDB.addUnresolvedField(
                        name,
                        FieldNameAndParent(
                            resolvingFieldName,
                            parentType,
                            typeDeclaration = typeDeclaration,
                            ast = realParentAstFromGeneric ?: this
                        )
                    )
                    return Type.UnresolvedType()
                }
                // Type DB
                if (typeFromDb is Type.UserLike) {
                    val copy = typeFromDb.copy(customPkg)
                    val letterToTypeMap = mutableMapOf<String, Type>()

                    if (this.typeArgumentList.count() != copy.typeArgumentList.count()) {
                        this.token.compileError("Type ${YEL}${this.name}${RESET} has ${WHITE}${copy.typeArgumentList.count()}${RESET} generic params, but you send only ${WHITE}${this.typeArgumentList.count()}")
                    }
                    val typeArgs = this.typeArgumentList.mapIndexed { i, it ->
                        val typeOfArg = it.toType(
                            typeDB, typeTable, parentType, resolvingFieldName, typeDeclaration,
                            realParentAstFromGeneric = realParentAstFromGeneric ?: this
                        )
                        letterToTypeMap[copy.typeArgumentList[i].name] = typeOfArg
                        typeOfArg
                    }.toMutableList()


                    copy.replaceTypeArguments(typeArgs)
                    // replace fields types from T to real
                    copy.fields.forEachIndexed { i, field ->
                        val fieldType = letterToTypeMap[field.type.name]
                        if (fieldType != null) {
                            field.type = fieldType
                        }
                    }
                    val result =  copy.also {
                        if (isMutable) it.isMutable = true
                    }.let {
                        if (this.isNullable)
                            Type.NullableType(it)
                        else
                            it
                    }
                    validateAstTypeHasGenericsDeclared(result)
                    return result
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


                typeDB.addUnresolvedField(
                    name,
                    FieldNameAndParent(
                        resolvingFieldName,
                        parentType,
                        typeDeclaration = typeDeclaration,
                        ast = realParentAstFromGeneric ?: this
                    )
                )
                return Type.UnresolvedType()
            }

            val type2 = if (this.isMutable) {
                (type).copyAnyType().also { it.isMutable = true }
            } else type

            validateAstTypeHasGenericsDeclared(type2)
            return replaceToNullableAndAddErrorsIfNeeded(type2)
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

            val lambdaType = Type.Lambda(
                args = args,
                extensionOfType = extensionOfType,
                returnType = returnType,
                pkg = customPkg ?: "common"
            )

            return replaceToNullableAndAddErrorsIfNeeded(lambdaType)
        }


    }

}


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
        ).also {
            if (typeAST.isMutable)
                it.isMutable = true // может тут надо копировать
        }
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
            branches = emptyList(),
            name = typeName,
            typeArgumentList = mutableListOf(),
            fields = mutableListOf(),
            
            pkg = pkg,
            protocols = mutableMapOf(),
            isError = isError,
            typeDeclaration = this
        )
    else if (isEnum)
        Type.EnumRootType(
            branches = emptyList(),
            name = typeName,
            typeArgumentList = mutableListOf(),
            fields = mutableListOf(),
            
            pkg = pkg,
            protocols = mutableMapOf(),
            typeDeclaration = this
        )
    else if (enumRootType != null) {
        Type.EnumBranchType(
            root = enumRootType,
            name = typeName,
            typeArgumentList = mutableListOf(),
            fields = mutableListOf(),
            
            pkg = pkg,
            protocols = mutableMapOf(),
            typeDeclaration = this
        ).also { it.parent = unionRootType }
    } else if (unionRootType != null) {
        Type.UnionBranchType(
            root = unionRootType,
            name = typeName,
            typeArgumentList = mutableListOf(),
            fields = mutableListOf(),
            
            pkg = pkg,
            protocols = mutableMapOf(),
            isError = isError,
            typeDeclaration = this
        ).also {
            it.parent = unionRootType
        }
    } else
        Type.UserType(
            name = typeName,
            typeArgumentList = mutableListOf(),
            fields = mutableListOf(),
            
            pkg = pkg,
            protocols = mutableMapOf(),
            typeDeclaration = this
        )


    val fieldsTyped = mutableListOf<KeywordArg>()
    val unresolvedSelfTypeFields = mutableListOf<KeywordArg>()

    fields.forEach {
        val astType = it.typeAST
        if (astType != null && astType.name == typeName) {
            // this is recursive type
            val fieldType = KeywordArg(
                name = it.name,
                type = if (!astType.isNullable) Type.UnresolvedType() else Type.NullableType(Type.UnresolvedType())
            )
            fieldsTyped.add(fieldType)
            unresolvedSelfTypeFields.add(fieldType)


        } else {
            fieldsTyped.add(it.toTypeField(typeDB, typeTable, parentType = result, typeDeclaration = this))
        }
    }

    fun getAllGenericTypesFromFields(
        fields2: List<KeywordArg>,
        fields: List<TypeFieldAST>,
        setOfCheckedFields: MutableSet<Type>
    ): MutableList<Type> {
        val result2 = mutableListOf<Type>()

        fields2.forEachIndexed { i, it ->
            val type = it.type
            val nullUnpacked = type.unpackNull()

            if (nullUnpacked is Type.UserLike) {
                val unknownGenericTypes = mutableListOf<Type.UserLike>()
                nullUnpacked.typeArgumentList.forEach {
                    if (it.name.isGeneric()) {
                        unknownGenericTypes.add(Type.UnknownGenericType(name = it.name))
                    }
                }

                if (nullUnpacked is Type.UnknownGenericType)
                    result2.add(type)

                result2.addAll(unknownGenericTypes)

                if (nullUnpacked !in setOfCheckedFields && nullUnpacked.fields.isNotEmpty()) {
                    setOfCheckedFields.add(nullUnpacked)
                    result2.addAll(getAllGenericTypesFromFields(nullUnpacked.fields, fields, setOfCheckedFields))
                }
            }

        }
        return result2
    }

    val genericsDeclarated = fieldsTyped.asSequence()
        .filter { it.type is Type.UnknownGenericType }
        .map { it.type }
    val genericsFromFieldsTypes = getAllGenericTypesFromFields(fieldsTyped, fields, mutableSetOf())


    val genericsFromFieldsAndDecl = (genericsDeclarated + genericsFromFieldsTypes).toMutableList()

    unresolvedSelfTypeFields.forEach {
        it.type = if ((it.type !is Type.NullableType)) result else Type.NullableType(result)
    }

    // add already declared generic fields(via `type Sas::T` syntax)
    this.genericFields.forEach {
        if (it.isGeneric() && genericsFromFieldsAndDecl.find { x -> x.name == it } == null) {
            genericsFromFieldsAndDecl.add(Type.UnknownGenericType(it))
            // add to recursive ast types of fields generic params

        }
    }
    // add generics params to astTypes of fields
    fields.asSequence()
        .map { it.typeAST }
        .filterIsInstance<TypeAST.UserType>()
        .filter { it.names.first() == this.typeName }
        .forEach { field ->
            field.typeArgumentList.addAll(
                genericsFromFieldsAndDecl.map {
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
            genericsFromFieldsAndDecl.addAll(unpackedNull.typeArgumentList.filter { it.name.isGeneric() })
        }
    }

    result.replaceTypeArguments(genericsFromFieldsAndDecl.distinctBy { it.name })
    result.fields = fieldsTyped
    //    result.protocols
    // Box::List::T will be resolved, but we need only Box::T to generate correct method in codogen
    // not class Box<List<T>>, but Box<T>
    fun copyTypeAndReplaceGenericListToReal(x: Type.UserLike): Type.UserLike {
        if (x.typeArgumentList.isEmpty()) return x
        if (x.typeArgumentList.first() is Type.UnknownGenericType) return x
        val copy = x.copy()
        copy.replaceTypeArguments(result.typeArgumentList)
//        copy.typeArgumentList = result.typeArgumentList
        return copy
    }

    this.receiver = copyTypeAndReplaceGenericListToReal(result)
    return result
}


fun MessageDeclaration.toAnyMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    isGetter: Boolean = false,
    isSetter: Boolean = false,// only for bindings of fields, we cant add new field, it will break the constructor, so we add msgs
    resolver: Resolver,
    receiverType: Type
): MessageMetadata {
    val result = when (this) {
        is MessageDeclarationKeyword -> toMessageData(typeDB, typeTable, pkg, isSetter)
        is MessageDeclarationUnary -> toMessageData(typeDB, typeTable, pkg, isGetter)
        is MessageDeclarationBinary -> toMessageData(typeDB, typeTable, pkg)
        is ConstructorDeclaration -> {
            val constructorForType = forType
            if (constructorForType is Type.UserLike && constructorForType.isBinding && body.isNotEmpty()) {
                this.token.compileError("Can't create custom constructors for binding, that require companion object in Kotlin(wait for static extension feature)")
            }
            if (this.returnTypeAST == null) {
                this.returnType = Resolver.defaultTypes[InternalTypes.Unit]!!
            }
            resolver.addStaticDeclaration(this, receiverType)
        }

        is StaticBuilderDeclaration -> {
            toMessageData(typeDB, typeTable, pkg, receiverType)
        }
    }

    result.forMutableType = forTypeAst.isMutable

    return result
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
        isGetter = isGetter,
        declaration = this
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

        pragmas = pragmas,
        declaration = this
    )
    return result
}

fun StaticBuilderDeclaration.toMessageData(
    typeDB: TypeDB,
    typeTable: MutableMap<TypeName, Type>,
    pkg: Package,
    forType: Type,
): BuilderMetaData {
    val x = this.msgDeclaration.toMessageData(typeDB, typeTable, pkg)
    if (receiverAst != null) {
        this.receiverType = receiverAst.toType(typeDB, typeTable)
    }
    return BuilderMetaData(
        name = x.name,
        argTypes = x.argTypes,
        forType = forType,
        receiverType = receiverType,
        returnType = x.returnType,
        pkg = x.pkg,
        pragmas = x.pragmas,
        msgSends = x.msgSends,
        defaultAction = defaultAction,
        declaration = this
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
        isSetter = isSetter,
        declaration = this
    )
    return result
}
