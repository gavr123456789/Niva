package main.codogen

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.Package
import frontend.resolver.Type
import frontend.resolver.unpackNull
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMethod
import main.utils.*

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
        append("<")
        append(realType.typeArgumentList.joinToString(", ") { it.name })
        append(">")
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


    // when its a union included in other union, fields of the root and current type can overlap
    val setOfAlreadyAddedArgs = mutableSetOf<String>()
    fun generateFieldArguments(it: TypeFieldAST, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {
        if (it.name in setOfAlreadyAddedArgs)
            return
        else
            setOfAlreadyAddedArgs.add(it.name)

        if (it.typeAST == null) {
            it.token.compileError("Arg $WHITE${it.name}$RED must have type")
        }

        if (!rootFields) {
            append("var ")
        }

        // shit, better make unresolved type a different kind of types
        val typeName =
            it.type!!.toKotlinString(true) //if (it.type!!.name == InternalTypes.NotResolved.toString()) it.typeAST.generateType() else it.type!!.toKotlinString(true)
        append(it.name, ": ", typeName)
        if (fieldsCountMinus1 != i) {
            append(", ")
        }
    }

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
        generateFieldArguments(it, i, false, fields.count() - 1)
    }
    overlappedRootFields.forEachIndexed { i, it ->
        generateFieldArguments(it, i, true, fields.count() - 1)
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
        val currentType = receiverType as Type.UserLike
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
                // NOT REPLACING IT, since then we need to support out in params too
//                realGenerics.add("Nothing")
                realGenerics.add(it)
            } else
                realGenerics.add(it)
        }

        append(realGenerics.toSortedSet().joinToString(", "))


        if (isThereGenericsSomewhere)
            append(">")

        // class Person (var age: Int, kek: String) : Human<...>^(kek)
        append("(")
        // this is Duplicate of generating fields from UserType
        val w = root2.fields.count() - 1
        root2.fields.map { it.name }.toSortedSet().forEachIndexed { i, it ->
            append(it)
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
        append("\toverride fun toString(): String")

        val qwf = findAnyMethod(receiverType, "toString", Package(receiverType.pkg), MessageDeclarationType.Unary)
        if (qwf != null && qwf.declaration != null && qwf.declaration.body.isNotEmpty()) {
            val returnTypeName = qwf.declaration.returnType?.name
            if (returnTypeName != "String") {
                qwf.declaration.token.compileError("${CYAN}toString$RESET methods should return ${YEL}String$RESET but it returns ${CYAN}$returnTypeName$RESET")
            }
            // generate body
            qwf.declaration.body
            val sb = StringBuilder()
            generateBody(qwf.declaration, sb)

            append(sb)
            appendLine("\n    companion object {")
        } else {
            // toString() : String" {"
            append(" {\n")

            append("\t\treturn \"\"\"")

            val fields: List<TypeFieldAST> = this@generateTypeDeclaration.collectFields()
//            val fewFields = fields.count() <= 2

            if (false)
                append("(", typeName)
            else
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
                        // we dont need before-spaces here since we already do prepend Indent for the whole string
                        "\${${it.name}.toString().prependIndent(\"        \")}\n" +
                        "    )"
            }


            val toStringFields = if (false) //
                fields.joinToString(" ") {
                    generateSimpleField(it)
                } + ")"
            else {
                "\n" + fields.joinToString("\n") {
                    val type = it.type?.unpackNull()
                    when (type) {
                        is Type.EnumBranchType -> generateSimpleField(it)
                        is Type.EnumRootType -> generateSimpleField(it)

                        is Type.UserLike -> generateComplexField(it)
                        is Type.InternalType -> generateSimpleField(it)

                        is Type.Lambda -> "    " + it.name + ": " + it.type.toString()
                        is Type.NullableType -> if (type.unpackNull() is Type.InternalType) generateSimpleField(it) else generateComplexField(
                            it
                        )

                        is Type.UnresolvedType -> it.token.compileError("Unresolved type $type of $it")
                        null -> it.token.compileError("type of $it is null")
                    }
                }
            }


            append(toStringFields, "\"\"\"")
            // for static methods like constructor
            append(
                """
    }
    companion object {
"""
            )
        }
        //////
    }
    val generateDynamicConverters5 = {
        val checkForCollections = { type: Type.UserLike ->
            when (type.name) {
                "List", "MutableList" -> false
                "Map", "MutableMap" -> false
                "Set", "MutableSet" -> false
                else -> true
            }
        }

        val isComplex: (Type?) -> Boolean = { type: Type? ->
            when (type) {
                is Type.EnumRootType, is Type.EnumBranchType, is Type.Lambda, is Type.UnresolvedType, null -> false
                is Type.UserLike -> checkForCollections(type)
                is Type.NullableType -> {
                    val unpack = type.unpackNull()
                    if (unpack is Type.UserType) checkForCollections(unpack) else false
                }
                is Type.InternalType -> false
            }
        }

        val isList: (Type?) -> Boolean = { type: Type? ->
            type is Type.UserType && (type.name == "List" || type.name == "MutableList")
        }

        val listElementType: (Type.UserType) -> Type? = { type: Type.UserType ->
            if (type.typeArgumentList.isNotEmpty()) type.typeArgumentList[0] else null
        }

        val fields: List<TypeFieldAST> = this@generateTypeDeclaration.collectFields()

        // toDynamic
        appendLine("        fun toDynamic(instance: $typeName): DynamicObj {")
        appendLine("            val map = mutableMapOf<String, Dynamic>()")
        val generateLocalFields = { fields2: List<TypeFieldAST> ->
            fields2.forEach { field ->
                appendLine("            val ${field.name} = instance.${field.name}")
            }
        }
        generateLocalFields(fields)
        for (field in fields) {
            val fieldName = field.name
            val originalType = field.type
            val type = originalType?.unpackNull()
            val isNullable = originalType is Type.NullableType
            val complex = isComplex(type)

            val expr = when {
                isList(type) -> {
                    val listType = type as Type.UserType
                    val elemType = listElementType(listType)
                    val unpackedElem = elemType?.unpackNull()
                    val elemIsNullable = elemType is Type.NullableType

                    val baseExpr = when (unpackedElem) {
                        is Type.InternalType -> when (unpackedElem.name) {
                            "String" -> "map { DynamicStr(it) }"
                            "Int" -> "map { DynamicInt(it) }"
                            "Double" -> "map { DynamicDouble(it) }"
                            "Float" -> "map { DynamicDouble(it.toDouble()) }"
                            "Boolean" -> "map { DynamicBoolean(it) }"
                            else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                        }
                        is Type.UserType, is Type.Union -> "map { ${unpackedElem.pkg}.${unpackedElem.name}.toDynamic(it) }"
                        else -> "/* unsupported unpacked null list $unpackedElem */ TODO()"
                    }

                    val listExpr = if (elemIsNullable) "$fieldName.filterNotNull().$baseExpr" else "$fieldName.$baseExpr"
                    "DynamicList($listExpr)"
                }

                complex -> "${field.type?.name}.toDynamic($fieldName)"
                type is Type.InternalType -> when (type.name) {
                    "String" -> "DynamicStr($fieldName)"
                    "Int" -> "DynamicInt($fieldName)"
                    "Double" -> "DynamicDouble($fieldName)"
                    "Float" -> "DynamicDouble($fieldName.toDouble())"
                    "Boolean" -> "DynamicBoolean($fieldName)"
                    else -> "/* unsupported primitive ${type.name} */ TODO()"
                }
                else -> "/* unknown type */ TODO()"
            }

            if (isNullable) {
                appendLine("            if ($fieldName != null) map[\"$fieldName\"] = $expr")
            } else {
                appendLine("            map[\"$fieldName\"] = $expr")
            }
        }

        appendLine("            return DynamicObj(map)")
        appendLine("        }")

        // fromDynamic
        appendLine("\n        fun fromDynamic(dyn: DynamicObj): $typeName {")
        appendLine("            val fields = dyn.value")
        appendLine("            return $typeName(")

        fields.forEachIndexed { index, field ->
            val fieldName = field.name
            val originalType = field.type
            val type = originalType?.unpackNull()
            val isNullable = originalType is Type.NullableType
            val complex = isComplex(type)

            val line = when {
                isList(type) -> {
                    val listType = type as Type.UserType
                    val elemType = listElementType(listType)
                    val unpackedElem = elemType?.unpackNull()
                    val elemIsNullable = elemType is Type.NullableType
                    val base = when (unpackedElem) {
                        is Type.InternalType -> when (unpackedElem.name) {
                            "String" -> "(it as DynamicStr).value"
                            "Int" -> "(it as DynamicInt).value"
                            "Double" -> "(it as DynamicDouble).value"
                            "Float" -> "(it as DynamicDouble).value.toFloat()"
                            "Boolean" -> "(it as DynamicBoolean).value"
                            else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                        }
                        is Type.UserType, is Type.Union  -> "${unpackedElem.pkg}.${unpackedElem.name}.fromDynamic(it as DynamicObj)"
                        else -> "/* unsupported list unpack $unpackedElem */ TODO()"
                    }
                    val nullableBase = when (unpackedElem) {
                        is Type.InternalType -> when (unpackedElem.name) {
                            //(it as? DynamicInt).value
                            "String" -> "(it as? DynamicStr)?.value"
                            "Int" -> "(it as? DynamicInt)?.value"
                            "Double" -> "(it as? DynamicDouble)?.value?.toFloat()"
                            "Boolean" -> "(it as? DynamicBoolean)?.value"
                            else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                        }
                        is Type.UserType -> "${unpackedElem.name}.fromDynamic(it as DynamicObj)"
                        else -> "/* unsupported list $unpackedElem */ TODO()"
                    }

                    val listRead =
                        if (isNullable) "(fields[\"$fieldName\"] as? DynamicList)?.value"
                        else  "(fields[\"$fieldName\"] as DynamicList).value"

                    "$fieldName = $listRead${if (isNullable) "?" else ""}.map { ${if (elemIsNullable) "if (it == null) null else $nullableBase" else base} }.toMutableList()"
                }

                complex -> {
                    val valueAccess =  "(fields[\"$fieldName\"] as? DynamicObj)"
                    if (isNullable) "$fieldName = $valueAccess?.let { ${field.type?.name}.fromDynamic(it) }"
                    else "$fieldName = ${field.type?.name}.fromDynamic(fields[\"$fieldName\"]!! as DynamicObj)"
                }

                type is Type.InternalType -> {
                    val base = when (type.name) {
                        "String" -> "(it as DynamicStr).value"
                        "Int" -> "(it as DynamicInt).value"
                        "Double" -> "(it as DynamicDouble).value"
                        "Float" -> "(it as DynamicDouble).value.toFloat()"
                        "Boolean" -> "(it as DynamicBoolean).value"
                        else -> "/* unsupported primitive ${type.name} */ TODO()"
                    }
                    if (isNullable) "$fieldName = (fields[\"$fieldName\"])?.let { $base }" else "$fieldName = fields[\"$fieldName\"]!!.let { $base }"
                }

                else -> "/* unknown type */ TODO()"
            }

            appendLine("            $line" + if (index != fields.lastIndex) "," else "")
        }

        appendLine("        )")
        appendLine("    }")
    }


    val generateDynamicForUnionRoot = { receiverType: Type.UnionRootType ->
        val typeName = receiverType.name
        val branches = receiverType.branches

        // toDynamic
        appendLine("\n        fun toDynamic(instance: $typeName): DynamicObj {")
        appendLine("            return when (instance) {")

        for (branch in branches) {
            val branchName = branch.name
            appendLine("                is $branchName -> $branchName.toDynamic(instance).also { it.value[\"_unionKind\"] = DynamicStr(\"$branchName\") }")
        }

        appendLine("            }")
        appendLine("        }")

        // fromDynamic
        appendLine("\n        fun fromDynamic(dyn: DynamicObj): $typeName {")
        appendLine("            val kind = (dyn.value[\"_unionKind\"] as? DynamicStr)?.value")
        appendLine("            return when (kind) {")

        for (branch in branches) {
            val branchName = branch.name
            appendLine("                \"$branchName\" -> $branchName.fromDynamic(dyn)")
        }

        appendLine("                else -> error(\"Unknown _unionKind: \$kind\")")
        appendLine("            }")
        appendLine("        }")
    }


    if (receiverType is Type.UserLike && receiverType !is Type.EnumRootType && receiverType !is Type.EnumBranchType) {
        if (receiverType !is Type.UnionRootType)
            generateDynamicConverters5()
        else {
            generateDynamicForUnionRoot(receiverType)
        }
    }
    if (receiverType !is Type.EnumRootType) {
        appendLine("    }")
    }
    append("\n}\n")
    //////

}
//fun internalToDynamic(type: Type.InternalType): String = when (type.name) {
//    "String" -> "DynamicStr"
//    "Int" -> "DynamicInt"
//    "Double" -> "DynamicDouble"
//    "Float" -> "DynamicDouble"
//    "Boolean" -> "DynamicBoolean"
//    else -> "TODO(\"unsupported primitive ${type}\")"
//}

//fun typeToDynamicConstructor(type: Type): String {
//
//}
//fun internalToDynamicConstructor(type: Type.InternalType, str: String): String = when (type.name) {
//    "String" -> "DynamicStr($str)"
//    "Int" -> "DynamicInt($str)"
//    "Double" -> "DynamicDouble($str)"
//    "Float" -> "DynamicDouble($str.toDouble())"
//    "Boolean" -> "DynamicBoolean($str)"
//    else -> "TODO(\"unsupported primitive ${type}\")"
//}

private fun SomeTypeDeclaration.collectFields(): List<TypeFieldAST> {
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


