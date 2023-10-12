package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.SomeTypeDeclaration
import frontend.parser.types.ast.TypeAST
import frontend.parser.types.ast.TypeFieldAST
import frontend.parser.types.ast.UnionDeclaration


fun SomeTypeDeclaration.generateTypeDeclaration(isUnionRoot: Boolean = false, root: UnionDeclaration? = null) =
    buildString {
        if (isUnionRoot) append("sealed ")
        append("class ")
        append(typeName)

        if (genericFields.isNotEmpty()) {
            append("<")
            genericFields.forEach({ append(", ") }) {
                append(it)
            }
            append(">")
        }


        append("(")
        // class Person (

        fun sas(it: TypeFieldAST, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {

            if (it.type == null) {
                it.token.compileError("arg must have type")
            }
            // TODO var or val?, maybe add  mut modifier

            val typeName = if (it.type is TypeAST.UserType && it.type.typeArgumentList.isNotEmpty()) {
                it.type.name + "<" + it.type.typeArgumentList.joinToString(", ") { it.name } + ">"
            } else it.type.name
            if (!rootFields) {
                append("var ")
            }
            append(it.name, ": ", typeName)
            if (fieldsCountMinus1 != i) {
                append(", ")
            }
        }

        // default fields
        fields.forEachIndexed { i, it ->
            sas(it, i, false, fields.count() - 1)
        }
        // class Person (var age: Int,
        // root fields
        if (root != null) {
            append(", ")
            root.fields.forEachIndexed { i, it ->
                sas(it, i, true, root.fields.count() - 1)
            }
        }


        append(")")
        // class Person (var age: Int, kek: String)

        if (root != null) {
            val w = root.fields.count() - 1

            append(" : ${root.typeName}(")
            root.fields.forEachIndexed { i, it ->
                append(it.name)
                if (w != i) {
                    append(", ")
                }
            }
            append(")")
        }

        // class Person (var age: Int, kek: String): Kek(kek)
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

sealed class Shape(val area: Int)
class Rectangle(val width: Int, val height: Int, area: Int) : Shape(area)

fun UnionDeclaration.generateUnionDeclaration() = buildString {
    val statement = this@generateUnionDeclaration

    append(statement.generateTypeDeclaration(true))
}
