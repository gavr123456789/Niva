package codogen

import frontend.parser.types.ast.*
import frontend.util.addIndentationForEachString


fun generateKtStatement(statement: Statement, ident: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> statement.generateExpression()
            is VarDeclaration -> statement.generateVarDeclaration()

            is MessageDeclaration -> statement.generateMessageDeclaration()

            is TypeDeclaration -> statement.generateTypeDeclaration()

            is ReturnStatement -> {
                "return ${statement.expression.generateExpression()}"
            }

            is Assign -> "${statement.name} = ${statement.value.generateExpression()}"

            is AliasDeclaration -> TODO()


            is UnionDeclaration -> TODO()
            is TypeAST.InternalType -> TODO()
            is TypeAST.Lambda -> TODO()
            is TypeAST.UserType -> TODO()

            is UnionBranch -> TODO()

        }.addIndentationForEachString(ident)
    )
}





