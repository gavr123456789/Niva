package main.frontend.parser.types.ast

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.Pragma
import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.Token
import main.frontend.resolver.findAnyMsgType
import java.util.Stack

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
    val isSuspend: Boolean = false,
    var forType: Type? = null,
    var returnType: Type? = null,
    var isRecursive: Boolean = false,
    val typeArgs: MutableList<String> = mutableListOf(),
    val stackOfPossibleErrors: Stack<Pair<Message, MutableSet<Type.Union>>> = Stack(),
    var messageData: MessageMetadata? = null
) : Declaration(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "${forTypeAst.name} $name -> ${returnType?.toString() ?: returnTypeAST?.name ?: "Unit"}"
    }

    fun getDeclType(): MessageDeclarationType = when (this) {
        is MessageDeclarationUnary -> MessageDeclarationType.Unary
        is MessageDeclarationBinary -> MessageDeclarationType.Binary
        is MessageDeclarationKeyword -> MessageDeclarationType.Keyword

        is ConstructorDeclaration -> this.msgDeclaration.getDeclType()
        is StaticBuilderDeclaration -> this.msgDeclaration.getDeclType()
    }
    fun findMetadata(resolver: Resolver): MessageMetadata {

        val msgKind = getDeclType()

        val msgType = resolver.findAnyMsgType(
            this.forType!!,
            this.name,
            this.token,
            msgKind
        )

        return msgType

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
    isInline: Boolean,
    isSuspend: Boolean,
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
    isSuspend,
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
    isSuspend: Boolean,
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
    isSuspend,
    typeArgs = typeArgs
)


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val typeAST: TypeAST? = null,
    val type: Type? = null,

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
    isInline: Boolean = false,
    isSuspend: Boolean,

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
    isSuspend,
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


class StaticBuilderDeclaration(
    val msgDeclaration: MessageDeclarationKeyword,
    val defaultAction: CodeBlock?,
    val receiverAst: TypeAST? = null, // Surface(Receiver) builder Card(forType) = [...]
    var receiverType: Type? = null,
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
    msgDeclaration.isInline,
    msgDeclaration.isSuspend,
    msgDeclaration.forType,
    msgDeclaration.returnType,
    msgDeclaration.isRecursive,
    msgDeclaration.typeArgs,
    msgDeclaration.stackOfPossibleErrors,
    msgDeclaration.messageData
)
