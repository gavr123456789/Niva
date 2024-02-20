package frontend.parser.types.ast

import frontend.meta.Token
import frontend.resolver.Type

sealed class MessageDeclaration(
    val name: String,
    val forTypeAst: TypeAST,
    token: Token,
    val isSingleExpression: Boolean,
    val body: List<Statement>,
    val returnTypeAST: TypeAST?,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    val isInline: Boolean = false,
    var forType: Type? = null,
    var returnType: Type? = null,
    var isRecursive: Boolean = false,
    val typeArgs: MutableList<String> = mutableListOf(),
) : Declaration(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "${forTypeAst.name} $name -> ${returnType?.name ?: returnTypeAST?.name ?: "Unit"}"
    }
}

class MessageDeclarationUnary(
    name: String,
    forType: TypeAST,
    token: Token,
    isSingleExpression: Boolean,
    body: List<Statement>,
    returnType: TypeAST?,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isInline: Boolean = false,
    typeArgs: MutableList<String> = mutableListOf()

) : MessageDeclaration(
    name,
    forType,
    token,
    isSingleExpression,
    body,
    returnType,
    isPrivate,
    pragmas,
    isInline,
    typeArgs = typeArgs
)

class MessageDeclarationBinary(
    name: String,
    forType: TypeAST,
    token: Token,
    val arg: KeywordDeclarationArg,
    body: List<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isInline: Boolean = false,
    typeArgs: MutableList<String> = mutableListOf()

) : MessageDeclaration(
    name,
    forType,
    token,
    isSingleExpression,
    body,
    returnType,
    isPrivate,
    pragmas,
    isInline,
    typeArgs = typeArgs
)


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val typeAST: TypeAST? = null,
) {
    override fun toString(): String {
        return "$name: ${typeAST?.name}"
    }
}

fun KeywordDeclarationArg.name() = localName ?: name


class MessageDeclarationKeyword(
    name: String,
    forType: TypeAST,
    token: Token,
    val args: List<KeywordDeclarationArg>,
    body: List<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    typeArgs: MutableList<String> = mutableListOf(),
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isInline: Boolean = false
) : MessageDeclaration(
    name,
    forType,
    token,
    isSingleExpression,
    body,
    returnType,
    isPrivate,
    pragmas,
    isInline,
    typeArgs = typeArgs
) {
    override fun toString(): String {
        return "${forTypeAst.name} ${args.joinToString(" ") { it.name + ": " + it.typeAST?.name }} -> ${returnType?.name ?: returnTypeAST?.name ?: "Unit"}"
    }
}

class ConstructorDeclaration(
    val msgDeclaration: MessageDeclaration,
    token: Token,
) : MessageDeclaration(
    msgDeclaration.name,
    msgDeclaration.forTypeAst,
    token,
    msgDeclaration.isSingleExpression,
    msgDeclaration.body,
    msgDeclaration.returnTypeAST,
    msgDeclaration.isPrivate,
    msgDeclaration.pragmas,
)


class ExtendDeclaration(
    @Suppress("unused")
    val forTypeAst: TypeAST,
    val messageDeclarations: List<MessageDeclaration>,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Declaration(token, isPrivate, pragmas)

@Suppress("unused")
class StaticBuilderDeclaration(
    val name: String,
    token: Token,
    val args: List<KeywordDeclarationArg>,
    body: List<Statement>,
    returnType: TypeAST?,
    val defaultAction: CodeBlock? = null,
    val typeArgs: MutableList<String> = mutableListOf(),
    isPrivate: Boolean = false,
    pragmas: MutableList<Pragma> = mutableListOf(),
    isInline: Boolean = false
) : Declaration(token, isPrivate, pragmas)
