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
    val addGenericsFromParentAndFields = {
        val receiverAlreadyHasGenericsGenerated =  this.last() == '>'

        // if one field has generic T in it, we should add it to class definition
        // class Sas(val x: Sus<T>) -> class Sas<T>(val x: Sus<T>)

        if (!receiverAlreadyHasGenericsGenerated) {
            val generalGenerics = mutableSetOf<String>()
            // from parent
            val parent = receiverType.parent
            if (parent is Type.UserLike && parent.typeArgumentList.isNotEmpty()) {
                generalGenerics.addAll(parent.typeArgumentList.map { it.toKotlinString(true) })
            }
            // from fields, BUT ONLY GENERAL GENERICS LIKE T G, not the resolved one
            fields.forEach { field ->
                val x = field.type as? Type.UserLike
                if (x?.typeArgumentList?.isNotEmpty() == true) {
                    generalGenerics.addAll(x.typeArgumentList.filter { it.name.isGeneric() }.map { it.name })
                }
            }

            if (generalGenerics.isNotEmpty()) {
                append("<")
                append(
                    generalGenerics.joinToString(", ")
                )
                append(">")
            }
        }

    }
    addGenericsFromParentAndFields()

    append("(")
    // class Person (^ arg: Type


    // when its a union included in other union, fields of the root and current type can overlap
    val setOfAlreadyAddedArgs = mutableSetOf<String>()

    fun generateFieldArgument2(fieldName: String, type: Type, i: Int, rootFields: Boolean, fieldsCountMinus1: Int) {
        if (typeName == "BinaryMsg") {
            1
        }
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
    } else
        Pair(fields, emptyList())

    // 3 phases here
    // 1) add fields of this branch (nonRootFields)
    // 2) add fields that overlaps with root
    // 3) add root fields

    nonRootFields.forEachIndexed { i, it ->
        generateFieldArgument2(
            it.name,
            it.type ?: it.token.compileError("ct error $it has no type"),
            i,
            false,
            fields.count() - 1
        )
    }
    // 2
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

    // 3 add fields of the root
    val parent = receiverType.parent as? Type.UserLike
    if (parent != null && parent.fields.isNotEmpty()) {
        if ((fields.isNotEmpty() || overlappedRootFields.isNotEmpty()) && this[this.count() - 2] != ',') {
//             comma after branch fields, before root fields
            append(", ")
        }
        parent.fields.forEachIndexed { i, it ->
            generateFieldArgument2(it.name, it.type, i, true, parent.fields.count() - 1)
        }
    }
//    if (receiverType is Type.UnionBranchType) {
//        if (receiverType.root.fields.isNotEmpty() && fields.isNotEmpty()) {
//            // comma after branch fields, before root fields
//            append(", ")
//        }
//        receiverType.root.fields.forEachIndexed { i, it ->
//            generateFieldArgument2(it.name, it.type, i, true, receiverType.root.fields.count() - 1)
//        }
//    }

    append(")")
    // class Person (var age: Int, kek: String)^

    // add inheritance
    if (root2 != null) {
        val currentType = receiverType as Type.UserLike
        val rootGenericFields = root2.typeArgumentList.map { it.name }
        val genericsOfTheBranch = currentType.typeArgumentList.map { it.name }.toSet()

        append(" : ${root2.name}")

        if (root2.name == "Statement") {
            1
        }
        if (root2.name == "MessageSend") {
            1
        }
        if (typeName == "MessageSend") {
            1
        }
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
    /// generate default, if "toString" is not declarated already
    if (enumRoot == null) {
        append("\toverride fun toString(): String")

        val toStringMethod =
            findAnyMethod(receiverType, "toString", Package(receiverType.pkg), MessageDeclarationType.Unary)
        if (toStringMethod != null && toStringMethod.declaration != null && toStringMethod.declaration.body.isNotEmpty()) {
            val returnTypeName = toStringMethod.declaration.returnType?.name
            if (returnTypeName != "String") {
                toStringMethod.declaration.token.compileError("${CYAN}toString$RESET methods should return ${YEL}String$RESET but it returns ${CYAN}$returnTypeName$RESET")
            }
            // generate body
            toStringMethod.declaration.body
            val sb = StringBuilder()
            generateBody(toStringMethod.declaration, sb)


            append(sb)
            appendLine("\n    companion object {")
        } else {
            // toString() : String" {"
            append(" {\n")

            append("\t\treturn \"\"\"\n")
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
                // we don't need before-spaces here since we already do prepend Indent for the whole string
                "\${${it.name}.toString().prependIndent(\"        \")}\n" +
                "    )"
            }

            val toStringFields = if (false)  //
//                fields.joinToString(" ") {
//                    generateSimpleField(it)
//                } + ")"

                ""
            else {
                generateFields(fields, generateSimpleField, generateComplexField)
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


//collect all fields from root and this
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
        append("<")
        append(realType.typeArgumentList.map { it.name }.toSet().joinToString(", ") { it })
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
