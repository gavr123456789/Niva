package main.codogen

import codogen.declarations.generateDynamicConverters5
import codogen.declarations.generateDynamicForUnionRoot
import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.Package
import frontend.resolver.Type
import frontend.resolver.unpackNull
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMethod
import main.utils.CYAN
import main.utils.RESET
import main.utils.YEL
import main.utils.isGeneric


fun SomeTypeDeclaration.generateTypeDeclaration(
    isUnionRoot: Boolean = false,
    isEnumRoot: Boolean = false,
    enumRoot: EnumDeclarationRoot? = null
) = buildString {
    appendPragmas(pragmas, this)
    val receiverType = receiver!!
    if (isUnionRoot)
        append("sealed ")
    if (isEnumRoot) append("enum ")
    append("class ")
    append(receiverType.toKotlinString(false))
    val addGenericsFromParent = {
        val parent = receiverType.parent
        if (this.last() != '>' && parent is Type.UserLike && parent.typeArgumentList.isNotEmpty()) {
            append("<")
            append(
                parent.typeArgumentList.asSequence().map { it.toKotlinString(true) }.toSortedSet().joinToString(", ")
            )
            append(">")
        }
    }
    addGenericsFromParent()

    append("(")
    // class Person (^ arg: Type


    // when it's a union included in other union, fields of the root and current type can overlap
    val setOfAlreadyAddedArgs = mutableSetOf<String>()

    fun generateFieldArgument2(fieldName: String, type: Type, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {
        if (fieldName in setOfAlreadyAddedArgs)
            return
        else
            setOfAlreadyAddedArgs.add(fieldName)

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

    // filter fields from root fields
    val root2 = receiverType.parent as? Type.UserLike
    val (nonRootFields, overlappedRootFields) = if (root2 != null) {
        val rootFields = root2.fields.map { it.name }.toSet()
        fields.partition { it.name !in rootFields }
    } else Pair(fields, emptyList())

    nonRootFields.forEachIndexed { i, it ->
        generateFieldArgument2(
            it.name,
            it.type ?: it.token.compileError("ct error $it has no type"),
            i,
            false,
            fields.count() - 1
        )
    }
    overlappedRootFields.forEachIndexed { i, it ->
        generateFieldArgument2(
            it.name,
            it.type ?: it.token.compileError("ct error $it has no type"),
            i,
            true,
            fields.count() - 1
        )
    }
    // class Person (var age: Int,

    // add fields of the root
    if (receiverType is Type.UnionBranchType) {
        if (receiverType.root.fields.isNotEmpty() && fields.isNotEmpty()) {
            // comma after branch fields, before root fields
            append(", ")
        }
        receiverType.root.fields.forEachIndexed { i, it ->
            generateFieldArgument2(it.name, it.type, i, true, receiverType.root.fields.count() - 1)
        }
    }

    append(")")
    // class Person (var age: Int, kek: String)^

    // add inheritance
    if (root2 != null) {
        val rootGenericFields = root2.typeArgumentList.map { it.name }

        append(" : ${root2.name}")

        // for each generic that is not in genericsOfTheRoot we must use Nothing
        // if current branch does not have a generic param, but root has, then add Never
        val isThereGenericsSomewhere = rootGenericFields.isNotEmpty() //|| genericsOfTheBranch.isNotEmpty()
        if (isThereGenericsSomewhere)
            append("<")

//        val realGenerics = mutableListOf<String>()
//        realGenerics.addAll(genericsOfTheBranch)

        // replacing all missing generics of current branch, that root have, to Nothing
//        rootGenericFields.forEach {
//            if (!genericsOfTheBranch.contains(it)) {
//                // NOT REPLACING IT, since then we need to support out in params too
////                realGenerics.add("Nothing")
//                realGenerics.add(it)
//            } else
//                realGenerics.add(it)
//        }

        append(rootGenericFields.toSortedSet().joinToString(", "))


        if (isThereGenericsSomewhere)
            append(">")

        // class Person (var age: Int, kek: String) : Human<...>^(kek)
        append("(")
        // this is Duplicate of generating fields from UserTypeУ
        val w = root2.fields.count() - 1
        root2.fields.map { it.name }.toSet().forEachIndexed { i, it ->
            append("$it = $it")
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
        generateToStringOverride(this@generateTypeDeclaration, receiverType)
    }

    // to\from Dynamic
    if (receiverType is Type.UserLike && receiverType !is Type.EnumRootType && receiverType !is Type.EnumBranchType) {
        if (receiverType !is Type.UnionRootType)
            generateDynamicConverters5(this)
        else {
            generateDynamicForUnionRoot(this, receiverType)
        }
    }

    if (receiverType !is Type.EnumRootType) {
        appendLine("    }")
    }
    append("\n}\n")
}


private fun StringBuilder.generateToStringOverride(
    declaration: SomeTypeDeclaration,
    receiverType: Type
) {
    append("\t" + "override fun toString(): String")

    val toStringMethod =
        findAnyMethod(receiverType, "toString", Package(receiverType.pkg), MessageDeclarationType.Unary)
    
    if (toStringMethod != null && toStringMethod.declaration != null && toStringMethod.declaration.body.isNotEmpty()) {
        // Custom toString implementation exists
        val returnTypeName = toStringMethod.declaration.returnType?.name
        if (returnTypeName != "String") {
            toStringMethod.declaration.token.compileError(
                "${CYAN}toString$RESET methods should return ${YEL}String$RESET but it returns ${CYAN}$returnTypeName$RESET"
            )
        }
        
        val sb = StringBuilder()
        generateBody(toStringMethod.declaration, sb)
        append(sb)
        appendLine("\n    companion object {")
    } else {
        // Generate default toString implementation
        generateDefaultToString(declaration, receiverType)
    }
}

private fun StringBuilder.generateDefaultToString(
    declaration: SomeTypeDeclaration,
    receiverType: Type
) {
    append(" {\n")
    append("\t\treturn \"\"\"\n")
    
    val fields: List<TypeFieldAST> = declaration.collectFields()
    val typeName = receiverType.name

    append(typeName)
    if (fields.isNotEmpty()) {
        append(" ")
    }

    val addQuotes = { it: TypeFieldAST ->
        if (it.type?.name == "String")
            "\"$${it.name}\""
        else "$${it.name}"
    }
    
    val generateSimpleField = { it: TypeFieldAST ->
        "    " + it.name + ": " + addQuotes(it)
    }

    val generateComplexField = { it: TypeFieldAST ->
        "    ${it.name}: (\n" +
            "\${${it.name}.toString().prependIndent(\"        \")}\n" +
            "    )"
    }

    val toStringFields = generateFields(fields, generateSimpleField, generateComplexField)
    
    append(toStringFields, "\"\"\"")
    append(
        """
    }
    companion object {
"""
    )
}

private fun generateFields(
    fields: List<TypeFieldAST>,
    generateSimpleField: (TypeFieldAST) -> String,
    generateComplexField: (TypeFieldAST) -> String
): String = "\n" + fields.joinToString("\n") {
    val type = it.type?.unpackNull()
    when (type) {
        is Type.EnumBranchType -> generateSimpleField(it)
        is Type.EnumRootType -> generateSimpleField(it)

        is Type.UserLike -> generateComplexField(it)
        is Type.InternalType -> generateSimpleField(it)

        is Type.Lambda -> "    " + it.name + ": " + it.type.toString()

        is Type.NullableType -> if (type.unpackNull() is Type.InternalType)
            generateSimpleField(it)
        else
            generateComplexField(it)

        is Type.UnresolvedType -> it.token.compileError("Unresolved type $type of $it")
        null -> it.token.compileError("type of $it is null")
    }
}


fun SomeTypeDeclaration.collectFields(): List<TypeFieldAST> {
    val result: MutableList<TypeFieldAST> = mutableListOf()
    if (this is UnionBranchDeclaration) {
        result.addAll(this.root.collectFields())
    }
    result.addAll(fields)
    return result.toList()
}

fun EnumDeclarationRoot.generateEnumDeclaration() = buildString {
    val statement = this@generateEnumDeclaration
    append(statement.generateTypeDeclaration(isEnumRoot = true, enumRoot = this@generateEnumDeclaration))
}


fun UnionRootDeclaration.collectAllGenericsFromBranches(): Set<String> {
    val genericsOfBranches = mutableSetOf<String>()
    branches.forEach {
        genericsOfBranches.addAll(it.genericFields)
    }
    return genericsOfBranches
}

fun TypeAliasDeclaration.generateTypeAlias() = buildString {
    // typealias MatchBlock<I, R> = (I,) -> R
    append("typealias ", typeName)
    val realType = this@generateTypeAlias.realType!!
    if (realType is Type.UserLike && realType.typeArgumentList.isNotEmpty()) {
        val unknownGenericParams = realType.typeArgumentList.filter {it.name.isGeneric()}
        if (unknownGenericParams.isNotEmpty()) {
            append("<")
            append(unknownGenericParams.map { it.name }.toSet().joinToString(", ") { it })
            append(">")
        }

    }
    if (realType is Type.Lambda) {
        val getAllGenerics = {
            val result = realType.args.asSequence().map { it.type }.filterIsInstance<Type.UnknownGenericType>()
            if (realType.returnType is Type.UnknownGenericType) {
                result + realType.returnType
            } else {
                result
            }
        }
        val genericsList = getAllGenerics()
        if (genericsList.toList().isNotEmpty()) {
            append("<")
            append(genericsList.joinToString(", ") { it.name })
            append(">")
        }
    }
    append(" = ")
    val ktType = realTypeAST.generateType((realType as? Type.UserLike)?.name)
    append(ktType)
}

fun DestructingAssign.generateDestruction(): String {
    val tempName = "temp_destruct_assign_${this.names.joinToString("_")}"
    val temp = "val $tempName = ${this.value.generateExpression()}\n"
    return temp + this.names.joinToString("\n") {
        "val $it = $tempName.$it"
    }
}
