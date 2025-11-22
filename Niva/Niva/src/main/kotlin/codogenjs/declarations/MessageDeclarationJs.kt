package main.codogenjs

import main.frontend.parser.types.ast.*

private fun buildDeclFuncNameJs(forType: frontend.resolver.Type, name: String, argTypes: List<frontend.resolver.Type>): String {
    val recv = forType.toJsMangledName()
    val base = name
    val suffix = if (argTypes.isNotEmpty()) argTypes.joinToString("__") { it.toJsMangledName() } else ""
    return if (suffix.isEmpty()) "${recv}__${base}" else "${recv}__${base}__${suffix}"
}

fun MessageDeclaration.generateJsMessageDeclaration(): String = when (this) {
    is MessageDeclarationUnary -> generateUnaryJs()
    is MessageDeclarationBinary -> generateBinaryJs()
    is MessageDeclarationKeyword -> generateKeywordJs()
    is ConstructorDeclaration -> this.msgDeclaration.generateJsMessageDeclaration()
    is StaticBuilderDeclaration -> this.msgDeclaration.generateJsMessageDeclaration()
}

private fun MessageDeclaration.generateSingleExpression(fn: String, params: List<String>): String {
    // Собираем тело функции без отступов, затем добавляем общий отступ в один уровень.
    val rawBody = buildString {
        // Неявные локальные переменные для всех полей типа: let field = receiver.field
        val recvType = forType
        val userType = recvType as? frontend.resolver.Type.UserLike
        if (userType != null && userType.fields.isNotEmpty()) {
            userType.fields.forEach { field ->
                append("let ", field.name, " = receiver.", field.name, "\n")
            }
        }

        // Основное тело метода
        if (isSingleExpression && body.size == 1 && body[0] is Expression) {
            append("return (" + (body[0] as Expression).generateJsExpression() + ")")
        } else {
            val inner = codegenJs(body, 0)
            append(inner)
        }
    }

    return buildString {
        append("function ", fn, "(", params.joinToString(", "), ") {\n")
        if (rawBody.isNotBlank()) {
            append(rawBody.addIndentationForEachStringJs(1))
            if (rawBody.contains('\n')) {
                append('\n')
            }
        }
        append("}\n")
    }
}

private fun MessageDeclarationUnary.generateUnaryJs(): String {
    val fn = buildDeclFuncNameJs(forType!!, name, emptyList())
    val params = listOf("receiver")
    return generateSingleExpression(fn, params)
}

private fun MessageDeclarationBinary.generateBinaryJs(): String {
    val argT = arg.type ?: return "/* unresolved arg type for $name */"
    val fn = buildDeclFuncNameJs(forType!!, name, listOf(argT))
    val params = listOf("receiver", arg.name())
    return generateSingleExpression(fn, params)
}

private fun MessageDeclarationKeyword.generateKeywordJs(): String {
    val types = args.mapNotNull { it.type }
    val fn = buildDeclFuncNameJs(forType!!, name, types)
    val params = listOf("receiver") + args.map { it.name() }
    return generateSingleExpression(fn, params)
}
