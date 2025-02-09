package main.codogen.lua

import main.frontend.parser.types.ast.*

class GeneratorLua {
//    companion object {
//        val LUA_IMPORTS = """
//            -- Standard Lua imports can be added here
//        """.trimIndent()
//    }
}

fun codegenLua(statements: List<Statement>, indent: Int = 0): String {
    if (statements.isEmpty()) {
        return ""
    }

    val generator = GeneratorLua()
    val code = statements.mapIndexed { index, stmt ->
        val stmtCode = generator.generateLuaStatement(stmt, indent)
        if (index < statements.size - 1) stmtCode + "\n" else stmtCode
    }.joinToString("")

    // Remove any trailing newlines
    return code.trimEnd()
}
