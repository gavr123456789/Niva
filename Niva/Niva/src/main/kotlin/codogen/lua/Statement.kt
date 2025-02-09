package main.codogen.lua

import main.frontend.parser.types.ast.*
import main.utils.addIndentationForEachString

fun GeneratorLua.generateLuaStatement(statement: Statement, indent: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> statement.generateLuaExpression()
            is VarDeclaration -> statement.generateLuaVarDeclaration()
            is DestructingAssign -> statement.generateLuaDestruction()
            is Assign -> statement.generateLuaAssign()

            is MessageDeclaration -> statement.generateLuaMessageDeclaration()
//            is StaticBuilderDeclaration -> TODO()

            is ExtendDeclaration -> statement.messageDeclarations.joinToString("\n") { it.generateLuaMessageDeclaration() }
            is ManyConstructorDecl -> statement.messageDeclarations.joinToString("\n") { it.generateLuaMessageDeclaration() }

            is TypeDeclaration -> statement.generateLuaTypeDeclaration()
            is TypeAliasDeclaration -> statement.generateLuaTypeAlias()
            is UnionRootDeclaration -> statement.generateLuaUnionDeclaration()

            is ReturnStatement -> {
                val expr = statement.expression
                if (expr != null) {
                    "return ${expr.generateLuaExpression()}"
                } else {
                    "return"
                }
            }

            else -> "-- TODO: Implement ${statement::class.simpleName} generation for Lua"
        }.addIndentationForEachString(indent)
    )
}
