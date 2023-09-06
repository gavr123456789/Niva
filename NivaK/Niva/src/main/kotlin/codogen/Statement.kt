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
            is IdentifierExpr -> statement.name

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





