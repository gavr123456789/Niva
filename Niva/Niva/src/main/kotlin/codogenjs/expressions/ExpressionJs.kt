package main.codogenjs

import main.frontend.parser.types.ast.*

fun Expression.generateJsExpression(withNullChecks: Boolean = false, isArgument: Boolean = false): String = buildString {
    when (this@generateJsExpression) {
        is ExpressionInBrackets -> {
            append("(")
            append(expr.generateJsExpression(withNullChecks))
            append(")")
        }
        is MessageSend -> append(generateJsMessageCall())
        is IdentifierExpr -> {
            if (names.count() == 1) append(name.ifJsKeywordPrefix())
            else append(names.dropLast(1).joinToString("."), ".", name.ifJsKeywordPrefix())
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
        is DotReceiver -> append("this")

        is ListCollection -> {
            append("[")
            append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true, isArgument = true) })
            append("]")
        }
        is SetCollection -> {
            append("new Set([")
            append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true, isArgument = true) })
            append("]) ")
        }
        is MapCollection -> {
            append("new Map([")
            append(initElements.joinToString(", ") { "[" + it.first.generateJsExpression(
                withNullChecks = true,
                isArgument = true
            ) + ", " + it.second.generateJsExpression(withNullChecks = true, isArgument = true) + "]" })
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
                        val first = ifNode.ifBranches.first()
                        append("if (", first.ifExpression.generateJsExpression(), ") {\n")
                        when (first) {
                            is IfBranch.IfBranchSingleExpr -> {
                                append(first.thenDoExpression.generateJsExpression().addIndentationForEachStringJs(1), "\n")
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyCode = codegenJs(first.body.statements, 1)
                                if (bodyCode.isNotEmpty()) {
                                    append(bodyCode.addIndentationForEachStringJs(1), "\n")
                                }
                            }
                        }
                        append("}")

                        // else if ветки
                        ifNode.ifBranches.drop(1).forEach { br ->
                            append(" else if (", br.ifExpression.generateJsExpression(), ") {\n")
                            when (br) {
                                is IfBranch.IfBranchSingleExpr -> {
                                    append(br.thenDoExpression.generateJsExpression().addIndentationForEachStringJs(1), "\n")
                                }
                                is IfBranch.IfBranchWithBody -> {
                                    val bodyCode = codegenJs(br.body.statements, 1)
                                    if (bodyCode.isNotEmpty()) {
                                        append(bodyCode.addIndentationForEachStringJs(1), "\n")
                                    }
                                }
                            }
                            append("}")
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
            // Switch как выражение → IIFE со switch и return в каждой ветке
            val sw = this@generateJsExpression
            when (sw.kind) {
                ControlFlowKind.ExpressionTypeMatch -> TODO()
                ControlFlowKind.StatementTypeMatch -> TODO()
                ControlFlowKind.Expression -> {append("(() => {\n")
                    append("    switch (", sw.switch.generateJsExpression(), ") {\n")

                    sw.ifBranches.forEach { br ->
                        // У одной ветки может быть несколько значений (otherIfExpressions)
                        val allConds = listOf(br.ifExpression) + br.otherIfExpressions
                        allConds.forEach { cond ->
                            append("        case ", cond.generateJsExpression(), ":\n")
                        }

                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> {
                                append("            return (", br.thenDoExpression.generateJsExpression(), ");\n")
                            }
                            is IfBranch.IfBranchWithBody -> {
                                val bodyCode = codegenJs(br.body.statements, 2)
                                if (bodyCode.isNotEmpty()) {
                                    append(bodyCode.addIndentationForEachStringJs(1), "\n")
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
                            append("            return (", elseExpr.generateJsExpression(), ");\n")
                        } else {
                            val elseCode = codegenJs(elseBranch, 2)
                            if (elseCode.isNotEmpty()) {
                                append(elseCode.addIndentationForEachStringJs(1), "\n")
                            }
                        }
                    }

                    append("    }\n")
                    append("})()")
                }
                ControlFlowKind.Statement -> {
                    // Обычный switch-statement без IIFE
                    buildString {
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
            if (statements.size == 1 && statements[0] is Expression) {
                append((statements[0] as Expression).generateJsExpression())
            } else {
                append("{\n")
                append(codegenJs(statements, 1))
                append("\n}")
            }
        }

        is StaticBuilder -> append("/* builder call is not supported in JS codegen yet */")
        is MethodReference -> append("/* method reference is not supported in JS codegen yet */")
    }
}
