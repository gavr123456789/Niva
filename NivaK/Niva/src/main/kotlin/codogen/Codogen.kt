package codogen

import frontend.parser.types.ast.*
import frontend.util.addIndentationForEachString

fun codogenKt(statements: List<Statement>, ident: Int = 0): String = buildString {
    statements.forEach {
        append(getStringFromDeclaration(it, ident), "\n")
    }

}

fun getStringFromDeclaration(statement: Statement, ident: Int): String = buildString {
    append(
        when (statement) {
            is MessageSend -> statement.generateMessageCall()
            is VarDeclaration -> statement.generateVarDeclaration()
            is IdentifierExpr -> TODO()
            is LiteralExpression.FalseExpr -> TODO()
            is LiteralExpression.FloatExpr -> TODO()
            is LiteralExpression.IntExpr -> TODO()
            is LiteralExpression.StringExpr -> TODO()
            is LiteralExpression.TrueExpr -> TODO()
            is MessageDeclarationUnary -> statement.generateUnaryDeclaration()
            is MessageDeclarationBinary -> statement.generateBinaryDeclaration()
            is MessageDeclarationKeyword -> statement.generateKeywordDeclaration()
            is TypeDeclaration -> statement.generateTypeDeclaration()
            is Expression -> statement.generateExpression()
            else -> {
                TODO()
            }
        }.addIndentationForEachString(ident)
    )
}

fun Expression.generateExpression(): String {
    return when (this) {
        is MessageSend -> this.generateMessageCall()
        is IdentifierExpr -> this.str
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> "\"${this.str}\""

        is ListCollection -> TODO()
        is ControlFlow.IfExpression -> this.generateIfStatement()
        is ControlFlow.IfStatement -> this.generateIfStatement()
        is ControlFlow.SwitchExpression -> TODO()
        is ControlFlow.SwitchStatement -> TODO()

        // when receiver
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()


        is CodeBlock -> TODO()
    }

}


