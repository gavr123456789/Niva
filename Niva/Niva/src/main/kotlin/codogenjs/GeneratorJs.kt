package main.codogenjs

import frontend.resolver.Package
import main.frontend.parser.types.ast.Statement

class GeneratorJs

fun codegenJs(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    val g = GeneratorJs()
    statements.forEachIndexed { i, st ->
        append(g.generateJsStatement(st, indent))
        if (i != statements.lastIndex) append('\n')
    }
}
