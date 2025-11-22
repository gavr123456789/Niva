package main.codogenjs

import main.frontend.parser.types.ast.*

fun Expression.generateJsExpression(withNullChecks: Boolean = false): String = buildString {
    when (this@generateJsExpression) {
        is ExpressionInBrackets -> {
            append("(")
            append(expr.generateJsExpression(withNullChecks))
            append(")")
        }
        is MessageSend -> append(generateJsMessageCall())
        is IdentifierExpr -> {
            // Внутри методов Niva `this` ссылается на ресивер, в JS это параметр `receiver`
            if (names.count() == 1 && name == "this") {
                append("receiver")
            } else if (names.count() == 1) {
                append(name.ifJsKeywordPrefix())
            } else {
                append(names.dropLast(1).joinToString("."), ".", name.ifJsKeywordPrefix())
            }
        }

        is LiteralExpression.FalseExpr -> append("false")
        is LiteralExpression.TrueExpr -> append("true")
        is LiteralExpression.NullExpr -> append("null")
        is LiteralExpression.FloatExpr -> append(str)
        is LiteralExpression.DoubleExpr -> append(str)
        is LiteralExpression.IntExpr -> append(str)
        is LiteralExpression.StringExpr -> append(str)
        is LiteralExpression.CharExpr -> append(str)
        is LiteralExpression.UnitExpr -> append("undefined")
        is DotReceiver -> append("receiver")

        is ListCollection -> {
            append("[")
            append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true) })
            append("]")
        }
        is SetCollection -> {
            append("new Set([")
            append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true) })
            append("]) ")
        }
        is MapCollection -> {
            append("new Map([")
            append(initElements.joinToString(", ") { "[" + it.first.generateJsExpression(
                withNullChecks = true
            ) + ", " + it.second.generateJsExpression(withNullChecks = true) + "]" })
            append("]) ")
        }

        is ControlFlow.If -> {
            // If как выражение → IIFE, чтобы вернуть значение
            val ifNode = this@generateJsExpression
            when (ifNode.kind) {
                ControlFlowKind.ExpressionTypeMatch -> TODO()
                ControlFlowKind.StatementTypeMatch -> TODO()
                ControlFlowKind.Expression -> {
                    append("(() => {")

                    // Основные ветки if / else if
                    ifNode.ifBranches.forEach { br ->
                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                append(" if (", br.ifExpression.generateJsExpression(), ") ")
                                append("return (", br.thenDoExpression.generateJsExpression(), ");")
                            }
                            is IfBranch.IfBranchWithBody -> {
                                append(" if (", br.ifExpression.generateJsExpression(), ") {")
                                val bodyCode = codegenJs(br.body.statements, 1)
                                if (bodyCode.isNotEmpty()) {
                                    append("\n")
                                    append(bodyCode.addIndentationForEachStringJs(1))
                                    append("\n")
                                }
                                append("}")
                            }
                        }
                    }

                    // else-ветка (если есть)
                    val elseBranch = ifNode.elseBranch
                    if (elseBranch != null) {
                        if (elseBranch.size == 1 && elseBranch[0] is Expression) {
                            val elseExpr = elseBranch[0] as Expression
                            append(" return (", elseExpr.generateJsExpression(), ");")
                        } else {
                            val elseCode = codegenJs(elseBranch, 1)
                            if (elseCode.isNotEmpty()) {
                                append("\n")
                                append(elseCode.addIndentationForEachStringJs(1))
                            }
                        }
                    }

                    append(" })()")
                }
                ControlFlowKind.Statement -> {
                    // Обычный if-statement без IIFE
                    val ifNode = ifNode
                    buildString {
                        val generateIfBranch = { ifBranch: IfBranch ->
                            when (ifBranch) {
                                is IfBranch.IfBranchSingleExpr -> {
                                    append(ifBranch.thenDoExpression.generateJsExpression().addIndentationForEachStringJs(1), "\n")
                                }
                                is IfBranch.IfBranchWithBody -> {
                                    val bodyCode = codegenJs(ifBranch.body.statements, 1)
                                    if (bodyCode.isNotEmpty()) {
                                        append(bodyCode.addIndentationForEachStringJs(1), "\n")
                                    }
                                }
                            }
                            append("}")
                        }
                        val first = ifNode.ifBranches.first()
                        append("if (", first.ifExpression.generateJsExpression(), ") {\n")
                        generateIfBranch(first)
                        append("}")

                        // else if ветки
                        ifNode.ifBranches.drop(1).forEach { br ->
                            append(" else if (", br.ifExpression.generateJsExpression(), ") {\n")
                            generateIfBranch(br)
                        }

                        // else-ветка
                        val elseBranch = ifNode.elseBranch
                        if (elseBranch != null) {
                            append(" else {\n")
                            val elseCode = codegenJs(elseBranch, 1)
                            if (elseCode.isNotEmpty()) {
                                append(elseCode.addIndentationForEachStringJs(1), "\n")
                            }
                            append("}")
                        }
                    }
                }
            }
        }
        is ControlFlow.Switch -> {
            // Switch для значений и type-match
            val sw = this@generateJsExpression
            when (sw.kind) {
                ControlFlowKind.ExpressionTypeMatch -> {
                    // type-match как выражение через IIFE и instanceof
                    append("(() => {\n")
                    append("    const __tmp = ", sw.switch.generateJsExpression(), ";\n")

                    sw.ifBranches.forEachIndexed { index, br ->
                        val allTypes = listOf(br.ifExpression) + br.otherIfExpressions
                        append(if (index == 0) "    if (" else "    else if (")
                        append(allTypes.joinToString(" || ") { it.generateJsTypeMatchCondition("__tmp") })
                        append(") {\n")

                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                val expr = br.thenDoExpression
                                val code = expr.generateJsExpression()
                                if (expr is BinaryMsg) {
                                    append("            return ", code, ";\n")
                                } else {
                                    append("            return (", code, ");\n")
                                }
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyStmts = br.body.statements
                                if (bodyStmts.isNotEmpty()) {
                                    val leading = bodyStmts.dropLast(1)
                                    val last = bodyStmts.last()

                                    if (leading.isNotEmpty()) {
                                        val leadingCode = codegenJs(leading, 2)
                                        if (leadingCode.isNotEmpty()) {
                                            append(leadingCode.addIndentationForEachStringJs(1), "\n")
                                        }
                                    }

                                    when (last) {
                                        is Expression -> {
                                            val code = last.generateJsExpression()
                                            if (last is BinaryMsg) {
                                                append("            return ", code, ";\n")
                                            } else {
                                                append("            return (", code, ");\n")
                                            }
                                        }
                                        else -> {
                                            val lastCode = codegenJs(listOf(last), 2)
                                            if (lastCode.isNotEmpty()) {
                                                append(lastCode.addIndentationForEachStringJs(1), "\n")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        append("    }\n")
                    }

                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("    else {\n")
                        if (elseBranch.size == 1 && elseBranch[0] is Expression) {
                            val elseExpr = elseBranch[0] as Expression
                            val code = elseExpr.generateJsExpression()
                            if (elseExpr is BinaryMsg) {
                                append("            return ", code, ";\n")
                            } else {
                                append("            return (", code, ");\n")
                            }
                        } else if (elseBranch.isNotEmpty()) {
                            val leading = elseBranch.dropLast(1)
                            val last = elseBranch.last()

                            if (leading.isNotEmpty()) {
                                val leadingCode = codegenJs(leading, 2)
                                if (leadingCode.isNotEmpty()) {
                                    append(leadingCode.addIndentationForEachStringJs(1), "\n")
                                }
                            }

                            when (last) {
                                is Expression -> {
                                    val code = last.generateJsExpression()
                                    if (last is BinaryMsg) {
                                        append("            return ", code, ";\n")
                                    } else {
                                        append("            return (", code, ");\n")
                                    }
                                }
                                else -> {
                                    val lastCode = codegenJs(listOf(last), 2)
                                    if (lastCode.isNotEmpty()) {
                                        append(lastCode.addIndentationForEachStringJs(1), "\n")
                                    }
                                }
                            }
                        }
                        append("    }\n")
                    }

                    append("})()")
                }
                ControlFlowKind.StatementTypeMatch -> {
                    // type-match как statement через if/else-if/else с instanceof
                    append("{")
                    append("\n")
                    append("    const __tmp = ", sw.switch.generateJsExpression(), ";\n")

                    sw.ifBranches.forEachIndexed { index, br ->
                        val allTypes = listOf(br.ifExpression) + br.otherIfExpressions
                        append(if (index == 0) "    if (" else "    else if (")
                        append(allTypes.joinToString(" || ") { it.generateJsTypeMatchCondition("__tmp") })
                        append(") {\n")

                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                append("        ", br.thenDoExpression.generateJsExpression(), "\n")
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyCode = codegenJs(br.body.statements, 1)
                                if (bodyCode.isNotEmpty()) {
                                    append(bodyCode.addIndentationForEachStringJs(1), "\n")
                                }
                            }
                        }
                        append("    }\n")
                    }

                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("    else {\n")
                        val elseCode = codegenJs(elseBranch, 1)
                        if (elseCode.isNotEmpty()) {
                            append(elseCode.addIndentationForEachStringJs(1), "\n")
                        }
                        append("    }\n")
                    }

                    append("}")
                }
                ControlFlowKind.Expression -> {
                    // Switch как выражение → IIFE со switch и return в каждой ветке
                    append("(() => {\n")
                    append("    switch (", sw.switch.generateJsExpression(), ") {\n")

                    sw.ifBranches.forEach { br ->
                        // У одной ветки может быть несколько значений (otherIfExpressions)
                        val allConds = listOf(br.ifExpression) + br.otherIfExpressions
                        allConds.forEach { cond ->
                            append("        case ", cond.generateJsExpression(), ":\n")
                        }

                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                val expr = br.thenDoExpression
                                val code = expr.generateJsExpression()
                                if (expr is BinaryMsg) {
                                    append("            return ", code, ";\n")
                                } else {
                                    append("            return (", code, ");\n")
                                }
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyStmts = br.body.statements
                                if (bodyStmts.isNotEmpty()) {
                                    val leading = bodyStmts.dropLast(1)
                                    val last = bodyStmts.last()

                                    // все, кроме последнего — обычные стейтменты
                                    if (leading.isNotEmpty()) {
                                        val leadingCode = codegenJs(leading, 2)
                                        if (leadingCode.isNotEmpty()) {
                                            append(leadingCode.addIndentationForEachStringJs(1), "\n")
                                        }
                                    }

                                    // последний Expression должен возвращаться из IIFE
                                    when (last) {
                                        is Expression -> {
                                            val code = last.generateJsExpression()
                                            if (last is BinaryMsg) {
                                                append("            return ", code, ";\n")
                                            } else {
                                                append("            return (", code, ");\n")
                                            }
                                        }
                                        else -> {
                                            val lastCode = codegenJs(listOf(last), 2)
                                            if (lastCode.isNotEmpty()) {
                                                append(lastCode.addIndentationForEachStringJs(1), "\n")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // default
                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("        default:\n")
                        if (elseBranch.size == 1 && elseBranch[0] is Expression) {
                            val elseExpr = elseBranch[0] as Expression
                            val code = elseExpr.generateJsExpression()
                            if (elseExpr is BinaryMsg) {
                                append("            return ", code, ";\n")
                            } else {
                                append("            return (", code, ");\n")
                            }
                        } else if (elseBranch.isNotEmpty()) {
                            val leading = elseBranch.dropLast(1)
                            val last = elseBranch.last()

                            if (leading.isNotEmpty()) {
                                val leadingCode = codegenJs(leading, 2)
                                if (leadingCode.isNotEmpty()) {
                                    append(leadingCode.addIndentationForEachStringJs(1), "\n")
                                }
                            }

                            when (last) {
                                is Expression -> {
                                    val code = last.generateJsExpression()
                                    if (last is BinaryMsg) {
                                        append("            return ", code, ";\n")
                                    } else {
                                        append("            return (", code, ");\n")
                                    }
                                }
                                else -> {
                                    val lastCode = codegenJs(listOf(last), 2)
                                    if (lastCode.isNotEmpty()) {
                                        append(lastCode.addIndentationForEachStringJs(1), "\n")
                                    }
                                }
                            }
                        }
                    }

                    append("    }\n")
                    append("})()")
                }
                ControlFlowKind.Statement -> {
                    // Обычный switch-statement без IIFE
                    append("switch (", sw.switch.generateJsExpression(), ") {\n")

                    sw.ifBranches.forEach { br ->
                        val allConds = listOf(br.ifExpression) + br.otherIfExpressions
                        allConds.forEach { cond ->
                            append("    case ", cond.generateJsExpression(), ":\n")
                        }

                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                append("        ", br.thenDoExpression.generateJsExpression(), "\n")
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyCode = codegenJs(br.body.statements, 1)
                                if (bodyCode.isNotEmpty()) {
                                    append(bodyCode.addIndentationForEachStringJs(1), "\n")
                                }
                            }
                        }
                        append("        break;\n")
                    }

                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("    default:\n")
                        val elseCode = codegenJs(elseBranch, 1)
                        if (elseCode.isNotEmpty()) {
                            append(elseCode.addIndentationForEachStringJs(1), "\n")
                        }
                    }

                    append("}")

                }
            }

        }

        is BinaryMsg -> append(generateJsAsCall())
        is KeywordMsg -> append(generateJsAsCall())
        is UnaryMsg -> append(generateJsAsCall())

        is CodeBlock -> {
            // лямбда → JS-функция
            val withTypes = (type as? frontend.resolver.Type.Lambda)
            val argsList = if (withTypes?.extensionOfType != null) {
                // первый аргумент будет ресивером при вызове, но в определении лямбды он не нужен
                inputList.drop(1)
            } else inputList

            append("(")
            append(argsList.joinToString(", ") { it.name.ifJsKeywordPrefix() })
            append(") => ")

            // Семантика CodeBlock как в котлине: последний Expression возвращается
            if (statements.size == 1 && statements[0] is Expression) {
                // Краткая форма: () => expr
                append((statements[0] as Expression).generateJsExpression())
            } else {
                append("{\n")

                val lastIndex = statements.lastIndex
                statements.forEachIndexed { index, st ->
                    when {
                        // Последний Expression → return expr
                        index == lastIndex && st is Expression -> {
                            append("    ")
                            append("return ")
                            append(st.generateJsExpression())
                        }

                        // Промежуточные Expression → expr;
                        st is Expression -> {
                            append("    ")
                            append(st.generateJsExpression())
                            append(";")
                        }

                        // Остальные стейтменты генерим как есть через общий кодоген
                        else -> {
                            val code = codegenJs(listOf(st), 1)
                            if (code.isNotEmpty()) {
                                append(code)
                            }
                        }
                    }

                    if (index != lastIndex) {
                        append("\n")
                    }
                }

                append("\n}")
            }
        }

        is StaticBuilder -> append("/* builder call is not supported in JS codegen yet */")
        is MethodReference -> append("/* method reference is not supported in JS codegen yet */")
    }
}

// Вспомогательная функция для type-match в JS.
// Принимает выражение-типа (например, Int, String, Bool, Person) и имя временной переменной с значением.
// Возвращает строку с JS-условием для проверки типа (__tmp).
private fun Expression.generateJsTypeMatchCondition(tmpVarName: String): String = when (this) {
    is IdentifierExpr -> {
        // Имя типа без пакета
        val typeName = this.name

        when (typeName) {
            // Числовые типы → typeof === "number"
            "Int", "Float", "Double", "Long" -> "typeof $tmpVarName === \"number\""

            // Строковые типы (Char в JS тоже строка длины 1)
            "String", "Char" -> "typeof $tmpVarName === \"string\""

            // Булев тип
            "Bool" -> "typeof $tmpVarName === \"boolean\""

            // Any/Dynamic — совпадает с любым значением
            "Any", "Dynamic" -> "true"

            else -> "$tmpVarName instanceof ${typeName.ifJsKeywordPrefix()}"
        }
    }

    else -> "$tmpVarName instanceof ${this.generateJsExpression()}"
}
