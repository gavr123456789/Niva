package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.*
import main.RED
import main.WHITE
import main.codogen.generateType

fun UnionDeclaration.collectAllGenericsFromBranches(): Set<String> {
    val genericsOfBranches = mutableSetOf<String>()
    branches.forEach {
        genericsOfBranches.addAll(it.genericFields)
    }
    return genericsOfBranches
}

fun SomeTypeDeclaration.generateTypeDeclaration(
    isUnionRoot: Boolean = false,
    root: UnionDeclaration? = null,
    isEnumRoot: Boolean = false,
    enumRoot: EnumDeclarationRoot? = null
) = buildString {
    if (isUnionRoot) append("sealed ")
    if (isEnumRoot) append("enum ")
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

    fun generateFieldArguments(it: TypeFieldAST, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {

        if (it.type == null) {
            it.token.compileError("Arg $WHITE${it.name}$RED must have type")
        }
        val typeName = it.type.generateType()
        if (!rootFields) {
            append("var ")
        }
        append(it.name, ": ", typeName)
        if (fieldsCountMinus1 != i) {
            append(", ")
        }
    }

    fun generateEnumArgs(enumRoot: EnumDeclarationRoot) {
        enumRoot.branches.forEach {
            append("    ", it.typeName)
            val hasFields = it.fieldsValues.isNotEmpty()
            if (hasFields) {
                append("(")
            }

            it.fieldsValues.forEach { field ->
                append(field.name, " = ", field.value.generateExpression(), ", ")
            }

            if (hasFields) {
                append("),")
            } else append(",")
            append("\n")
        }
    }

    // default fields
    fields.forEachIndexed { i, it ->
        generateFieldArguments(it, i, false, fields.count() - 1)
    }
    // class Person (var age: Int,
    // root fields
    if (root != null) {
        // comma after branch fields, before root fields
        if (fields.isNotEmpty()) {
            append(", ")
        }

        root.fields.forEachIndexed { i, it ->
            generateFieldArguments(it, i, true, root.fields.count() - 1)
        }
    }

    append(")")
    // class Person (var age: Int, kek: String)

    if (root != null) {
        val w = root.fields.count() - 1

        append(" : ${root.typeName}")

        val genericsOfTheBranch = genericFields.toSet()
        // for each generic that is not in genericsOfTheRoot we must use Nothing
        // if current branch does not has a generic param, but root has, then add Never

        val isThereGenericsSomewhere = genericFields.isNotEmpty() || root.genericFields.isNotEmpty()
        if (isThereGenericsSomewhere)
            append("<")

        val realGenerics = mutableListOf<String>()
        realGenerics.addAll(genericFields)

        root.genericFields.forEach {
            if (!genericsOfTheBranch.contains(it)) {
                //append("Nothing")
                realGenerics.add("Nothing")
            } else
                realGenerics.add(it)
        }

        append(realGenerics.toSortedSet().joinToString(", "))


        if (isThereGenericsSomewhere)
            append(">")

        append("(")
        root.fields.forEachIndexed { i, it ->
            append(it.name)
            if (w != i) {
                append(", ")
            }
        }
        append(")")
    }

    // class Person (var age: Int, kek: String): Kek(kek)
    append(" {\n")

    if (enumRoot != null) {
        generateEnumArgs(enumRoot)
        append("    ;\n")
    }


    /// Override toString
    if (enumRoot == null) {
        append("\toverride fun toString(): String {\n")
        append("\t\treturn \"", typeName)

        if (fields.isNotEmpty()) {
            append(" ")
        }

        val toStringFields = fields.joinToString(" ") {
            it.name + ": " + "$" + it.name
        }

        append(toStringFields, "\"")
        // for static methods like constructor
        append(
            """
    }
    companion object"""
        )
    }
    append("\n}\n")

}

fun EnumDeclarationRoot.generateEnumDeclaration() = buildString {
    val statement = this@generateEnumDeclaration
    append(statement.generateTypeDeclaration(isEnumRoot = true, enumRoot = this@generateEnumDeclaration))
}

fun UnionDeclaration.generateUnionDeclaration() = buildString {
    val statement = this@generateUnionDeclaration

    append(statement.generateTypeDeclaration(true))
}
