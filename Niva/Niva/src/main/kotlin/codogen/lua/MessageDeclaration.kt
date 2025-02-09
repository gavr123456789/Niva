package main.codogen.lua

import main.frontend.parser.types.ast.*

fun MessageDeclaration.generateLuaMessageDeclaration(): String {
    val generator = LuaGenerator()
    return generator.generate(listOf(this))
}

fun ExtendDeclaration.generateLuaMessageDeclaration(): String {
    val generator = LuaGenerator()
    return generator.generate(listOf(this))
}

fun ManyConstructorDecl.generateLuaMessageDeclaration(): String {
    val generator = LuaGenerator()
    return generator.generate(listOf(this))
}
