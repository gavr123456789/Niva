package main.codogen

import main.frontend.parser.types.ast.TypeAST


fun TypeAST.generateType(generateGeneric: Boolean = true, customGenerics: Set<String>? = null): String = buildString {
//    val x: (String, Int) -> Int = {}
     when (this@generateType) {
        is TypeAST.InternalType -> append(name)
        is TypeAST.UserType -> {
            append(names.joinToString("."))
            if (generateGeneric && typeArgumentList.isNotEmpty()) {
                val genericsNames = typeArgumentList.map { it.name }
                append("<")
                append((genericsNames + (customGenerics ?: setOf())).joinToString(", ") { it })
                append(">")
            }
        }

        is TypeAST.Lambda -> {
                append("(")
                inputTypesList.forEach {
                    append(it.generateType(), ",")
                }
                append(") -> ")

                append(returnType.generateType())
        }
    }

    if (isNullable) append("?")
}
