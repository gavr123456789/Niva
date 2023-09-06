package codogen

import frontend.parser.types.ast.*

fun Expression.generateExpression(): String {
    return when (this) {
        is ExpressionInBrackets -> this.generateExpressionInBrackets()

        is MessageSend -> this.generateMessageCall()
        is IdentifierExpr -> if (name != "do") this.name else "`do`"
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> this.str

        is ListCollection -> {
            this.generateList()
        }

        is MapCollection -> TODO()
        is ControlFlow.IfExpression -> this.generateIf()
        is ControlFlow.IfStatement -> this.generateIf()
        is ControlFlow.SwitchExpression -> this.generateSwitch()
        is ControlFlow.SwitchStatement -> this.generateSwitch()

        // when message is receiver
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()


        is CodeBlock -> this.generateCodeBlock()
    }

}

fun ExpressionInBrackets.generateExpressionInBrackets() = buildString {
    append("(")
    val statementsCode = codogenKt(statements, 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}
