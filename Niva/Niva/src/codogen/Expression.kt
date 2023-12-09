package codogen

import frontend.parser.types.ast.*
import frontend.typer.createEmptyKwConstructor

fun replaceKeywords(str: String) =
    when (str) {
        "do", "val", "var", "class", "in", "for" -> "`$str`"
        else -> str
    }

fun Expression.generateExpression(replaceLiteral: String? = null): String = buildString {

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
            is ExpressionInBrackets -> generateExpressionInBrackets()

            is MessageSend -> generateMessageCall()
            is IdentifierExpr -> if (isConstructor) {
                // prevent stack overflow, since keywordGenerate contains generate expression for receiver
                this@generateExpression.isConstructor = false
                keywordGenerate(
                    createEmptyKwConstructor(
                        this@generateExpression, this@generateExpression.type!!,
                        this@generateExpression.token
                    )
                )
            } else {
                if (names.count() == 1) {
                    replaceKeywords(replaceLiteral ?: name)
                } else
                    names.dropLast(1).joinToString(".") + "." + replaceKeywords(replaceLiteral ?: name)
            }

            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
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
