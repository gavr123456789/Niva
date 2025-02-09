package main.codogen.lua

import main.frontend.parser.types.ast.*

fun CodeBlock.generateLuaCodeBlock(): String = buildString {
    if (isStatement) {
        // For statement blocks (if/else, etc.), just generate the statements
        append("do\n")
        append(codegenLua(statements, 2))
        append("\nend")
    } else {
        // For lambda expressions, create a function
        append("function(")
        if (inputList.isNotEmpty()) {
            append(inputList.joinToString(", ") { it.name })
        }
        append(")\n")

        if (isSingle && statements.size == 1) {
            // For single expression lambdas, just return the expression
            val stmt = statements.first()
            if (stmt is Expression) {
                append("  return ${stmt.generateLuaExpression()}\n")
            } else {
                append(codegenLua(statements, 2))
            }
        } else {
            append(codegenLua(statements, 2))
        }
        append("end")
    }
}

