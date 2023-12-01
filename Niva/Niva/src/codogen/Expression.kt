package codogen

import frontend.parser.types.ast.*

fun replaceKeywords(str: String) =
    when (str) {
        "do", "val", "var", "class", "in" -> "`$str`"
        else -> str
    }

fun Expression.generateExpression(replaceLiteral: String? = null): String = buildString {

    if (isInlineRepl) {
        append("inlineRepl(")
    }



    append(
        when (this@generateExpression) {
            is ExpressionInBrackets -> generateExpressionInBrackets()

            is MessageSend -> generateMessageCall()
            is IdentifierExpr -> replaceKeywords(replaceLiteral ?: name)
            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.FloatExpr -> str
            is LiteralExpression.IntExpr -> str
            is LiteralExpression.StringExpr -> str
            is LiteralExpression.CharExpr -> str
            is DotReceiver -> ""

            is ListCollection -> {
                generateList()
            }

            is SetCollection -> {
                generateSet()
            }


            is MapCollection -> generateMap()
            is ControlFlow.If -> generateIf()
            is ControlFlow.Switch -> generateSwitch()

            // when message is receiver
            is BinaryMsg -> TODO()
            is KeywordMsg -> {
                if (this@generateExpression.pragmas.isNotEmpty()) {
                    replaceNameFromPragma(this@generateExpression)
                    emitFromPragma(this@generateExpression)
                    noPkgEmit(this@generateExpression)
                }
                generateSingleKeyword(0, receiver, this@generateExpression)
            }

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
    val statementsCode = codegenKt(statements, 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}
