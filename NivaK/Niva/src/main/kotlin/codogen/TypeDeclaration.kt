package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.TypeAST
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

    if (typeFields.isNotEmpty()) {
        append("<")
        typeFields.forEach({ append(", ") }) {
            append(it)
        }
        append(">")
    }


    append("(")
    // class Person (
    val c = fields.count() - 1
    fields.forEachIndexed { i, it ->
        if (it.type == null) {
            it.token.compileError("arg must have type")
        }
        // TODO var or val?, maybe add  mut modifier

        val typeName = if (it.type is TypeAST.UserType && it.type.typeArgumentList.isNotEmpty()) {
            it.type.name + "<" + it.type.typeArgumentList.joinToString(", ") { it.name } + ">"
        } else it.type.name
        append("var ", it.name, ": ", typeName)
        if (c != i) append(", ")
    }
    append(")")
    // class Person (age: Int)


    /// Override toString
    append(" {\n")
    append("\toverride fun toString(): String {\n")
    append("\t\treturn \"", typeName, " ")

    val toStringFields = fields.joinToString(" ") {
        it.name + ": " + "$" + it.name
    }

    append(toStringFields, "\"")
    // for static methods like constructor
    append(
        """
        
        }
        companion object
    }
    """.trimIndent()
    )
}
