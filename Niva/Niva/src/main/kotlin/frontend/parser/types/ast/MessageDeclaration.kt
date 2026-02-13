package main.frontend.parser.types.ast

import frontend.parser.parsing.MessageDeclarationType
import frontend.parser.types.ast.Pragma
import frontend.resolver.MessageMetadata
import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.resolver.findAnyMsgType
import java.util.Stack

class PairOfErrorAndMessage (val msg: Message, val errors: Set<Type.Union>) {
    override fun toString(): String {
        val errorsStr = if (errors.count() == 1) errors.first() else errors.joinToString(prefix = "(", postfix = ")")
        return "${msg.selectorName} $errorsStr"
    }

}

sealed class MessageDeclaration(
    val name: String,
    val forTypeAst: TypeAST,
    token: Token,
    val isSingleExpression: Boolean,
    val body: MutableList<Statement>,
    val returnTypeAST: TypeAST?,
    pragmas: MutableList<Pragma> = mutableListOf(),
    val isInline: Boolean = false,
    val isSuspend: Boolean = false,
    var forType: Type? = null,
    var returnType: Type? = null,
    var isRecursive: Boolean = false,
    val typeArgs: MutableList<String> = mutableListOf(),
    val stackOfPossibleErrors: Stack<PairOfErrorAndMessage> = Stack(),
    var messageData: MessageMetadata? = null
) : Declaration(token, pragmas) {
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
    body: MutableList<Statement>,
    returnType: TypeAST?,
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
    body: MutableList<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
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
    pragmas,
    isInline,
    isSuspend,
    typeArgs = typeArgs
)


// key(localName): type
class KeywordDeclarationArg(
    val name: String,
    val tok: Token,
    val localName: String? = null,
    val typeAST: TypeAST? = null,
    var type: Type? = null,

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
    body: MutableList<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    typeArgs: MutableList<String> = mutableListOf(),
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
    msgDeclaration.pragmas,
) {
    fun isFun() = token.kind == TokenType.Fun
}

class ManyConstructorDecl(
    val messageDeclarations: List<ConstructorDeclaration>,
    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Declaration(token, pragmas)

class ExtendDeclaration(
    val messageDeclarations: List<MessageDeclaration>,
    token: Token,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Declaration(token, pragmas)


class StaticBuilderDeclaration(
    val msgDeclaration: MessageDeclarationKeyword,
    val defaultAction: CodeBlock?,
    val withReceiver: Boolean,
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
