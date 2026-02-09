package main.codogenjs

import frontend.resolver.Package
import main.frontend.parser.types.ast.Statement

class GeneratorJs

object JsCodegenContext {
    var currentPackage: Package? = null
    // set of types already generated as isRoot branches
    val generatedAsIsRootBranches = mutableSetOf<String>()
    // union root classes already emitted (including roots generated via branches)
    val generatedUnionRoots = mutableSetOf<String>()
    // Source map builder for the current generation (null if source maps are disabled)
    var sourceMapBuilder: SourceMapBuilder? = null
}

fun codegenJs(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    // push information about the current package into all nested calls
    val prev = JsCodegenContext.currentPackage
    if (pkg != null) JsCodegenContext.currentPackage = pkg
    // clear set of generated types for a new code generation call
    JsCodegenContext.generatedAsIsRootBranches.clear()
    JsCodegenContext.generatedUnionRoots.clear()
    try {
        val g = GeneratorJs()
        statements.forEachIndexed { i, st ->
            append(g.generateJsStatement(st, indent))
            if (i != statements.lastIndex) append('\n')
        }
    } finally {
        JsCodegenContext.currentPackage = prev
    }
}
