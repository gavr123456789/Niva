package main.codogen

import main.frontend.parser.types.ast.MethodReference
import main.utils.capitalizeFirstLetter

fun MethodReference.generateMethodReference() = buildString {
    val type = forType.generateType()
    append("$type::")

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
