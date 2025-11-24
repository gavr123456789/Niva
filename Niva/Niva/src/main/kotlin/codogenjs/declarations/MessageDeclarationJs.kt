package main.codogenjs

import main.frontend.parser.types.ast.*

private fun buildDeclFuncNameJs(forType: frontend.resolver.Type, name: String, argTypes: List<frontend.resolver.Type>): String {
    val recv = if (forType is frontend.resolver.Type.UserLike) {
        buildString {
            if (forType.pkg.isNotEmpty() && forType.pkg != "core" && forType.pkg != "common") {
                append(forType.pkg.replace('.', '_').replace("::", "_"), "_")
            }
            append(forType.emitName)
            // We don't append generic parameters here to avoid List__Int__count
            if (forType.isMutable) append("__mut")
        }
    } else {
        forType.toJsMangledName()
    }
    val base = name
    return "${recv}__${base}"
}

fun MessageDeclaration.generateJsMessageDeclaration(): String = when (this) {
    is MessageDeclarationUnary -> generateUnaryJs()
    is MessageDeclarationBinary -> generateBinaryJs()
    is MessageDeclarationKeyword -> generateKeywordJs()
    is ConstructorDeclaration -> this.msgDeclaration.generateJsMessageDeclaration()
    is StaticBuilderDeclaration -> this.msgDeclaration.generateJsMessageDeclaration()
}

private fun MessageDeclaration.generateSingleExpression(fn: String, params: List<String>, doc: String = ""): String {
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
        append(doc)
		// Все сгенерированные функции сообщений должны быть экспортируемыми
		append("export function ", fn, "(", params.joinToString(", "), ") {\n")
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
    val doc = "/**\n * @param {${forType!!.name}} receiver\n */\n"
    return generateSingleExpression(fn, params, doc)
}

private fun MessageDeclarationBinary.generateBinaryJs(): String {
    val argT = arg.type ?: return "/* unresolved arg type for $name */"
    val fn = buildDeclFuncNameJs(forType!!, name, listOf(argT))
    val params = listOf("receiver", arg.name())
    val doc = "/**\n * @param {${forType!!.name}} receiver\n * @param {${argT.name}} ${arg.name()}\n */\n"
    return generateSingleExpression(fn, params, doc)
}

private fun MessageDeclarationKeyword.generateKeywordJs(): String {
    val types = args.mapNotNull { it.type }
    val fn = buildDeclFuncNameJs(forType!!, name, types)
    val params = listOf("receiver") + args.map { it.name() }
    
    val sb = StringBuilder()
    sb.append("/**\n")
    sb.append(" * @param {${forType!!.name}} receiver\n")
    args.forEach { arg ->
        sb.append(" * @param {${arg.type?.name ?: "?"}} ${arg.name()}\n")
    }
    sb.append(" */\n")

    return generateSingleExpression(fn, params, sb.toString())
}
