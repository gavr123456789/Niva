package main.codogen

import main.frontend.meta.compileError
import main.frontend.parser.types.ast.MethodReference
import main.utils.capitalizeFirstLetter

fun MethodReference.generateMethodReference() = buildString {
    val getType = { forIdentifier.type?.toKotlinString(true) ?: token.compileError("Bug method reference unresolved") }
    val typeOrIdent = if(forIdentifier.isType) getType() else forIdentifier.name

    append("$typeOrIdent::")

    // String::funcName
    when (this@generateMethodReference) {
        is MethodReference.Unary -> {
            append(name)
        }
        is MethodReference.Binary -> {
            append(operatorToString(name))
        }
        is MethodReference.Keyword -> {
            val methodName = keys.first() + keys.drop(1).joinToString("") { it.capitalizeFirstLetter() }
            append(methodName)
        }
    }
}
