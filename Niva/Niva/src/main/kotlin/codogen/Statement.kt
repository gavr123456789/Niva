package main.codogen

import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.util.addIndentationForEachString
import main.frontend.util.createFakeToken


@Suppress("UnusedReceiverParameter")
fun GeneratorKt.generateKtStatement(statement: Statement, indent: Int): String = buildString {
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

            is NeedInfo -> createFakeToken().compileError("Compiler bug: u cant have ! expression inside code generation")


        }.addIndentationForEachString(indent)
    )
}





