package codogen

import frontend.parser.types.ast.*

fun Expression.generateExpression(): String = buildString {

    if (isInlineRepl) {
        append("inlineRepl(")
    }

    append(
        when (this@generateExpression) {
            is ExpressionInBrackets -> generateExpressionInBrackets()

            is MessageSend -> generateMessageCall()
            is IdentifierExpr -> if (name != "do") name else "`do`"
            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.FloatExpr -> str
            is LiteralExpression.IntExpr -> str
            is LiteralExpression.StringExpr -> str

            is ListCollection -> {
                generateList()
            }

            is MapCollection -> TODO()
            is ControlFlow.If -> generateIf()
            is ControlFlow.Switch -> generateSwitch()

            // when message is receiver
            is BinaryMsg -> TODO()
            is KeywordMsg -> TODO()
            is UnaryMsg -> TODO()


            is CodeBlock -> generateCodeBlock()
        }
    )

    if (isInlineRepl) {
        val fileAndLine = token.file.absolutePath + ":::" + token.line
        append(", \"\"\"$fileAndLine\"\"\", $inlineReplCounter)")
    }

}

fun ExpressionInBrackets.generateExpressionInBrackets() = buildString {
    append("(")
    val statementsCode = codogenKt(statements, 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}
