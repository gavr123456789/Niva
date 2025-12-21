package main.codogenjs

import frontend.parser.types.ast.KeyPragma
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.Message

const val RENAME_JS_PRAGMA = "renameJs"

/**
 * Получение переименованного имени функции из pragma `rename`.
 * В отличие от emitJs, rename заменяет только имя функции, а не весь вызов.
 * Возвращает новое имя функции или null, если прагмы нет.
 */
fun Message.tryGetRenamedFunctionName(): String? {
    val pragma = pragmas.firstOrNull { it is KeyPragma && it.name == RENAME_JS_PRAGMA } as? KeyPragma ?: return null
    val value = pragma.value as? LiteralExpression.StringExpr
        ?: pragma.value.token.compileError("String literal expected for @rename pragma")
    
    return value.toString()
}
