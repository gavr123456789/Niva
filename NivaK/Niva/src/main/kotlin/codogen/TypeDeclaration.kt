package codogen

import frontend.parser.types.ast.TypeDeclaration
import java.lang.Exception

fun TypeDeclaration.generateTypeDeclaration() = buildString {
    append("class ")
    append(typeName)
    append("(")

    val c = fields.count() - 1
    fields.forEachIndexed { i, it ->
        if (it.type == null) {
            throw Exception("arg must have type")
        }
        append("val ", it.name, ": ", it.type.name)
        if (c != i) append(", ")
    }

    append(")")

}
