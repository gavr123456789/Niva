package main.codogen

import frontend.resolver.Type
import main.utils.RED
import main.utils.WHITE
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.frontend.parser.types.ast.TypeAliasDeclaration
import main.frontend.parser.types.ast.TypeFieldAST
import main.frontend.parser.types.ast.UnionRootDeclaration

fun UnionRootDeclaration.collectAllGenericsFromBranches(): Set<String> {
    val genericsOfBranches = mutableSetOf<String>()
    branches.forEach {
        genericsOfBranches.addAll(it.genericFields)
    }
    return genericsOfBranches
}

fun TypeAliasDeclaration.generateTypeAlias() = buildString {
    append("typealias ", typeName, " = ")
    val ktType = realTypeAST.generateType()
    append(ktType)
}

fun SomeTypeDeclaration.generateTypeDeclaration(
    isUnionRoot: Boolean = false,
    isEnumRoot: Boolean = false,
    enumRoot: EnumDeclarationRoot? = null
) = buildString {
    appendPragmas(pragmas, this)
    val receiverType = receiver!!
    if (isUnionRoot) append("sealed ")
    if (isEnumRoot) append("enum ")
    append("class ")
    append(receiverType.toKotlinString(false))

//    if (genericFields.isNotEmpty()) {
//        append("<")
//        genericFields.toSet().forEach({ append(", ") }) {
//            append(it)
//        }
//        append(">")
//    }

    append("(")
    // class Person (^ arg: Type

    fun generateFieldArguments(it: TypeFieldAST, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {
        if (it.typeAST == null) {
            it.token.compileError("Arg $WHITE${it.name}$RED must have type")
        }

        if (!rootFields) {
            append("var ")
        }

        val typeName = it.type!!.toKotlinString(true)
        append(it.name, ": ", typeName)
        if (fieldsCountMinus1 != i) {
            append(", ")
        }
    }
    fun generateFieldArguments2(fieldName: String, type: Type, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {


        if (!rootFields) {
            append("var ")
        }

        val typeName = type.toKotlinString(true)
        append(fieldName, ": ", typeName)
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
    if (receiverType is Type.UnionRootType) {
        if (receiverType.fields.isNotEmpty()) {
            // comma after branch fields, before root fields
            append(", ")
        }
        receiverType.fields.forEachIndexed { i, it ->
            generateFieldArguments2(it.name, it.type, i, true, receiverType.fields.count() - 1)
        }
    }

    append(")")
    // class Person (var age: Int, kek: String)^

    // add inheritance
    if (receiverType.parent != null) {
        val currentType = receiverType as Type.UserLike
        val root2 = receiverType.parent as Type.UserLike
        val rootGenericFields = root2.typeArgumentList.map { it.name }
        val genericsOfTheBranch = currentType.typeArgumentList.map { it.name }.toSet()

        append(" : ${root2.name}")

        // for each generic that is not in genericsOfTheRoot we must use Nothing
        // if current branch does not have a generic param, but root has, then add Never
        val isThereGenericsSomewhere = genericsOfTheBranch.isNotEmpty() || rootGenericFields.isNotEmpty()
        if (isThereGenericsSomewhere)
            append("<")

        val realGenerics = mutableListOf<String>()
        realGenerics.addAll(genericsOfTheBranch)

        // replacing all missing generics of current branch, that root have, to Nothing
        rootGenericFields.forEach {
            if (!genericsOfTheBranch.contains(it)) {
                realGenerics.add("Nothing")
            } else
                realGenerics.add(it)
        }

        append(realGenerics.toSortedSet().joinToString(", "))


        if (isThereGenericsSomewhere)
            append(">")

        append("(")
        // this is Duplicate of generating fields from UserType
        val w = root2.fields.count() - 1
        root2.fields.forEachIndexed { i, it ->
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


