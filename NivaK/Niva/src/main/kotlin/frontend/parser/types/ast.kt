package frontend.parser.types

import frontend.meta.Token
import frontend.parser.MessageDeclarationType

// https://github.com/antlr/grammars-v4/blob/master/smalltalk/Smalltalk.g4
sealed class ASTNode2(
    val token: Token
) {
    val str: String
        get() = this.token.lexeme
}

sealed class Statement(
    token: Token,
    val isPrivate: Boolean,
    val pragmas: List<Pragma>,
) : ASTNode2(token) {
    override fun toString(): String {
        return "Declaration(${token.lexeme})"
    }
}

sealed class Expression(
    val type: String? = null,
    token: Token,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
) : Statement(token, isPrivate, pragmas)

class VarDeclaration(
    token: Token,
    val name: String,
    val value: Expression,
    val valueType: String? = null,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Statement(token, isPrivate, pragmas) {
    override fun toString(): String {
        return "VarDeclaration(${name} = ${value.str}, valueType=$valueType)"
    }
}


// receiver

//  | Primary
//  | BlockConstructor
//  | BracketExpression
//  | CollectionLiteral
sealed class Receiver(type: String?, token: Token) : Expression(type, token)


// PRIMARY
// identifier | LiteralExpression
sealed class Primary(type: String?, token: Token) : Receiver(type, token)

// LITERALS
sealed class LiteralExpression(type: String?, literal: Token) : Primary(type, literal) {
    class IntExpr(literal: Token) : LiteralExpression("int", literal)
    class StringExpr(literal: Token) : LiteralExpression("string", literal)
    class FalseExpr(literal: Token) : LiteralExpression("bool", literal)
    class TrueExpr(literal: Token) : LiteralExpression("bool", literal)
    class FloatExpr(literal: Token) : LiteralExpression("float", literal)
}

class IdentifierExpr(
    name: String,
    type: String?,
    token: Token,
//    val depth: Int,
) : Primary(type, token)

sealed class Collection(type: String?, token: Token) : Receiver(type, token)

class ListCollection(
    val initElements: List<Primary>,
    type: String?,
    token: Token,
) : Collection(type, token)


// MESSAGES

// x sas + y sus
class MessageCall(
    val receiver: Receiver,
    val messages: List<Message>,
    val mainMessageType: MessageDeclarationType, // это нужно превратить в union тип
    type: String?,
    token: Token
) :
    Expression(type, token) {
    override fun toString(): String {
        return "${messages.map { it.toString() }}"
    }
}

// binaryMessage | unaryMessage | keywordMessage
sealed class Message(
    val receiver: Receiver,
    val selectorName: String,
    val type: String?,
    val token: Token
)

class UnaryMsg(
    receiver: Receiver,
    selectorName: String,
    type: String?,
    token: Token,
) : Message(receiver, selectorName, type, token)

class BinaryMsg(
    receiver: Receiver,
    val unaryMsgsForReceiver: List<UnaryMsg>,
    selectorName: String,
    type: String?,
    token: Token,
    val argument: Receiver,
    val unaryMsgsForArg: List<UnaryMsg>,

//    val unaryMsgs: List<UnaryFirstMsg> = listOf(),
) : Message(receiver, selectorName, type, token)


data class KeywordArgAndItsMessages(
    val selectorName: String,
    val keywordArg: Receiver,
    // there can't be unary AND binary messages in one time, binary will contain unary
    val unaryOrBinaryMsgsForArg: List<Message>
) {
    override fun toString(): String {
        if (unaryOrBinaryMsgsForArg.isNotEmpty()) {
            val firstMsg = unaryOrBinaryMsgsForArg[0]
            if (firstMsg is BinaryMsg) {
//                val u = unaryOrBinaryMessagesForArg.filterIsInstance<UnaryMsg>()
                val receiver = firstMsg.receiver
                val unaryForReceiver =
                    if (firstMsg.unaryMsgsForReceiver.isNotEmpty())
                        firstMsg.unaryMsgsForReceiver.map { it.selectorName }.toString()
                    else ""
                val unaryForArg =
                    if (firstMsg.unaryMsgsForArg.isNotEmpty())
                        firstMsg.unaryMsgsForArg.map { it.selectorName }.toString()
                    else ""
                val binaryOperator = firstMsg.selectorName
                val arg = firstMsg.argument.str
                return "$receiver $unaryForReceiver $binaryOperator $arg $unaryForArg "

            } else {
                // unary
                return "${keywordArg.str} ${unaryOrBinaryMsgsForArg.map { it.selectorName }}"
            }

        }
        return "$selectorName: ${keywordArg.str}"
    }
}

class KeywordMsg(
    receiver: Receiver,
    selectorName: String,
    type: String?,
    token: Token,
    val args: List<KeywordArgAndItsMessages>
) : Message(receiver, selectorName, type, token) {
    override fun toString(): String {

        val receiverName = receiver

        return "KeywordCall($receiverName ${args.map { it.toString() }})"
    }
}


class Pragma(
    val name: IdentifierExpr,
    val args: List<LiteralExpr>
)

@JvmInline
value class LiteralExpr(val literal: Token)


class TypeField(
    val name: String,
    val type: String?,
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


sealed class IfBranch(
    val ifExpression: Expression,
) {
    class IfBranchSingleExpr(
        ifExpression: Expression,
        val thenDoExpression: Expression
    ) : IfBranch(ifExpression)

    class IfBranchWithBody(
        ifExpression: Expression,
        val body: List<Statement>
    ) : IfBranch(ifExpression)
}


sealed class ControlFlow(
    val ifBranches: List<IfBranch>,
    val elseBranch: List<Statement>?,
    token: Token,
    type: String?,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Expression(type, token, isPrivate, pragmas) {

    sealed class If(
        ifBranches: List<IfBranch>,
        elseBranch: List<Statement>?,
        token: Token,
        type: String?
    ) : ControlFlow(ifBranches, elseBranch, token, type)

    class IfExpression(
        type: String?,
        branches: List<IfBranch>,
        elseBranch: List<Statement>,
        token: Token
    ) : If(branches, elseBranch, token, type)

    class IfStatement(
        type: String?,
        branches: List<IfBranch>,
        elseBranch: List<Statement>?,
        token: Token
    ) : If(branches, elseBranch, token, type)


    sealed class Switch(
        val switch: Expression,
        iF: If
    ) : ControlFlow(iF.ifBranches, iF.elseBranch, iF.token, iF.type)

    class SwitchStatement(
        switch: Expression,
        iF: If
    ) : Switch(switch, iF)

    class SwitchExpression(
        switch: Expression,
        iF: If
    ) : Switch(switch, iF)


}

