package main.codogen.lua

import main.frontend.parser.types.ast.*

fun VarDeclaration.generateLuaVarDeclaration(): String = buildString {
    // In Lua, all variables are mutable and local by default
    append("local ")
    append(name)
    append(" = ")
    append(value.generateLuaExpression())
}

fun DestructingAssign.generateLuaDestruction(): String = buildString {
    // In Lua, we'll create multiple local variables
    val valueCode = value.generateLuaExpression()
    append("local ")
    append(names.joinToString(", ") { it.name })
    append(" = ")
    
    // If the value is a function call or complex expression, store it in a temporary variable
    if (value !is Primary) {
        append("(function()\n")
        append("  local _temp = $valueCode\n")
        append("  return ")
        append(names.mapIndexed { index, _ -> "_temp[$index + 1]" }.joinToString(", "))
        append("\nend)()")
    } else {
        // For simple values, unpack directly
        append(names.mapIndexed { index, _ -> "$valueCode[$index + 1]" }.joinToString(", "))
    }
}

fun Assign.generateLuaAssign(): String = buildString {
    append(name)
    append(" = ")
    append(value.generateLuaExpression())
}