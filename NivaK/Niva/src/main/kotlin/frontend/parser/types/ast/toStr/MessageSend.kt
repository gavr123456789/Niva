package frontend.parser.types.ast.toStr

import codogen.generateExpressionInBrackets
import frontend.parser.types.ast.*

fun Receiver.toNivaStr(): String {
    return when (this) {

        is ExpressionInBrackets -> this.generateExpressionInBrackets()

        is MessageSend -> TODO()
        is CodeBlock -> TODO()
        is ListCollection -> TODO()
        is SetCollection -> TODO()
        is MapCollection -> TODO()

        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()
        is IdentifierExpr -> TODO()

        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> this.str
        is LiteralExpression.CharExpr -> this.str
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
