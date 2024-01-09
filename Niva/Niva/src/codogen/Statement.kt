package codogen

import frontend.parser.types.ast.*
import frontend.util.addIndentationForEachString


@Suppress("UnusedReceiverParameter")
fun GeneratorKt.generateKtStatement(statement: Statement, ident: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> statement.generateExpression()
            is VarDeclaration -> statement.generateVarDeclaration()

            is MessageDeclaration -> statement.generateMessageDeclaration()
            is ExtendDeclaration -> statement.messageDeclarations.joinToString("\n") { it.generateMessageDeclaration() }

            is TypeDeclaration -> statement.generateTypeDeclaration()

            is ReturnStatement -> {
                val expr = statement.expression
                if (expr != null) {
                    "return ${expr.generateExpression()}"
                } else {
                    "return"
                }
            }

            is Assign -> "${statement.name} = ${statement.value.generateExpression()}"

            is AliasDeclaration -> TODO()


            is UnionDeclaration -> statement.generateUnionDeclaration()
            is StaticBuilderDeclaration -> TODO()


            is EnumDeclarationRoot -> statement.generateEnumDeclaration()
            is EnumBranch -> TODO()

            is TypeAST.InternalType -> TODO()
            is TypeAST.Lambda -> TODO()
            is TypeAST.UserType -> TODO()

            is UnionBranch -> {
                statement.generateTypeDeclaration(false, statement.root)
            }


        }.addIndentationForEachString(ident)
    )
}





