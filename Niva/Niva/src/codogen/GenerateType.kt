package main.codogen

import frontend.parser.types.ast.TypeAST

fun TypeAST.generateType(generateGeneric: Boolean = true): String = buildString {
//    val x: (String, Int) -> Int = {}
     when (this@generateType) {
        is TypeAST.InternalType -> append(name)
        is TypeAST.UserType -> {
            append(names.joinToString("."))
            if (generateGeneric && typeArgumentList.isNotEmpty()) {
                append("<${typeArgumentList.joinToString(", ") { it.name }}>")
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
