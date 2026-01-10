package main.codogenjs

import frontend.resolver.Package
import main.frontend.parser.types.ast.Statement

class GeneratorJs

/**
 * Глобальный, но очень простой контекст JS-кодогена.
 * Нужен, чтобы знать «текущий» пакет при генерации вызовов
 * и решать, нужен ли префикс pkgAlias. перед функциями/класcами.
 */
object JsCodegenContext {
    var currentPackage: Package? = null
    // Набор типов, которые уже были сгенерированы как ветки с isRoot = true
    val generatedAsIsRootBranches = mutableSetOf<String>()
    // Source map builder для текущей генерации (null если source maps отключены)
    var sourceMapBuilder: SourceMapBuilder? = null
}

fun codegenJs(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    // Проталкиваем информацию о текущем пакете во все вложенные вызовы
    val prev = JsCodegenContext.currentPackage
    if (pkg != null) JsCodegenContext.currentPackage = pkg
    // Очищаем набор сгенерированных типов для нового вызова кодогенерации
    JsCodegenContext.generatedAsIsRootBranches.clear()
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
