package frontend.parser.types.ast

import frontend.meta.Token
import frontend.parser.parsing.CodeAttribute
import frontend.resolver.Type

sealed class MessageDeclaration(
        val name: String,
        val forTypeAst: TypeAST,
        token: Token,
        val isSingleExpression: Boolean,
        val body: List<Statement>,
        val returnTypeAST: TypeAST?,
        isPrivate: Boolean = false,
        pragmas: MutableList<CodeAttribute> = mutableListOf(),
        val isInline: Boolean = false,
        var forType: Type? = null,
        var returnType: Type? = null,
        var isRecursive: Boolean = false,
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
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
    isInline: Boolean = false

) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas, isInline, )

class MessageDeclarationBinary(
    name: String,
    forType: TypeAST,
    token: Token,
    val arg: KeywordDeclarationArg,
    body: List<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
    isInline: Boolean = false
) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas, isInline, )


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val type: TypeAST? = null,
) {
    override fun toString(): String {
        return "$name: ${type?.name}"
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
    val typeArgs: MutableList<String> = mutableListOf(),
    isPrivate: Boolean = false,
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
    isInline: Boolean = false
) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas, isInline, ) {
    override fun toString(): String {
        return "${forTypeAst.name} ${args.joinToString(" ") { it.name + ": " + it.type?.name }} -> ${returnType?.name ?: returnTypeAST?.name ?: "Unit"}"
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
    pragmas: MutableList<CodeAttribute> = mutableListOf(),
) : Declaration(token, isPrivate, pragmas)
