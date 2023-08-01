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
            is ReturnStatement -> {
                "return ${statement.expression.generateExpression()}"
            }

            else -> {
                TODO()
            }
        }.addIndentationForEachString(ident)
    )
}

fun Expression.generateExpression(): String {
    return when (this) {
        is MessageSend -> this.generateMessageCall()
        is IdentifierExpr -> this.name
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> "\"${this.str}\""

        is ListCollection -> TODO()
        is ControlFlow.IfExpression -> this.generateIf()
        is ControlFlow.IfStatement -> this.generateIf()
        is ControlFlow.SwitchExpression -> this.generateSwitch()
        is ControlFlow.SwitchStatement -> this.generateSwitch()

        // when message is receiver
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()


        is CodeBlock ->
            // [x::Int, y::Int -> x + y] == {x: Int, y: Int -> x + y}
            this.generateCodeBlock()


        // message like
//        is ConstructorMsg -> TODO()
//        is SetterMsg -> TODO()
//        is GetterMsg -> TODO()
    }

}

private fun CodeBlock.generateCodeBlock() = buildString {
    // {x: Int, y: Int -> x + y}
    
    if (isSingle) {
        append(";")
    }

    append("{")


    inputList.forEach {
        append(it.name, ": ", it.type!!.name, ", ")
    }

    append("-> \n")
    val statementsCode = codogenKt(statements, 1)
    append(statementsCode)


    append("}")
}



