package main.codogen

import frontend.resolver.Type
import main.frontend.parser.types.ast.*

fun ControlFlow.If.generateIf(): String = buildString {

    val firstIfBranch = ifBranches[0]

    append("if (")
    append(firstIfBranch.ifExpression.generateExpression())
    append(") {\n")
    append("    ")

    when (firstIfBranch) {
        is IfBranch.IfBranchSingleExpr -> append(firstIfBranch.thenDoExpression.generateExpression())
        is IfBranch.IfBranchWithBody -> append(codegenKt(firstIfBranch.body.statements, 1))
    }
    append("\n}")


    ifBranches.drop(1).forEach { ifBranch ->
        append(" else if (")
        append(ifBranch.ifExpression.generateExpression())
        append(") {\n")
        append("    ")
        when (ifBranch) {
            is IfBranch.IfBranchSingleExpr -> append(ifBranch.thenDoExpression.generateExpression())
            is IfBranch.IfBranchWithBody -> append(codegenKt(ifBranch.body.statements, 1))
        }
        append("\n}")

    }

    if (elseBranch != null) {
        append(" else {\n")
        append(codegenKt(elseBranch, 1))
        append("} ")
    }

}


fun ControlFlow.Switch.generateSwitch() = buildString {
    append("when (")
    append(switch.generateExpression())
    append(") {\n")
    ifBranches.forEach {
        append("    ")

        if (kind != ControlFlowKind.ExpressionTypeMatch && kind != ControlFlowKind.StatementTypeMatch) {
            append(it.ifExpression.generateExpression())
            if (it.otherIfExpressions.isNotEmpty()) {
                append(", " + it.otherIfExpressions.joinToString(", ") {x -> x.generateExpression() })
            }
        } else {
            append("is ", it.ifExpression.generateExpression())
            if (it.otherIfExpressions.isNotEmpty()) {
                append(", is " + it.otherIfExpressions.joinToString(", is ") {x -> x.generateExpression() })
            }
        }

        append(" -> ")
        when (it) {
            is IfBranch.IfBranchSingleExpr -> {
                append(it.thenDoExpression.generateExpression())
            }

            is IfBranch.IfBranchWithBody -> append("{\n",codegenKt(it.body.statements, 1), "\n}\n")
        }
        append("\n")
    }

    if (elseBranch != null) {
        append("    else -> {")
        val elseBranchCode = codegenKt(elseBranch, 0)
        appendLine(elseBranchCode)
        appendLine("    }")
        appendLine("}")
    } else {
        // if this is switch on errors, we need fix kotlins exhaustive, because its possible to check it only on niva side
        // (errors are effects, and set of them exist in current scope)
        val switch = this@generateSwitch.switch
        val type = switch.type
        if (type is Type.Union && type.isError && this@generateSwitch.kind == ControlFlowKind.ExpressionTypeMatch) {
            append($$"    else -> throw Exception(\"Compiler bug, non exhaustive switch on error, got ${$$switch}\")")
        }
        // end of when
        append("}\n")
    }
}


inline fun <T> Iterable<T>.forEach(exceptLastDo: (T) -> Unit, action: (T) -> Unit) {
    val c = count()
    this.forEachIndexed { index, t ->
        action(t)
        if (index != c - 1) {
            exceptLastDo(t)
        }
    }
}

private fun isReceiverATypeItself(x: Receiver): Boolean =
    x is IdentifierExpr && x.isType

private fun StringBuilder.appendGenericIfNeeded(
    shouldAddGeneric: Boolean,
    wasTypesInsideCollection: Boolean,
    initElements: List<Receiver>,
    type: Type?
) {
    if (!shouldAddGeneric) return

    val typeArg = if (wasTypesInsideCollection && initElements.isNotEmpty()) {
        initElements.first().type!!
    } else if (type is Type.UserLike) {
        type.typeArgumentList.firstOrNull()
    } else null

    // Only add generic if it's not an unresolved generic type like T
    if (typeArg != null && typeArg !is Type.UnknownGenericType) {
        append("<")
        append(typeArg.toKotlinString(false))
        append(">")
    }
}

fun ListCollection.generateList() = buildString {
    val filteredInitElements = initElements.filter { !isReceiverATypeItself(it) }
    // x = {Int}
    val wasTypesInsideCollection = filteredInitElements.count() < initElements.count()

    if (!isMutableCollection)
        append("listOf")
    else
        append("mutableListOf")

    // add generic parameter if:
    // types were inside collection like Int
    // OR collection is empty but has type like List::Int val: {}
    val shouldAddGeneric = wasTypesInsideCollection && initElements.isNotEmpty() ||
                          filteredInitElements.isEmpty() //&& type is Type.UserLike

    appendGenericIfNeeded(shouldAddGeneric, wasTypesInsideCollection, initElements, type)

    append("(")

    filteredInitElements
        .forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}


fun MapCollection.generateMap() = buildString {
    val filteredInitElements = initElements.filter { (key, value) -> !(isReceiverATypeItself(key) && isReceiverATypeItself(value)) }
    // x = {Int}
    val wasTypesInsideCollection = filteredInitElements.count() < initElements.count()

    if (!isMutable)
        append("mapOf")
    else
        append("mutableMapOf")

    val shouldAddGeneric = wasTypesInsideCollection && initElements.isNotEmpty() ||
                          filteredInitElements.isEmpty() && type is Type.UserLike

    if (shouldAddGeneric) {
        val (keyType, valueType) = if (wasTypesInsideCollection && initElements.isNotEmpty()) {
            initElements.first().first.type!! to initElements.first().second.type!!
        } else if (type is Type.UserLike) {
            val typeArgs = (type as Type.UserLike).typeArgumentList
            if (typeArgs.size >= 2) {
                typeArgs[0] to typeArgs[1]
            } else {
                null to null
            }
        } else {
            null to null
        }

        // add generics onlyif they are not unresolved generic types like T
        if (keyType != null && valueType != null &&
            keyType !is Type.UnknownGenericType && valueType !is Type.UnknownGenericType) {
            append("<")
            append(keyType.toKotlinString(false))
            append(", ")
            append(valueType.toKotlinString(false))
            append(">")
        }
    }

    append("(")

    filteredInitElements
        .forEach(exceptLastDo = { append(", ") }) {
        append(it.first.generateExpression(), " to ", it.second.generateExpression())
    }

    append(")")
}

fun SetCollection.generateSet() = buildString {
    val filteredInitElements = initElements.filter { !isReceiverATypeItself(it) }
    // x = {Int}
    val wasTypesInsideCollection = filteredInitElements.count() < initElements.count()

    if (!isMutableCollection)
        append("setOf")
    else
        append("mutableSetOf")

    val shouldAddGeneric = wasTypesInsideCollection && initElements.isNotEmpty() ||
                          filteredInitElements.isEmpty() && type is Type.UserLike

    appendGenericIfNeeded(shouldAddGeneric, wasTypesInsideCollection, initElements, type)

    append("(")

    filteredInitElements
        .forEach(exceptLastDo = { append(", ") }) {
        append(it.generateExpression())
    }

    append(")")
}
