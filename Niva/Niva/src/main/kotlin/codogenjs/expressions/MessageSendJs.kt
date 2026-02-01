package main.codogenjs

import frontend.resolver.Package
import frontend.resolver.Type
import main.codogen.operatorToString
import main.frontend.parser.types.ast.*



private fun buildJsFuncName(receiverType: Type, message: Message): String {
    val recv = when (receiverType) {
        is Type.UserLike -> buildString {
            if (receiverType.pkg.isNotEmpty() && receiverType.pkg != "core" && receiverType.pkg != "common") {
                append(receiverType.pkg.replace('.', '_').replace("::", "_"), "_")
            }
            append(receiverType.emitName)
            if (receiverType.isMutable) append("__mut")
        }
        is Type.NullableType -> "Nullable"
        else -> receiverType.toJsMangledName()
    }
    val baseName = operatorToString(message.selectorName, null)
    val res = "${recv}__${baseName}"
    if (message.selectorName == "add") {
        java.io.File("/tmp/niva_debug.txt").appendText("DEBUG: buildJsFuncName selector=${message.selectorName} recv=$recv res=$res type=${receiverType.name} emit=${(receiverType as? Type.UserLike)?.emitName} mut=${receiverType.isMutable}\n")
    }
    return res
}

private fun Type.targetPackageName(): String? = when (val t = unwrapNull()) {
    is Type.UserLike -> t.pkg
    is Type.InternalType -> t.pkg
    else -> null
}

private fun Type.jsQualifierFor(currentPkg: Package?): String? {
    val targetPkg = targetPackageName() ?: return null
    val currentName = currentPkg?.packageName
    if (currentName == null || currentName == targetPkg) return null
    return targetPkg.replace('.', '_')
}


private fun getFunctionName(receiverType: Type, message: Message): String {
    val renamedName = message.tryGetRenamedFunctionName()
    if (renamedName != null) {
        return renamedName
    }
    return buildQualifiedJsFuncName(receiverType, message)
}


private fun buildQualifiedJsFuncName(receiverType: Type, message: Message): String {
    val metaForType = message.msgMetaData?.forType
    val declForType = message.declaration?.forType

    fun isDefaultUnknownGeneric(t: Type?): Boolean = t is Type.InternalType && t.name == "UnknownGeneric"

    val pickedForType = when {
        isDefaultUnknownGeneric(metaForType) && declForType is Type.UnknownGenericType -> declForType
        else -> metaForType ?: declForType ?: receiverType
    }

    val baseName = buildJsFuncName(pickedForType, message)

    val currentPkgName = JsCodegenContext.currentPackage?.packageName
    val currentPkgAlias = currentPkgName?.replace('.', '_')

    val rawAlias = message.declaration?.messageData?.pkg
        ?: message.msgMetaData?.pkg
        ?: pickedForType.jsQualifierFor(JsCodegenContext.currentPackage)
    val alias = rawAlias
        ?.replace('.', '_')
        ?.takeUnless { it == currentPkgAlias || it == currentPkgName }

    return if (alias != null) {
        val realAlias = if (alias == "core") "common" else alias
        "$realAlias.$baseName"
    } else {
        baseName
    }
}

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
        "-", "*", "/", "%" -> if (rNum && aNum) "(($recvExpr) $op ($argExpr))" else null
        ">", "<", ">=", "<=" -> if (rNum && aNum) "(($recvExpr) $op ($argExpr))" else null
        "||", "&&" -> if (rBool && aBool) "(($recvExpr) $op ($argExpr))" else null
        else -> null
    }
}

private fun generateIfBranchJs(condition: String, lambda: Expression?, negateCondition: Boolean): String {
    val conditionExpr = if (negateCondition) "!($condition)" else condition
    
    return if (lambda is CodeBlock) {
        buildString {
            append("if (", conditionExpr, ") {\n")
            val bodyCode = codegenJs(lambda.statements, 1)
            if (bodyCode.isNotEmpty()) {
                append(bodyCode, "\n")
            }
            append("}")
        }
    } else {
        val lambdaExpr = lambda?.generateJsExpression(true) ?: "undefined"
        "if ($conditionExpr) { $lambdaExpr() }"
    }
}

private fun generateIfTrueIfFalseBranchJs(lambda: Expression?, resultVarName: String = "__ifResult"): String {
    return buildString {
        if (lambda is CodeBlock) {
            val stmts = lambda.statements
            if (stmts.isNotEmpty()) {
                val leading = stmts.dropLast(1)
                val last = stmts.last()
                
                if (leading.isNotEmpty()) {
                    val leadingCode = codegenJs(leading, 2)
                    if (leadingCode.isNotEmpty()) {
                        append(leadingCode.addIndentationForEachStringJs(1), "\n")
                    }
                }
                
                if (last is Expression) {
                    append("        ", resultVarName, " = ", last.generateJsExpression(), ";\n")
                } else {
                    val lastCode = codegenJs(listOf(last), 2)
                    if (lastCode.isNotEmpty()) {
                        append(lastCode.addIndentationForEachStringJs(1), "\n")
                    }
                }
            }
        } else if (lambda != null) {
            val lambdaExpr = lambda.generateJsExpression(true)
            append("        ", resultVarName, " = ", lambdaExpr, "();\n")
        }
    }
}

private fun generateWhileBranchJs(conditionExpr: String, lambda: Expression?): String {
    return if (lambda is CodeBlock) {
        buildString {
            append("while (", conditionExpr, ") {\n")
            val bodyCode = codegenJs(lambda.statements, 1)
            if (bodyCode.isNotEmpty()) {
                append(bodyCode, "\n")
            }
            append("}")
        }
    } else {
        val lambdaExpr = lambda?.generateJsExpression(true) ?: "undefined"
        "while ($conditionExpr) { $lambdaExpr() }"
    }
}

private fun generateWhileConditionExpr(conditionExpr: String, conditionType: Type?): String {
    return if (conditionType is Type.Lambda) "($conditionExpr)()" else conditionExpr
}

private fun Message.isConstructorCall(): Boolean = declaration is ConstructorDeclaration

fun MessageSend.generateJsMessageCall(): String {
    val b = StringBuilder()
    var currentExpr = receiver.generateJsExpression()
    var currentType = receiver.type ?: messageTypeSafe(this)

    messages.forEach { msg ->
        // pragma emitJs replaces message call with custom js
        // template: $0 = receiver, $1..$N = keyword args
        val emitJs = msg.tryEmitJsReplacement(currentExpr)
        if (emitJs != null) {
            currentExpr = emitJs
            currentType = msg.type ?: currentType
            return@forEach
        }

        when (msg) {
            is UnaryMsg -> {
                if (msg.kind == UnaryMsgKind.Getter) {
                    // field access: p name -> p.name
                    currentExpr = "$currentExpr.${msg.selectorName}"
                    currentType = msg.type ?: currentType
                } else {
                    val name = getFunctionName(currentType, msg)
                    currentExpr = if (msg.isConstructorCall()) "$name()" else "$name($currentExpr)"
                    currentType = msg.type ?: currentType
                }
            }
            is BinaryMsg -> {
                // apply unary messages to receiver
                var recvExpr = currentExpr
                var recvType = currentType
                if (msg.unaryMsgsForReceiver.isNotEmpty()) {
                    msg.unaryMsgsForReceiver.forEach { u ->
                        if (u.kind == UnaryMsgKind.Getter) {
                            recvExpr = "$recvExpr.${u.selectorName}"
                        } else {
                            val name = getFunctionName(recvType, u)
                            recvExpr = "$name($recvExpr)"
                        }
                        recvType = u.type ?: recvType
                    }
                }

                // generate arg and apply its unaries
                var argExpr = msg.argument.generateJsExpression()
                var argType: Type? = msg.argument.type ?: (msg.declaration as? MessageDeclarationBinary)?.arg?.type
                if (msg.unaryMsgsForArg.isNotEmpty()) {
                    msg.unaryMsgsForArg.forEach { u ->
                        if (u.kind == UnaryMsgKind.Getter) {
                            argExpr = "$argExpr.${u.selectorName}"
                        } else {
                            val name = getFunctionName(argType ?: recvType, u)
                            argExpr = "$name($argExpr)"
                        }
                        argType = u.type ?: argType
                    }
                }

                val native = tryEmitNativeBinary(msg.selectorName, recvExpr, recvType, argExpr, argType)
                if (native != null) {
                    currentExpr = native
                } else {
                    val fn = getFunctionName(recvType, msg)
                    currentExpr = if (msg.isConstructorCall()) "$fn($argExpr)" else "$fn($recvExpr, $argExpr)"
                }
                currentType = msg.type ?: recvType
            }
            is KeywordMsg -> {
                // special case for ifTrue:, ifFalse:, ifTrue:ifFalse: (for non local return)
                val selector = msg.selectorName
                val isBoolReceiver = isBool(currentType)
                
                if (isBoolReceiver && (selector == "ifTrue" || selector == "ifFalse" || selector == "ifTrueIfFalse")) {
                    // get lambda arguments
                    val lambdaArgs = msg.args.map { it.keywordArg }
                    
                    when (selector) {
                        "ifTrue" -> {
                            val lambda = lambdaArgs.firstOrNull()
                            currentExpr = generateIfBranchJs(currentExpr, lambda, negateCondition = false).addIndentationForEachStringJs(0)
                            currentType = msg.type ?: currentType
                        }
                        "ifFalse" -> {
                            val lambda = lambdaArgs.firstOrNull()
                            currentExpr = generateIfBranchJs(currentExpr, lambda, negateCondition = true).addIndentationForEachStringJs(0)
                            currentType = msg.type ?: currentType
                        }
                        "ifTrueIfFalse" -> {
                            // IIFE with variable
                            val trueLambda = lambdaArgs.getOrNull(0)
                            val falseLambda = lambdaArgs.getOrNull(1)
                            
                            currentExpr = buildString {
                                append("(() => {\n")
                                append("    let __ifResult = undefined;\n")
                                append("    if (", currentExpr, ") {\n")
                                append(generateIfTrueIfFalseBranchJs(trueLambda))
                                append("    } else {\n")
                                append(generateIfTrueIfFalseBranchJs(falseLambda))
                                append("    }\n")
                                append("    return __ifResult;\n")
                                append("})()")
                            }
                            currentType = msg.type ?: currentType
                        }
                    }
                } else if (selector == "whileTrue" || selector == "whileFalse") {
                    val lambda = msg.args.firstOrNull()?.keywordArg
                    val conditionExpr = generateWhileConditionExpr(currentExpr, currentType)
                    val finalConditionExpr = if (selector == "whileFalse") "!($conditionExpr)" else conditionExpr
                    currentExpr = generateWhileBranchJs(finalConditionExpr, lambda).addIndentationForEachStringJs(0)
                    currentType = msg.type ?: currentType
                } else {
                    // usual KeywordMsg
                    val args = msg.args.map { it.keywordArg.generateJsExpression(true) }

                    if (msg.kind == KeywordLikeType.Constructor) {
                        // call type constructor: Person name: "Alice" age: 24 -> new Person("Alice", 24)
                        val recvType = currentType
                        val typeName = recvType.constructorJsName()
                        currentExpr = buildString {
                            append("new ", typeName, "(")
                            append(args.joinToString(", "))
                            append(")")
                        }
                        currentType = msg.type ?: currentType
                    } else if (msg.kind == KeywordLikeType.CustomConstructor) {
                        val fn = getFunctionName(currentType, msg)
                        currentExpr = buildString {
                            append(fn, "(")
                            append(args.joinToString(", "))
                            append(")")
                        }
                        currentType = msg.type ?: currentType
                    } else {
                        val fn = getFunctionName(currentType, msg)
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

    val emitJs = this.tryEmitJsReplacement(recvExpr)
    if (emitJs != null) return emitJs

    return when (this) {
        is UnaryMsg -> {
            if (this.kind == UnaryMsgKind.Getter) {
                "$recvExpr.${this.selectorName}"
            } else {
                val name = getFunctionName(recvType, this)
                if (this.isConstructorCall()) "$name()" else "$name($recvExpr)"
            }
        }
        is BinaryMsg -> {
            if (unaryMsgsForReceiver.isNotEmpty()) {
                unaryMsgsForReceiver.forEach { u ->
                    if (u.kind == UnaryMsgKind.Getter) {
                        recvExpr = "$recvExpr.${u.selectorName}"
                    } else {
                        val name = getFunctionName(recvType, u)
                        recvExpr = "$name($recvExpr)"
                    }
                    recvType = u.type ?: recvType
                }
            }

            // arg and its unaries
            var argExpr = argument.generateJsExpression()
            var argType: Type? = argument.type ?: (declaration as? MessageDeclarationBinary)?.arg?.type
            if (unaryMsgsForArg.isNotEmpty()) {
                unaryMsgsForArg.forEach { u ->
                    if (u.kind == UnaryMsgKind.Getter) {
                        argExpr = "$argExpr.${u.selectorName}"
                    } else {
                        val name = getFunctionName(argType ?: recvType, u)
                        argExpr = "$name($argExpr)"
                    }
                    argType = u.type ?: argType
                }
            }

            val native = tryEmitNativeBinary(this.selectorName, recvExpr, recvType, argExpr, argType)
            if (native != null) native else {
                val fn = getFunctionName(recvType, this)
                if (this.isConstructorCall()) "$fn($argExpr)" else "$fn($recvExpr, $argExpr)"
            }
        }
        is KeywordMsg -> {
            val selector = this.selectorName
            if (selector == "whileTrue" || selector == "whileFalse") {
                val lambda = this.args.firstOrNull()?.keywordArg
                val conditionExpr = generateWhileConditionExpr(recvExpr, recvType)
                val finalConditionExpr = if (selector == "whileFalse") "!($conditionExpr)" else conditionExpr
                return generateWhileBranchJs(finalConditionExpr, lambda)
            }

            val args = args.map { it.keywordArg.generateJsExpression(true) }

            if (this.kind == KeywordLikeType.Constructor) {
                val typeName = recvType.constructorJsName()
                buildString {
                    append("new ", typeName, "(")
                    append(args.joinToString(", "))
                    append(")")
                }
            } else if (this.kind == KeywordLikeType.CustomConstructor) {
                val fn = getFunctionName(recvType, this)
                buildString {
                    append(fn, "(")
                    append(args.joinToString(", "))
                    append(")")
                }
            } else {
                val fn = getFunctionName(recvType, this)
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
