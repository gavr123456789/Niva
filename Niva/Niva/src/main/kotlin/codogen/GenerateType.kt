package main.codogen

import main.frontend.parser.types.ast.TypeAST
import main.utils.isGeneric


fun TypeAST.generateType(
    realName: String?,
    generateGeneric: Boolean = true,
    customGenerics: Set<String>? = null
): String = buildString {
//    val x: (String, Int) -> Int = {}
     when (this@generateType) {
        is TypeAST.InternalType -> append(name)
        is TypeAST.UserType -> {
            append(realName ?: names.joinToString("."))
            if (generateGeneric && typeArgumentList.isNotEmpty()) {
                val (genericSingleLetters, genericNames) =
                    typeArgumentList.asSequence().map { it.name }.partition { it.isGeneric() }
                val genericsNames = genericNames + genericSingleLetters.toSet()
                append("<")
                append((genericsNames + (customGenerics ?: setOf())).joinToString(", ") { it })
                append(">")
            }
        }

        is TypeAST.Lambda -> {
            val realArgs = if (extensionOfType != null) {
                // fun sas(x: ^Int.(Int) -> String) =
                val kotlinExtType = extensionOfType.generateType(realName)
                append(kotlinExtType, ".")
                inputTypesList.drop(1)
            } else inputTypesList

            append("(")
            realArgs.forEach {
                append(it.generateType(realName), ",")
            }
            append(") -> ")

            append(returnType.generateType(realName))
        }
    }

    if (isNullable) append("?")
}
