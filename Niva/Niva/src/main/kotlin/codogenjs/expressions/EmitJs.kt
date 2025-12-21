package main.codogenjs

import frontend.parser.types.ast.KeyPragma
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.KeywordMsg

const val EMIT_JS_PRAGMA = "emitJs"

/**
 * Полная замена вызова сообщения на сырой JS-код из pragma `emitJs`.
 * Подстановки:
 *  - $0 — текущий ресивер (выражение)
 *  - $1..$N — аргументы keyword-сообщения (в порядке args)
 */
fun Message.tryEmitJsReplacement(currentReceiverExpr: String): String? {
    val pragma = pragmas.firstOrNull { it is KeyPragma && it.name == EMIT_JS_PRAGMA } as? KeyPragma ?: return null
    val value = pragma.value as? LiteralExpression.StringExpr
        ?: pragma.value.token.compileError("String literal expected for @emitJs pragma")

    val template = value.toString()
    val argMap = mutableMapOf<String, String>()
    argMap["0"] = currentReceiverExpr

    if (this is KeywordMsg) {
        this.args.forEachIndexed { i, arg ->
            argMap[(i + 1).toString()] = arg.keywordArg.generateJsExpression(withNullChecks = true)
        }
    }

    val pattern = Regex("\\$(\\d+)")
    return pattern.replace(template) { m ->
        val key = m.groupValues[1]
        argMap[key] ?: m.value
    }
}
