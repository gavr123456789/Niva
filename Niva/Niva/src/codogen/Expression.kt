package codogen

import frontend.parser.types.ast.*

fun replaceKeywords(str: String) =
    when (str) {
        "do", "val", "var", "class", "in", "for" -> "`$str`"
        else -> str
    }

fun Expression.generateExpression(replaceLiteral: String? = null, withNullChecks: Boolean = false): String = buildString {

    if (isInfoRepl) {
        return@buildString
    }

    if (isInlineRepl) {
        append("inlineRepl(")
    }

    val keywordGenerate = { kw: KeywordMsg ->
        if (kw.pragmas.isNotEmpty()) {
            replaceNameFromPragma(kw)
            emitFromPragma(kw)
            noPkgEmit(kw)
        }
        generateSingleKeyword(0, kw.receiver, kw)
    }

    append(
        when (this@generateExpression) {
            is ExpressionInBrackets -> generateExpressionInBrackets(withNullChecks)

            is MessageSend -> generateMessageCall(withNullChecks)
            is IdentifierExpr ->
                if (names.count() == 1) {
                    replaceKeywords(replaceLiteral ?: name)
                } else
                    names.dropLast(1).joinToString(".") + "." + replaceKeywords(replaceLiteral ?: name)


            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.NullExpr -> "null"
            is LiteralExpression.FloatExpr -> str
            is LiteralExpression.DoubleExpr -> str
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
            is KeywordMsg -> keywordGenerate(this@generateExpression)

            is UnaryMsg -> TODO()


            is CodeBlock -> generateCodeBlock()
            is StaticBuilder -> TODO()
        }
    )

    if (isInlineRepl) {
        val fileAndLine = token.file.absolutePath + ":::" + token.line
        append(", \"\"\"$fileAndLine\"\"\", $inlineReplCounter)")
    }

}

fun ExpressionInBrackets.generateExpressionInBrackets(withNullChecks: Boolean = false) = buildString {
    if (withNullChecks) throw Exception("Compiler error, not realized nullable check with brackets")
    append("(")
    val statementsCode = codegenKt(statements, 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}
