package main.codogenjs

import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*

@Suppress("UnusedReceiverParameter")
fun GeneratorJs.generateJsStatement(statement: Statement, indent: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> statement.generateJsExpression()
            is VarDeclaration -> "let ${statement.name.ifJsKeywordPrefix()} = ${statement.value.generateJsExpression()}"

            is MessageDeclaration -> statement.generateJsMessageDeclaration()
            is ExtendDeclaration -> statement.messageDeclarations.joinToString("\n") { it.generateJsMessageDeclaration() }
            is ManyConstructorDecl -> statement.messageDeclarations.joinToString("\n") { it.generateJsMessageDeclaration() }

            is TypeDeclaration -> "// type ${statement.typeName} not emitted for JS yet"
            is TypeAliasDeclaration -> "// typealias ${statement.typeName} = ${statement.realTypeAST.name}"

            is ReturnStatement -> {
                val expr = statement.expression
                if (expr != null) {
                    "return (" + expr.generateJsExpression() + ")"
                } else {
                    "return"
                }
            }
            is Assign -> "${statement.name.ifJsKeywordPrefix()} = ${statement.value.generateJsExpression()}"
            is DestructingAssign -> "// destructuring is not implemented in JS codegen yet"

            is UnionRootDeclaration -> "// union ${statement.typeName} not emitted for JS yet"
            is UnionBranchDeclaration -> "// union branch ${statement.typeName} not emitted for JS yet"

            is EnumDeclarationRoot -> "// enum ${statement.typeName} not emitted for JS yet"
            is EnumBranch -> "// enum branch not emitted for JS yet"

            is TypeAST.InternalType -> "/* internal type stub */"
            is TypeAST.Lambda -> "/* lambda type stub */"
            is TypeAST.UserType -> "/* user type stub */"

            is NeedInfo -> createFakeToken().let { "// compiler bug placeholder" }

            is ErrorDomainDeclaration -> "// error domain is not emitted for JS yet"
        }.addIndentationForEachStringJs(indent)
    )
}
