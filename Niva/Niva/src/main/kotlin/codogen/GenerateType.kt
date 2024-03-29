package main.codogen

import main.frontend.parser.types.ast.TypeAST


fun TypeAST.generateType(generateGeneric: Boolean = true, customGenerics: Set<String>? = null): String = buildString {
//    val x: (String, Int) -> Int = {}
     when (this@generateType) {
        is TypeAST.InternalType -> append(name)
        is TypeAST.UserType -> {
            append(names.joinToString("."))
            if (generateGeneric && typeArgumentList.isNotEmpty()) {
                val genericsNames = typeArgumentList.map { it.name }.toSet()
                append("<")
                append((genericsNames + (customGenerics ?: setOf())).joinToString(", ") { it })
                append(">")
            }
        }

        is TypeAST.Lambda -> {
            val realArgs = if (extensionOfType != null) {
                // fun sas(x: ^Int.(Int) -> String) =
                val kotlinExtType = extensionOfType.generateType()
                append(kotlinExtType, ".")
                inputTypesList.drop(1)
            } else inputTypesList

            append("(")
            realArgs.forEach {
                append(it.generateType(), ",")
            }
            append(") -> ")

            append(returnType.generateType())
        }
    }

    if (isNullable) append("?")
}
