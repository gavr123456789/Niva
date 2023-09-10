package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.TypeDeclaration

class Sas(val x: String) {
    override fun toString(): String {
        return "Sas x: $x"
    }

    companion object
}

fun TypeDeclaration.generateTypeDeclaration() = buildString {
    append("class ")
    append(typeName)
    append("(")

    val c = fields.count() - 1
    fields.forEachIndexed { i, it ->
        if (it.type == null) {
            it.token.compileError("arg must have type")
        }
        // TODO var or val?, maybe add  mut modifier
        append("var ", it.name, ": ", it.type.name)
        if (c != i) append(", ")
    }
    // for static methods like constructor
    append(") {\n") // " { companion object }"
    append("override fun toString(): String {\n")
    //
    append("        return \"", typeName, " ")

    val q = fields.map {
        it.name + ": " + "$" + it.name
    }.joinToString(" ")

    append(q)

    append("\"")

    //
    append(
        """
        
        }
        companion object
    }
    """.trimIndent()
    )
}
