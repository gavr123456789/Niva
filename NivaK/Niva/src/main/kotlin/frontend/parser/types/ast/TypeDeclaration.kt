package frontend.parser.types.ast

import frontend.meta.Token

sealed class Type(
    val name: String,
    val isNullable: Boolean,
    token: Token,
    isPrivate: Boolean,
    pragmas: List<Pragma>
) : Statement(token, isPrivate, pragmas) {


    class InternalType(
        typeName: InternalTypes,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : Type(typeName.name, isNullable, token, isPrivate, pragmas)

    class UserType(
        name: String,
        val typeArgumentList: List<Type>,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : Type(name, isNullable, token, isPrivate, pragmas)

    // [anyType, anyType -> anyType]?
    class Lambda(
        name: String,
        val inputTypesList: List<Type>,
        val returnType: Type,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : Type(name, isNullable, token, isPrivate, pragmas)
}

class TypeField(
    val name: String,
    val type: Type?,
    val token: Token
)

interface ITypeDeclaration {
    val typeName: String
    val fields: List<TypeField>
}

class TypeDeclaration(
    override val typeName: String,
    override val fields: List<TypeField>,
    token: Token,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Statement(token, isPrivate, pragmas), ITypeDeclaration

class UnionBranch(
    override val typeName: String,
    override val fields: List<TypeField>,
    val token: Token,
) : ITypeDeclaration

class UnionDeclaration(
    override val typeName: String,
    val branches: List<UnionBranch>,
    override val fields: List<TypeField>,
    token: Token,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Statement(token, isPrivate, pragmas), ITypeDeclaration

@Suppress("EnumEntryName")
enum class InternalTypes {
    int, string, float, boolean
}
