package codogen

import frontend.parser.types.ast.TypeDeclaration

fun TypeDeclaration.generateTypeDeclaration() = buildString {
    append("class ")
    append(typeName)
    append("(")

    val c = fields.count() - 1
    fields.forEachIndexed { i, it ->
        if (it.type == null) {
            throw Exception("arg must have type")
        }
        // TODO var or val?, maybe add  mut modifier
        append("var ", it.name, ": ", it.type.name)
        if (c != i) append(", ")
    }
    // for static methods like constructor
    append(")", " { companion object }")
}
