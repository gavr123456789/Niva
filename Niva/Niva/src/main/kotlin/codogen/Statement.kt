package main.codogen

import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*
import main.utils.addIndentationForEachString


@Suppress("UnusedReceiverParameter")
fun GeneratorKt.generateKtStatement(statement: Statement, indent: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> statement.generateExpression()
            is VarDeclaration -> statement.generateVarDeclaration()

            is MessageDeclaration -> statement.generateMessageDeclaration()
//            is StaticBuilderDeclaration -> TODO()

            is ExtendDeclaration -> statement.messageDeclarations.joinToString("\n") { it.generateMessageDeclaration() }
            is ManyConstructorDecl -> statement.messageDeclarations.joinToString("\n") { it.generateMessageDeclaration() }

            is TypeDeclaration -> statement.generateTypeDeclaration()
            is TypeAliasDeclaration -> statement.generateTypeAlias()

            is ReturnStatement -> {
                val expr = statement.expression
                if (expr != null) {
                    "return (${expr.generateExpression()})"
                } else {
                    "return"
                }
            }

            is Assign ->
                "${statement.name} = ${statement.value.generateExpression()}"
            is DestructingAssign -> {
                statement.generateDestruction()
            }


            is UnionRootDeclaration -> {
                statement.generateTypeDeclaration(true)
            }
            is UnionBranchDeclaration -> {
                statement.generateTypeDeclaration(false)
            }


            is EnumDeclarationRoot -> statement.generateEnumDeclaration()
            is EnumBranch -> TODO()

            is TypeAST.InternalType -> TODO()
            is TypeAST.Lambda -> TODO()
            is TypeAST.UserType -> TODO()

            is NeedInfo -> createFakeToken().compileError("Compiler bug: u cant have ! expression inside code generation")

            is ErrorDomainDeclaration -> TODO()
        }.addIndentationForEachString(indent)
    )
}





