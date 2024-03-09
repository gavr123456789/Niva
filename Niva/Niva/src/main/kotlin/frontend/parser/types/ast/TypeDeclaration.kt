package main.frontend.parser.types.ast

import frontend.parser.types.ast.Pragma
import frontend.resolver.Type
import frontend.resolver.Type.RecursiveType.isPrivate
import frontend.resolver.Type.RecursiveType.name
import main.frontend.meta.Token


sealed class TypeAST(
    val name: String,
    val isNullable: Boolean,
    token: Token,
    isPrivate: Boolean,
    pragmas: MutableList<Pragma>
) : Statement(token, isPrivate, pragmas) {
//    val name: String
//        get() = this.type.name

    // [anyType, anyType -> anyType]?


    // generics are UserTypes now because they need names
    class UserType(
        name: String,

        val typeArgumentList: MutableSet<TypeAST> = mutableSetOf(), // in recursive types like node, new generics can be added
        isNullable: Boolean = false,
        token: Token,
        val names: List<String> = listOf(name),
        isPrivate: Boolean = false,
        pragmas: MutableList<Pragma> = mutableListOf()
    ) : TypeAST(name, isNullable, token, isPrivate, pragmas) {
        override fun toString(): String {
            return names.joinToString(".")
        }
    }

    class InternalType(
        name: InternalTypes,
        token: Token,
        isNullable: Boolean = false,
        isPrivate: Boolean = false,
        pragmas: MutableList<Pragma> = mutableListOf()
    ) : TypeAST(name.name, isNullable, token, isPrivate, pragmas)

    class Lambda(
        name: String,
        val inputTypesList: List<TypeAST>,
        val returnType: TypeAST,
        token: Token,
        val extensionOfType: TypeAST? = null, // String.[x: Int -> Int]
        isNullable: Boolean = false,
        isPrivate: Boolean = false,
        pragmas: MutableList<Pragma> = mutableListOf()
    ) : TypeAST(name, isNullable, token, isPrivate, pragmas)
}


class EnumFieldAST(
    val name: String,
    val value: Expression,
    val token: Token,
) {
    override fun toString(): String {
        return "$name: $value"
    }
}

class TypeFieldAST(
    val name: String,
    val typeAST: TypeAST?,
    var type: Type?,
    val token: Token,
) {
    override fun toString(): String {
        return name + ":" + (typeAST?.toString() ?: "")
    }
}

sealed class SomeTypeDeclaration(
    val typeName: String,
    val fields: List<TypeFieldAST>,
    token: Token,
    val genericFields: MutableSet<String> = mutableSetOf(),
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    var receiver: Type? = null, // for codegen
) : Declaration(token, isPrivate, pragmas)

class TypeDeclaration(
    typeName: String,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableSet<String> = mutableSetOf(),
    pragmas: MutableList<Pragma> = mutableListOf(),
    isPrivate: Boolean = false,
) : SomeTypeDeclaration(typeName, fields, token, genericFields, isPrivate, pragmas) {
    override fun toString(): String {
        return "TypeDeclaration($typeName)"
    }
}

class EnumBranch(
    name: String,
    val fieldsValues: List<EnumFieldAST>,
    token: Token,
    val root: EnumDeclarationRoot,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : SomeTypeDeclaration(name, listOf(), token, mutableSetOf(), isPrivate, pragmas) {
    override fun toString(): String {
        return name + " " + fields.joinToString(", ") { it.name + ": " + it.toString() }
    }
}

class EnumDeclarationRoot(
    typeName: String,
    var branches: List<EnumBranch>,
    fields: List<TypeFieldAST>,
    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isPrivate: Boolean = false,
) : SomeTypeDeclaration(typeName, fields, token, mutableSetOf(), isPrivate, pragmas)

class UnionBranch(
    typeName: String,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableSet<String> = mutableSetOf(),
    val root: UnionDeclaration,
    var isRoot: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : SomeTypeDeclaration(typeName, fields, token, genericFields, pragmas = pragmas) {
    override fun toString(): String {
        return typeName + " " + fields.joinToString(", ") { it.name + ": " + it.typeAST }
    }
}

class UnionDeclaration(
    typeName: String,
    var branches: List<UnionBranch>,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableSet<String> = mutableSetOf(),
    pragmas: MutableList<Pragma> = mutableListOf(),
    isPrivate: Boolean = false,
) : SomeTypeDeclaration(typeName, fields, token, genericFields, isPrivate, pragmas)

class AliasDeclaration(
    val typeName: String,
    @Suppress("unused") val matchedTypeName: String,
    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isPrivate: Boolean = false,
) : Declaration(token, isPrivate, pragmas)


enum class InternalTypes {
    Int, String, Float, Double, Boolean, Unit, Project, Char, IntRange, CharRange, Any, Bind, Compiler, Nothing, Exception, Null, UnknownGeneric
}
