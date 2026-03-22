package main.codogen

import main.frontend.parser.types.ast.TypeAST


fun replaceCollectionWithMutable(name: String) = when(name) {
    "List" -> "MutableList"
    "Set" -> "MutableSet"
    "Map" -> "MutableMap"
    else -> name
}


fun TypeAST.generateType(
    realName: String?,
    generateGeneric: Boolean = true,
    customGenerics: Set<String>? = null
): String = buildString {
//    val x: (String, Int) -> Int = {}
     when (this@generateType) {
        is TypeAST.InternalType -> append(name)
        is TypeAST.UserType -> {
            val baseName = realName ?: names.joinToString(".")
            val finalName = if (isMutable) replaceCollectionWithMutable(baseName) else baseName
            append(finalName)
            if (generateGeneric && typeArgumentList.isNotEmpty()) {
                append("<")
                val renderedArgs = typeArgumentList.asSequence().map { it.generateType(null) }.toMutableList()
                if (customGenerics != null) {
                    renderedArgs.addAll(customGenerics)
                }
                append(renderedArgs.joinToString(", ") { it })
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
