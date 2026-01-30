package codogen.declarations

import frontend.resolver.Type
import frontend.resolver.isCollection
import frontend.resolver.unpackNull
import main.codogen.collectFields
import main.frontend.parser.types.ast.SomeTypeDeclaration
import main.frontend.parser.types.ast.TypeFieldAST
import kotlin.collections.forEach


fun generateDynamicForUnionRoot(b: StringBuilder, receiverType: Type.UnionRootType) {
    // cant make T.fromDynamic() call
    if (receiverType.typeArgumentList.isNotEmpty()) return
    // lazy generation
    val x = receiverType
    if (!x.needGenerateDynamic) {
        return
    }

    val typeName = receiverType.toKotlinString(true)
    val branches = receiverType.branches

    // toDynamic
    b.appendLine("\n        fun toDynamic(instance: $typeName): DynamicObj {")
    b.appendLine("            return when (instance) {")

    for (branch in branches) {
        val branchName = branch.toKotlinString(true)
        b.appendLine("                is $branchName -> $branchName.toDynamic(instance).also { it.value[\"_unionKind\"] = DynamicStr(\"$branchName\") }")
    }

    b.appendLine("            }")
    b.appendLine("        }")

    // fromDynamic
    b.appendLine("\n        fun fromDynamic(dyn: DynamicObj): $typeName {")
    b.appendLine("            val kind = (dyn.value[\"_unionKind\"] as? DynamicStr)?.value")
    b.appendLine("            return when (kind) {")

    for (branch in branches) {
        val branchName = branch.name
        b.appendLine("                \"$branchName\" -> $branchName.fromDynamic(dyn)")
    }

    b.appendLine($$"                else -> error(\"Unknown _unionKind: $kind\")")
    b.appendLine("            }")
    b.appendLine("        }")
}

fun SomeTypeDeclaration.generateDynamicConverters5(b: StringBuilder) {
    if (genericFields.isNotEmpty()) return

    // lazy generation, if to\from Dynami never called, do not generate it
    val x = receiver
    if (x is Type.UserLike && !x.needGenerateDynamic) {
        return
    }


    val isComplex: (Type?) -> Boolean = { type: Type? ->
        when (type) {
            is Type.EnumRootType, is Type.EnumBranchType, is Type.Lambda, is Type.UnresolvedType, null -> false
            is Type.UserLike -> {
                type.needGenerateDynamic = true
                true
            } //isCollection(type.name)
            is Type.NullableType -> {
                val unpack = type.unpackNull()
                if (unpack is Type.UserLike) isCollection(unpack.name) else false
            }

            is Type.InternalType -> false
        }
    }

    val isCollectionType: (Type?) -> Boolean = { type: Type? ->
        type is Type.UserType && (isCollection(type.name))
    }

    val listElementType: (Type.UserType) -> Type? = { type: Type.UserType ->
        if (type.typeArgumentList.isNotEmpty()) type.typeArgumentList[0] else null
    }

    val fields: List<TypeFieldAST> = this.collectFields()
    // toDynamic
    b.appendLine("        fun toDynamic (instance: ${receiver!!.toKotlinString(true)}): DynamicObj {")
    b.appendLine("            val map = mutableMapOf<String, Dynamic>()")
    val generateLocalFields = { fields2: List<TypeFieldAST> ->
        fields2.forEach { field ->
            b.appendLine("            val ${field.name} = instance.${field.name}")
        }
    }
    generateLocalFields(fields)
    for (field in fields) {
        val fieldName = field.name
        val originalType = field.type
        val type = originalType?.unpackNull()
        val isNullable = originalType is Type.NullableType

        val expr = when {
            type is Type.EnumRootType -> "DynamicStr($fieldName.name)"
            isCollectionType(type) -> {
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
                        "Bool" -> "map { DynamicBoolean(it) }"
                        else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                    }

                    is Type.UserType, is Type.Union -> "map { ${unpackedElem.pkg}.${unpackedElem.name}.toDynamic(it) }"
                    is Type.EnumRootType -> "map { DynamicStr(it.name) }"
                    else -> "/* unsupported unpacked null list $unpackedElem */ TODO()"
                }

                val listExpr = if (elemIsNullable) "$fieldName.filterNotNull().$baseExpr" else "$fieldName.$baseExpr"
                "DynamicList($listExpr)"
            }

            isComplex(type) -> {
                "${field.type?.name}.toDynamic($fieldName)"
            }
            type is Type.InternalType -> when (type.name) {
                "String" -> "DynamicStr($fieldName)"
                "Int" -> "DynamicInt($fieldName)"
                "Double" -> "DynamicDouble($fieldName)"
                "Float" -> "DynamicDouble($fieldName.toDouble())"
                "Bool" -> "DynamicBoolean($fieldName)"
                else -> "/* unsupported primitive ${type.name} */ TODO()"
            }

            else -> "/* unknown type */ TODO()"
        }

        if (isNullable) {
            b.appendLine("            if ($fieldName != null) map[\"$fieldName\"] = $expr")
        } else {
            b.appendLine("            map[\"$fieldName\"] = $expr")
        }
    }

    b.appendLine("            return DynamicObj(map)")
    b.appendLine("        }")

    // fromDynamic
    b.appendLine("\n        fun fromDynamic(dyn: DynamicObj): $typeName {")
    b.appendLine("            val fields = dyn.value")
    b.appendLine("            return $typeName(")

    fields.forEachIndexed { index, field ->
        val fieldName = field.name
        val originalType = field.type
        val type = originalType?.unpackNull()
        val isNullable = originalType is Type.NullableType

        val line = when {
            type is Type.EnumRootType -> {
                val enumClassName = type.name
                if (isNullable) "$fieldName = (fields[\"$fieldName\"] as? DynamicStr)?.value?.let { $enumClassName.valueOf(it) }"
                else "$fieldName = $enumClassName.valueOf((fields[\"$fieldName\"] as DynamicStr).value)"
            }
            isCollectionType(type) -> {
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
                        "Bool" -> "(it as DynamicBoolean).value"
                        else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                    }

                    is Type.UserType, is Type.Union -> "${unpackedElem.pkg}.${unpackedElem.name}.fromDynamic(it as DynamicObj)"
                    is Type.EnumRootType -> {
                        val enumClassName = unpackedElem.name
                        "(it as DynamicStr).value.let { $enumClassName.valueOf(it) }"
                    }
                    else -> "/* unsupported list unpack $unpackedElem */ TODO()"
                }
                val nullableBase = when (unpackedElem) {
                    is Type.InternalType -> when (unpackedElem.name) {
                        //(it as? DynamicInt).value
                        "String" -> "(it as? DynamicStr)?.value"
                        "Int" -> "(it as? DynamicInt)?.value"
                        "Double" -> "(it as? DynamicDouble)?.value"
                        "Float" -> "(it as? DynamicDouble)?.value?.toFloat()"
                        "Bool" -> "(it as? DynamicBoolean)?.value"
                        else -> "/* unsupported primitive list ${unpackedElem.name} */ TODO()"
                    }
                    is Type.UserType -> "${unpackedElem.name}.fromDynamic(it as DynamicObj)"
                    is Type.EnumRootType -> {
                        val enumClassName = unpackedElem.name
                        "(it as? DynamicStr)?.value?.let { $enumClassName.valueOf(it) }"
                    }
                    else -> "/* unsupported list $unpackedElem */ TODO()"
                }

                val listRead =
                    if (isNullable) "(fields[\"$fieldName\"] as? DynamicList)?.value"
                    else "(fields[\"$fieldName\"] as DynamicList).value"

                "$fieldName = $listRead${if (isNullable) "?" else ""}.map { ${if (elemIsNullable) "if (it == null) null else $nullableBase" else base} }.toMutableList()"
            }

            isComplex(type) -> {
                val valueAccess = "(fields[\"$fieldName\"] as? DynamicObj)"
                if (isNullable) "$fieldName = $valueAccess?.let { ${field.type?.name}.fromDynamic(it) }"
                else "$fieldName = ${field.type?.name}.fromDynamic(fields[\"$fieldName\"]!! as DynamicObj)"
            }

            type is Type.InternalType -> {
                val base = when (type.name) {
                    "String" -> "(it as DynamicStr).value"
                    "Int" -> "(it as DynamicInt).value"
                    "Double" -> "(it as DynamicDouble).value"
                    "Float" -> "(it as DynamicDouble).value.toFloat()"
                    "Bool" -> "(it as DynamicBoolean).value"
                    else -> "/* unsupported primitive ${type.name} */ TODO()"
                }
                if (isNullable) "$fieldName = (fields[\"$fieldName\"])?.let { $base }" else "$fieldName = fields[\"$fieldName\"]!!.let { $base }"
            }

            else -> "/* unknown type */ TODO()"
        }

        b.appendLine("            $line" + if (index != fields.lastIndex) "," else "")
    }

    b.appendLine("        )")
    b.appendLine("    }")

}