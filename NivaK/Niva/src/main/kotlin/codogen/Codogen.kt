package codogen

import frontend.parser.types.ast.*
import frontend.util.addIndentationForEachString

fun codogenKt(statements: List<Statement>, ident: Int = 0): String = buildString {
    statements.forEach {
        append(generateKtStatement(it, ident), "\n")
    }

}

fun generateKtStatement(statement: Statement, ident: Int): String = buildString {
    append(
        when (statement) {
            is MessageSend -> statement.generateMessageCall()
            is VarDeclaration -> statement.generateVarDeclaration()
            is IdentifierExpr -> TODO()

            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.FloatExpr -> statement.str
            is LiteralExpression.IntExpr -> statement.str
            is LiteralExpression.StringExpr -> "\"" + statement.str + "\""

            is MessageDeclaration -> statement.generateMessageDeclaration()

            is TypeDeclaration -> statement.generateTypeDeclaration()
            is Expression -> statement.generateExpression()
            is ReturnStatement -> {
                "return ${statement.expression.generateExpression()}"
            }

            is Assign -> "${statement.name} = ${statement.value.generateExpression()}"

            is AliasDeclaration -> TODO()


            is UnionDeclaration -> TODO()
            is TypeAST.InternalType -> TODO()
            is TypeAST.Lambda -> TODO()
            is TypeAST.UserType -> TODO()
        }.addIndentationForEachString(ident)
    )
}

fun ConstructorDeclaration.generateConstructorDeclaration() =
    this.msgDeclaration.generateMessageDeclaration(true)


fun MessageDeclaration.generateMessageDeclaration(isStatic: Boolean = false): String = buildString {
    append(
        when (this@generateMessageDeclaration) {
            is ConstructorDeclaration -> generateConstructorDeclaration()
            is MessageDeclarationUnary -> generateUnaryDeclaration(isStatic)
            is MessageDeclarationBinary -> generateBinaryDeclaration(isStatic)
            is MessageDeclarationKeyword -> generateKeywordDeclaration(isStatic)
        }
    )
}

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


typealias WhileIf = () -> Boolean

inline fun <T> WhileIf.whileTrue(x: () -> T) {
    while (this()) {
        x()
    }
}

inline fun <T> WhileIf.whileFalse(x: () -> T) {
    while (!this()) {
        x()
    }


}

fun ExpressionInBrackets.generateExpressionInBrackets() = buildString {
    append("(")
    val statementsCode = codogenKt(statements, 0).removeSuffix("\n")
    append(statementsCode)
    append(")")
}


private fun CodeBlock.generateCodeBlock() = buildString {
    // {x: Int, y: Int -> x + y}

    if (isSingle) {
        append(";")
    }

    append("{")

    // x: Int, ->
    inputList.forEach {
        append(it.name, ": ", it.type!!.name, ", ")
    }
    val isThereArgs = inputList.isNotEmpty()
    // generate single line lambda or not
    val statementsCode = if (statements.count() == 1) {
        append(if (isThereArgs) "-> " else "")
        codogenKt(statements, 0).removeSuffix("\n")
    } else {
        append(if (isThereArgs) "-> " else "", "\n")
        codogenKt(statements, 1)
    }
    append(statementsCode)



    append("}")
}



