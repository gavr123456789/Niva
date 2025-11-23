package main.codogenjs

import frontend.resolver.Package
import frontend.resolver.Type
import main.frontend.parser.types.ast.*

private fun normalizeSelectorJs(name: String): String = when (name) {
    "+" -> "plus"
    "-" -> "minus"
    "*" -> "times"
    "/" -> "div"
    "%" -> "rem"
    ".." -> "rangeTo"
    "+=" -> "plusAssign"
    "-=" -> "minusAssign"
    "*=" -> "timesAssign"
    "/=" -> "divAssign"
    "%=" -> "remAssign"
    "==" -> "equals"
    "!=" -> "notEquals"
    ">" -> "gt"
    "<" -> "lt"
    ">=" -> "ge"
    "<=" -> "le"
    "apply" -> "invoke"
    else -> name
}

private fun buildJsFuncName(receiverType: Type, message: Message, argTypes: List<Type>): String {
    val recv = receiverType.toJsMangledName()
    val baseName = normalizeSelectorJs(message.selectorName)
    val suffix = if (argTypes.isNotEmpty()) argTypes.joinToString("__") { it.toJsMangledName() } else ""
    return if (suffix.isEmpty()) "${recv}__${baseName}" else "${recv}__${baseName}__${suffix}"
}

/**
 * Имя пакета, к которому принадлежит тип (без Nullable-обёртки).
 */
private fun Type.targetPackageName(): String? = when (val t = unwrapNull()) {
    is Type.UserLike -> t.pkg
    is Type.InternalType -> t.pkg
    else -> null
}

/**
 * alias для пакета, если он отличается от текущего.
 * Пример: pkg "core.utils" -> alias "core_utils".
 */
private fun Type.jsQualifierFor(currentPkg: Package?): String? {
    val targetPkg = targetPackageName() ?: return null
    val currentName = currentPkg?.packageName
    if (currentName == null || currentName == targetPkg) return null
    return targetPkg.replace('.', '_')
}

/**
 * Полное имя функции для вызова: либо просто mangledName, либо alias.mangledName
 * если функция живёт в другом пакете.
 */
private fun buildQualifiedJsFuncName(receiverType: Type, message: Message, argTypes: List<Type>): String {
    val forType = message.msgMetaData?.forType ?: message.declaration?.forType ?: receiverType
    val baseName = buildJsFuncName(forType, message, argTypes)
    val alias = forType.jsQualifierFor(JsCodegenContext.currentPackage)
    return if (alias != null) "$alias.$baseName" else baseName
}

/**
 * Имя класса для конструктора с возможным префиксом alias.
 */
private fun Type.constructorJsName(): String {
    val baseName = (this as? Type.UserLike)?.emitName ?: this.name
    val alias = jsQualifierFor(JsCodegenContext.currentPackage)
    return if (alias != null) "$alias.$baseName" else baseName
}

private fun Type.unwrapNull(): Type = if (this is Type.NullableType) this.realType else this

private fun isNumeric(t: Type): Boolean = when (t.unwrapNull()) {
    is Type.InternalType -> t.unwrapNull().name in setOf("Int", "Double", "Float")
    else -> false
}

private fun isString(t: Type): Boolean = (t.unwrapNull() as? Type.InternalType)?.name == "String"
private fun isBool(t: Type): Boolean = (t.unwrapNull() as? Type.InternalType)?.name == "Bool"

private fun tryEmitNativeBinary(
    op: String,
    recvExpr: String,
    recvType: Type?,
    argExpr: String,
    argType: Type?
): String? {
    val rt = recvType ?: return null
    val at = argType ?: return null
    val rNum = isNumeric(rt)
    val aNum = isNumeric(at)
    val rStr = isString(rt)
    val aStr = isString(at)
    val rBool = isBool(rt)
    val aBool = isBool(at)

    return when (op) {
        "==" -> "(($recvExpr) === ($argExpr))"
        "!=" -> "(($recvExpr) !== ($argExpr))"

        "+" -> when {
            rNum && aNum -> "(($recvExpr) + ($argExpr))"
            rStr && aStr -> "(($recvExpr) + ($argExpr))"
            else -> null
        }
        "-", "*", "/" -> if (rNum && aNum) "(($recvExpr) $op ($argExpr))" else null
        ">", "<", ">=", "<=" -> if (rNum && aNum) "(($recvExpr) $op ($argExpr))" else null
        "||", "&&" -> if (rBool && aBool) "(($recvExpr) $op ($argExpr))" else null
        else -> null
    }
}

fun MessageSend.generateJsMessageCall(): String {
    val b = StringBuilder()
    var currentExpr = receiver.generateJsExpression()
    var currentType = receiver.type ?: messageTypeSafe(this)

    messages.forEach { msg ->
        when (msg) {
            is UnaryMsg -> {
                if (msg.kind == UnaryMsgKind.Getter) {
                    // доступ к полю: p name  ->  p.name
                    currentExpr = "$currentExpr.${msg.selectorName}"
                    currentType = msg.type ?: currentType
                } else {
                    val name = buildQualifiedJsFuncName(currentType, msg, emptyList())
                    currentExpr = "$name($currentExpr)"
                    currentType = msg.type ?: currentType
                }
            }
            is BinaryMsg -> {
                // применяем унарные сообщения к ресиверу
                var recvExpr = currentExpr
                var recvType = currentType
                if (msg.unaryMsgsForReceiver.isNotEmpty()) {
                    msg.unaryMsgsForReceiver.forEach { u ->
                        val name = buildQualifiedJsFuncName(recvType, u, emptyList())
                        recvExpr = "$name($recvExpr)"
                        recvType = u.type ?: recvType
                    }
                }

                // генерим аргумент и применяем его унарные
                var argExpr = msg.argument.generateJsExpression()
                var argType: Type? = msg.argument.type ?: (msg.declaration as? MessageDeclarationBinary)?.arg?.type
                if (msg.unaryMsgsForArg.isNotEmpty()) {
                    msg.unaryMsgsForArg.forEach { u ->
                        val name = buildQualifiedJsFuncName(argType ?: recvType, u, emptyList())
                        argExpr = "$name($argExpr)"
                        argType = u.type ?: argType
                    }
                }

                val native = tryEmitNativeBinary(msg.selectorName, recvExpr, recvType, argExpr, argType)
                if (native != null) {
                    currentExpr = native
                } else {
                    val fn = buildQualifiedJsFuncName(recvType, msg, listOfNotNull(argType))
                    currentExpr = "$fn($recvExpr, $argExpr)"
                }
                currentType = msg.type ?: recvType
            }
            is KeywordMsg -> {
                val args = msg.args.map { it.keywordArg.generateJsExpression(true) }
                val argTypes = msg.args.mapNotNull { it.keywordArg.type }

                if (msg.kind == KeywordLikeType.Constructor || msg.kind == KeywordLikeType.CustomConstructor) {
                    // вызов конструктора типа: Person name: "Alice" age: 24 -> new Person("Alice", 24)
                    val recvType = currentType
                    val typeName = recvType.constructorJsName()
                    currentExpr = buildString {
                        append("new ", typeName, "(")
                        append(args.joinToString(", "))
                        append(")")
                    }
                    currentType = msg.type ?: currentType
                } else {
                    val fn = buildQualifiedJsFuncName(currentType, msg, argTypes)
                    currentExpr = buildString {
                        append(fn, "(", currentExpr)
                        if (args.isNotEmpty()) {
                            append(", ")
                            append(args.joinToString(", "))
                        }
                        append(")")
                    }
                    currentType = msg.type ?: currentType
                }
            }
            else -> {}
        }
    }
    b.append(currentExpr)
    return b.toString()
}

fun Message.generateJsAsCall(): String {
    var recvExpr = receiver.generateJsExpression()
    var recvType = receiver.type ?: this.type ?: error("Receiver type unknown for message call")
    return when (this) {
        is UnaryMsg -> {
            if (this.kind == UnaryMsgKind.Getter) {
                "$recvExpr.${this.selectorName}"
            } else {
                val name = buildQualifiedJsFuncName(recvType, this, emptyList())
                "$name($recvExpr)"
            }
        }
        is BinaryMsg -> {
            // применяем унарные к ресиверу
            if (unaryMsgsForReceiver.isNotEmpty()) {
                unaryMsgsForReceiver.forEach { u ->
                    val name = buildQualifiedJsFuncName(recvType, u, emptyList())
                    recvExpr = "$name($recvExpr)"
                    recvType = u.type ?: recvType
                }
            }

            // аргумент и его унарные
            var argExpr = argument.generateJsExpression()
            var argType: Type? = argument.type ?: (declaration as? MessageDeclarationBinary)?.arg?.type
            if (unaryMsgsForArg.isNotEmpty()) {
                unaryMsgsForArg.forEach { u ->
                    val name = buildQualifiedJsFuncName(argType ?: recvType, u, emptyList())
                    argExpr = "$name($argExpr)"
                    argType = u.type ?: argType
                }
            }

            val native = tryEmitNativeBinary(this.selectorName, recvExpr, recvType, argExpr, argType)
            if (native != null) native else {
                val fn = buildQualifiedJsFuncName(recvType, this, listOfNotNull(argType))
                "$fn($recvExpr, $argExpr)"
            }
        }
        is KeywordMsg -> {
            val args = args.map { it.keywordArg.generateJsExpression(true) }
            val argTypes = this.args.mapNotNull { it.keywordArg.type }

            if (this.kind == KeywordLikeType.Constructor || this.kind == KeywordLikeType.CustomConstructor) {
                val typeName = recvType.constructorJsName()
                buildString {
                    append("new ", typeName, "(")
                    append(args.joinToString(", "))
                    append(")")
                }
            } else {
                val fn = buildQualifiedJsFuncName(recvType, this, argTypes)
                buildString {
                    append(fn, "(", recvExpr)
                    if (args.isNotEmpty()) {
                        append(", ")
                        append(args.joinToString(", "))
                    }
                    append(")")
                }
            }
        }
        else -> recvExpr
    }
}

private fun messageTypeSafe(ms: MessageSend): Type =
    ms.type ?: (ms.receiver.type ?: error("Receiver type is unknown during JS codegen"))
