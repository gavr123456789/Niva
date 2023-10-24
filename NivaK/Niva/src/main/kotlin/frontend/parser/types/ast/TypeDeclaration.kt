package frontend.parser.types.ast

import frontend.meta.Token
import frontend.parser.parsing.CodeAttribute


sealed class TypeAST(
    val name: String,
    val isNullable: Boolean,
    token: Token,
    isPrivate: Boolean,
    pragmas: List<Pragma>
) : Statement(token, isPrivate, pragmas) {
//    val name: String
//        get() = this.type.name

    // [anyType, anyType -> anyType]?


    class UserType(
        name: String,
        val typeArgumentList: List<TypeAST>,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : TypeAST(name, isNullable, token, isPrivate, pragmas)

    class InternalType(
        name: InternalTypes,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : TypeAST(name.name, isNullable, token, isPrivate, pragmas)

    class Lambda(
        name: String,
        val inputTypesList: List<TypeAST>,
        val returnType: TypeAST,
        isNullable: Boolean,
        token: Token,
        isPrivate: Boolean = false,
        pragmas: List<Pragma> = listOf()
    ) : TypeAST(name, isNullable, token, isPrivate, pragmas)
}


fun TypeAST.toKotlinStr(): String {
//    val x: (String, Int) -> Int = {}
    return when (this) {
        is TypeAST.InternalType -> name
        is TypeAST.UserType -> buildString {
            append(name)
            if (typeArgumentList.isNotEmpty()) {
                append("<${typeArgumentList.joinToString(", ") { it.name }}>")
            }
        }

        is TypeAST.Lambda -> {
            buildString {
                append("(")
                inputTypesList.forEach {
                    append(it.toKotlinStr(), ",")
                }
                append(") -> ")

                append(returnType.toKotlinStr())

            }
        }
    }
}


class TypeFieldAST(
    val name: String,
    val type: TypeAST?,
    val token: Token,
)

sealed class SomeTypeDeclaration(
    val typeName: String,
    val fields: List<TypeFieldAST>,
    token: Token,
    val genericFields: MutableList<String> = mutableListOf(),
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
    val codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
) : Declaration(token, isPrivate, pragmas)

class TypeDeclaration(
    typeName: String,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableList<String> = mutableListOf(),
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
    codeAttributes: MutableList<CodeAttribute>,
) : SomeTypeDeclaration(typeName, fields, token, genericFields, isPrivate, pragmas, codeAttributes) {
    override fun toString(): String {
        return "TypeDeclaration($typeName)"
    }
}

class UnionBranch(
    typeName: String,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableList<String> = mutableListOf(),
    val root: UnionDeclaration,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
) : SomeTypeDeclaration(typeName, fields, token, genericFields, codeAttributes = codeAttributes)

class UnionDeclaration(
    typeName: String,
    var branches: List<UnionBranch>,
    fields: List<TypeFieldAST>,
    token: Token,
    genericFields: MutableList<String> = mutableListOf(),
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
    codeAttributes: MutableList<CodeAttribute> = mutableListOf(),
) : SomeTypeDeclaration(typeName, fields, token, genericFields, isPrivate, pragmas, codeAttributes)

class AliasDeclaration(
    val typeName: String,
    val matchedTypeName: String,
    token: Token,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Declaration(token, isPrivate, pragmas)


enum class InternalTypes {
    Int, String, Float, Boolean, Unit, Project, Char, IntRange, Any, Bind,

    //    List, Map, Set,
//    Unknown // For generics like T
}
