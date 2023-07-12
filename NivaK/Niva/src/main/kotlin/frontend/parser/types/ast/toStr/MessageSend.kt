package frontend.parser.types.ast.toStr

import frontend.parser.types.ast.*

fun Receiver.toNivaStr(): String {
    return when (this) {
        is CodeBlock -> TODO()
        is ListCollection -> TODO()
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()
        is IdentifierExpr -> TODO()
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> this.str
    }
}

fun Message.toNivaStr(): String {
    return when (this) {
        is UnaryMsg -> this.toNivaStr()
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
    }
}

fun UnaryMsg.toNivaStr(): String {
    return this.selectorName
}

fun MessageSendUnary.toNivaStr(): String {
    val receiverStr = receiver.toNivaStr()
    val unaryMessages = messages.joinToString(" ") { it.toNivaStr() }
    return "$receiverStr $unaryMessages"
}

fun MessageSendBinary.toNivaStr(): String {
    val receiverStr = receiver.toNivaStr()
    val unaryMessages = messages.joinToString(" ") { it.toNivaStr() }
    return "$receiverStr $unaryMessages"
}
