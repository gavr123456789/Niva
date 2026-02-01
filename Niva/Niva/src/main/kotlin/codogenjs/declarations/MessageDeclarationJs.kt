package main.codogenjs

import main.codogen.operatorToString
import main.frontend.parser.types.ast.*

private fun buildDeclFuncNameJs(forType: frontend.resolver.Type, name: String): String {
    val recv = when (forType) {
        is frontend.resolver.Type.UserLike -> buildString {
            if (forType.pkg.isNotEmpty() && forType.pkg != "core" && forType.pkg != "common") {
                append(forType.pkg.replace('.', '_').replace("::", "_"), "_")
            }
            append(forType.emitName)
            if (forType.isMutable) append("__mut")
        }
        is frontend.resolver.Type.NullableType -> "Nullable"
        else -> forType.toJsMangledName()
    }
    val base = name
    return "${recv}__${base}"
}

fun MessageDeclaration.generateJsMessageDeclaration(isConstructor: Boolean): String = when (this) {
    is MessageDeclarationUnary -> generateUnaryJs(isConstructor)
    is MessageDeclarationBinary -> generateBinaryJs(isConstructor)
    is MessageDeclarationKeyword -> generateKeywordJs(isConstructor)
    is ConstructorDeclaration -> this.msgDeclaration.generateJsMessageDeclaration(true)
    is StaticBuilderDeclaration -> this.msgDeclaration.generateJsMessageDeclaration(isConstructor)
}

private fun MessageDeclaration.generateSingleExpression(
    fn: String,
    params: List<String>,
    doc: String = "",
    isConstructor: Boolean // so we do not generate extracting of this fields
): String {
    val rawBody = buildString {
        // implicit local variables for all fields like: let field = receiver.field
        val recvType = forType
        val userType = recvType as? frontend.resolver.Type.UserLike
        if (userType != null && userType.fields.isNotEmpty() && !isConstructor) {
            val uniqueFields = userType.fields.distinctBy { it.name } // union root and its branches can have general fields with same names
            append("// destruct this ${uniqueFields.joinToString { it.name }}\n")
            uniqueFields.forEach { field ->
                // If this function doesn't have param same as the field, to avoid name clashes
                if (params.all { it != field.name }) {
                    append("let ", field.name, " = _receiver" + ".", field.name, "\n")
                }
            }
        }

        // method body
        if (isSingleExpression && body.size == 1 && body[0] is Expression) {
            append("return (" + (body[0] as Expression).generateJsExpression() + ")")
        } else {
            val inner = codegenJs(body, 0)
            append(inner)
        }
    }

    return buildString {
        append(doc)
		append("export function ", fn, "(", params.joinToString(", ") { it.ifJsKeywordPrefix() }, ") {\n")
        if (rawBody.isNotBlank()) {
            append(rawBody.addIndentationForEachStringJs(1))
            if (rawBody.contains('\n')) {
                append('\n')
            }
        }
        append("}\n")
    }
}

private fun MessageDeclarationUnary.generateUnaryJs(isConstructor: Boolean): String {
    val fn = buildDeclFuncNameJs(forType!!, name)
    val params = if (isConstructor) emptyList() else listOf("_receiver")
    val doc = if (isConstructor) "/**\n */\n" else "/**\n * @param {${forType!!.name}} _receiver\n */\n"
    return generateSingleExpression(fn, params, doc, isConstructor)
}

private fun MessageDeclarationBinary.generateBinaryJs(isConstructor: Boolean): String {
    val argT = arg.type ?: return "/* unresolved arg type for $name */"
    val safeName = operatorToString(name, token)
    val fn = buildDeclFuncNameJs(forType!!, safeName)
    val params = if (isConstructor) listOf(arg.name()) else listOf("_receiver", arg.name())
    val doc = if (isConstructor) "/**\n * @param {${argT.name}} ${arg.name()}\n */\n" else "/**\n * @param {${forType!!.name}} _receiver\n * @param {${argT.name}} ${arg.name()}\n */\n"
    return generateSingleExpression(fn, params, doc, isConstructor)
}

private fun MessageDeclarationKeyword.generateKeywordJs(isConstructor: Boolean): String {
    val fn = buildDeclFuncNameJs(forType!!, name)
    val params = if (isConstructor) args.map { it.name() } else listOf("_receiver") + args.map { it.name() }
    
    val sb = StringBuilder()
    sb.append("/**\n")
    if (!isConstructor) {
        sb.append(" * @param {${forType!!.name}} _receiver\n")
    }
    args.forEach { arg ->
        sb.append(" * @param {${arg.type?.name ?: "?"}} ${arg.name()}\n")
    }
    sb.append(" */\n")

    return generateSingleExpression(fn, params, sb.toString(), isConstructor)
}
