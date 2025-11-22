package main.codogenjs

import main.frontend.parser.types.ast.*
import java.lang.StringBuilder

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
    val bodyCode = if (isSingleExpression && body.size == 1 && body[0] is Expression) {
        "return (" + (body[0] as Expression).generateJsExpression() + ")"
    } else codegenJs(body, 1)

     return  buildString {
         append("function ", fn, "(", params.joinToString(", "), ") {\n")
         append(bodyCode.addIndentationForEachStringJs(1))
         if (!(isSingleExpression && body.size == 1 && body[0] is Expression)) append('\n')
         append("}")
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
