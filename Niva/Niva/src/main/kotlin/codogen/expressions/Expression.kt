package main.codogen

import frontend.resolver.Type
import languageServer.generateAddDevDataFunCall
import main.frontend.parser.types.ast.*

fun Expression.generateExpression(replaceLiteral: String? = null, withNullChecks: Boolean = false, isArgument: Boolean = false): String = buildString {

    if (isInfoRepl) {
        return@buildString
    }

    if (isInlineRepl) {
        append("NivaDevModeDB.db.add((")
    }
    
    fun keywordGenerate(kw: KeywordMsg): String {
        evalPragmas(kw)
        return generateSingleKeyword(0, kw.receiver, kw.kind == KeywordLikeType.Constructor, kw)
    }

    fun unaryGenerate(unaryMsg: UnaryMsg): String {
        evalPragmas(unaryMsg)
        return generateSingleUnary(1, unaryMsg.receiver, unaryMsg)
    }
    fun binaryGenerate(binary: BinaryMsg): String {
        evalPragmas(binary)
        return generateSingleBinary(1, binary.receiver, binary)
    }
    append(
        when (this@generateExpression) {
            is ExpressionInBrackets -> generateExpressionInBrackets(withNullChecks)

            is MessageSend ->
                generateMessageCall(withNullChecks)
            is IdentifierExpr ->
                if (names.count() == 1) {
                    (replaceLiteral ?: name).ifKtKeywordAddBackTicks()
                } else
                    names.dropLast(1).joinToString(".") + "." + (replaceLiteral ?: name).ifKtKeywordAddBackTicks()


            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.NullExpr -> "null"
            is LiteralExpression.FloatExpr -> str
            is LiteralExpression.DoubleExpr -> str
            is LiteralExpression.IntExpr -> str
            is LiteralExpression.StringExpr -> str
            is LiteralExpression.CharExpr -> str
            is LiteralExpression.UnitExpr -> "Unit"
            is DotReceiver -> "this"

            is ListCollection -> generateList()
            is SetCollection -> generateSet()
            is MapCollection -> generateMap()
            is ControlFlow.If -> generateIf()
            is ControlFlow.Switch -> generateSwitch()

            // when message is receiver
            is BinaryMsg -> binaryGenerate(this@generateExpression)
            is KeywordMsg -> keywordGenerate(this@generateExpression)
            is UnaryMsg -> unaryGenerate(this@generateExpression)

            // we dont need to generate types if this is arg list.forEach: {it ~~: Int~~ -> ...}
            is CodeBlock -> generateCodeBlock(
                withTypeDeclaration = !isArgument,
                putArgListInBrackets = (type as? Type.Lambda)?.specialFlagForLambdaWithDestruct == true
            )

            is StaticBuilder -> generateBuilderCall(this@generateExpression)
            is MethodReference -> generateMethodReference()
        }
    )

    if (isInlineRepl) {
        generateAddDevDataFunCall(this, token)
    }

}

fun ExpressionInBrackets.generateExpressionInBrackets(withNullChecks: Boolean = false) = buildString {
    if (withNullChecks) throw Exception("Compiler error, not realized nullable check with brackets")
    append("(")
    val statementsCode = codegenKt(listOf(expr), 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}
