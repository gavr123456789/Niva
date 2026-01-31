package main.codogenjs

import main.frontend.parser.types.ast.*
import main.utils.GlobalVariables

fun Expression.generateJsExpression(withNullChecks: Boolean = false, forceExpressionContext: Boolean = false): String = buildString {
    when (this@generateJsExpression) {
        is ExpressionInBrackets -> {
            append("(")
            append(expr.generateJsExpression(withNullChecks, forceExpressionContext))
            append(")")
        }
        is MessageSend -> {
            append(jsSourceComment())
            append(generateJsMessageCall())
        }
        is IdentifierExpr -> {
            // replace this with _receiver
            if (names.count() == 1 && name == "this") {
                append("_receiver")
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
        is LiteralExpression.StringExpr -> append(emitJsString(this@generateJsExpression))
        is LiteralExpression.CharExpr -> append(str)
        is LiteralExpression.UnitExpr -> append("undefined")
        is DotReceiver -> append("_receiver")

        is ListCollection -> {
            append("[")
            // {Int} means an empty array of ints
            if (initElements.size != 1 || !(initElements.first() is IdentifierExpr && (initElements.first() as IdentifierExpr).isType)) {
                append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true) })
            }

            append("]")
        }
        is SetCollection -> {
            append("new Set([")
            if (initElements.size != 1 || !(initElements.first() is IdentifierExpr && (initElements.first() as IdentifierExpr).isType)) {
                append(initElements.joinToString(", ") { it.generateJsExpression(withNullChecks = true) })
            }
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
            // If expressions to IIFE
            val ifNode = this@generateJsExpression
            val effectiveKind = if (forceExpressionContext && ifNode.kind == ControlFlowKind.Statement) {
                ControlFlowKind.Expression
            } else {
                ifNode.kind
            }
            when (effectiveKind) {
                ControlFlowKind.ExpressionTypeMatch -> TODO()
                ControlFlowKind.StatementTypeMatch -> TODO()
                ControlFlowKind.Expression -> {
                    append("(() => {")

                    // main if / else if branches
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

                    // else branch (if any)
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
                    // usual if-statement with no IIFE
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

                        // else if branches
                        ifNode.ifBranches.drop(1).forEach { br ->
                            append(" else if (", br.ifExpression.generateJsExpression(), ") {\n")
                            generateIfBranch(br)
                        }

                        // else-branch
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
            // switch for values \ type-match
            val sw = this@generateJsExpression
            val effectiveKind = if (forceExpressionContext && sw.kind == ControlFlowKind.Statement) {
                ControlFlowKind.Expression
            } else {
                sw.kind
            }
            when (effectiveKind) {
                ControlFlowKind.ExpressionTypeMatch -> {
                    // type-match as expression via IIFE and instanceof
                    append("(() => {\n")
                    append("    const __tmp = ", sw.switch.generateJsExpression(), ";\n")

                    sw.ifBranches.forEachIndexed { index, br ->
                        appendTypeMatchCondition(index, br, "__tmp")

                        appendIfBranchWithReturn(br, forceExpressionContext = true)
                        append("    }\n")
                    }

                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("    else {\n")
                        appendElseBranchWithReturn(elseBranch, forceExpressionContext = true)
                        append("    }\n")
                    }

                    append("})()")
                }
                ControlFlowKind.StatementTypeMatch -> {
                    // type-match as statement via if/else-if/else with instanceof
                    append("{")
                    append("\n")
                    append("    const __tmp = ", sw.switch.generateJsExpression(), ";\n")

                    sw.ifBranches.forEachIndexed { index, br ->
                        appendTypeMatchCondition(index, br, "__tmp")
                        appendIfBranchNoReturn(br)
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
                    // switch as expr → IIFE via switch and return in each branch
                    append("(() => {\n")
                    append("    switch (", sw.switch.generateJsExpression(), ") {\n")

                    sw.ifBranches.forEach { br ->
                        // one branch may have multiple values (otherIfExpressions)
                        val allConds = listOf(br.ifExpression) + br.otherIfExpressions
                        allConds.forEach { cond ->
                            append("        case ", cond.generateJsExpression(), ":\n")
                        }

                        appendIfBranchWithReturn(br, forceExpressionContext = true)
                    }

                    // default
                    val elseBranch = sw.elseBranch
                    if (elseBranch != null) {
                        append("        default:\n")
                        appendElseBranchWithReturn(elseBranch, forceExpressionContext = true)
                    }

                    append("    }\n")
                    append("})()")
                }
                ControlFlowKind.Statement -> {
                    // usual switch-statement with no IIFE
                    append("switch (", sw.switch.generateJsExpression(), ") {\n")

                    sw.ifBranches.forEach { br ->
                        val allConds = listOf(br.ifExpression) + br.otherIfExpressions
                        allConds.forEach { cond ->
                            append("    case ", cond.generateJsExpression(), ":\n")
                        }

                        appendIfBranchNoReturn(br)
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

        is StaticBuilder -> append("/* builder call is not supported in JS codegen yet */")

        // probably never triggered
        is Message -> {
            append(jsSourceComment())
            append(generateJsAsCall())
        }

        is CodeBlock -> {
            // lambda → JS-function
            val lambdaType = type as? frontend.resolver.Type.Lambda

            // lambda parameter names:
            // 1) try to get them from AST (inputList), as was before;
            // 2) if AST-parameters are absent (syntax like `[ it echo ]`),
            //    try to restore names from lambda type (Type.Lambda.args),
            //    which was already built by resolver (including implicit `it`).
            val argsFromAst: List<String> = when {
                // extension lambdas: first argument in type is `this`,
                // it will be receiver on call and should not be in function params list
                lambdaType?.extensionOfType != null -> inputList.drop(1).map { it.name }
                else -> inputList.map { it.name }
            }

            val finalArgNames: List<String> = if (argsFromAst.isNotEmpty()) {
                argsFromAst
            } else if (lambdaType != null) {
                val argsFromType = if (lambdaType.extensionOfType != null)
                    lambdaType.args.drop(1) // skip `this`
                else
                    lambdaType.args

                argsFromType.map { it.name }
            } else {
                emptyList()
            }

            append("(")
            append(finalArgNames.joinToString(", ") { it.ifJsKeywordPrefix() })
            append(") => ")

            // last expr returns
            if (statements.size == 1 && statements[0] is Expression && (statements[0] as Expression).type?.name != "Unit") { // remove () => if () {}, since if isn't expression
                // short form: () => expr
                append((statements[0] as Expression).generateJsExpression())
            } else {
                append("{\n")

                val lastIndex = statements.lastIndex
                statements.forEachIndexed { index, st ->
                    when {
                        // last expr -> return expr
                        index == lastIndex && st is Expression && lambdaType?.returnType?.name != "Unit" -> {
                            append("    return (")
                            append(st.generateJsExpression(), ")")
                        }
                        st is Expression -> {
                            val q = st.generateJsExpression().addIndentationForEachStringJs(1)
                            append(q)
                            append(";")
                        }

                        // other statements are generated as is via common codegen
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

        is MethodReference -> append("/* method reference is not supported in JS codegen yet */")
    }
}

// template literal in backticks
private fun emitJsString(node: LiteralExpression.StringExpr): String {
    val lexeme = node.token.lexeme
    val isTriple = lexeme.startsWith("\"\"\"")
    // """ means raw string
    val trim = if (isTriple) 3 else 1
    val raw = if (lexeme.length >= trim * 2) lexeme.substring(trim, lexeme.length - trim) else ""

    val hasNewLine = raw.contains('\n') || isTriple
    return if (hasNewLine) emitAsTemplateLiteral(raw) else emitAsDoubleQuoted(raw)
}

private fun emitAsDoubleQuoted(raw: String): String {
    val sb = StringBuilder()
    sb.append('"')
    raw.forEach { ch ->
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f") // form feed
            else -> sb.append(ch)
        }
    }
    sb.append('"')
    return sb.toString()
}

private fun emitAsTemplateLiteral(raw: String): String {
    val sb = StringBuilder()
    sb.append('`')
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        when (ch) {
            '`' -> sb.append("\\`")
            '\\' -> sb.append("\\\\")
            '$' -> {
                // escaping interpolation
                if (i + 1 < raw.length && raw[i + 1] == '{') {
                    sb.append("\\")
                }
                sb.append('$')
            }
            else -> sb.append(ch)
        }
        i++
    }
    sb.append('`')
    return sb.toString()
}

// TODO, idk wtf is this, was a comment source map generation
private fun Expression.jsSourceComment(): String {
//    val builder = JsCodegenContext.sourceMapBuilder
//    if (builder != null) {
//        val t = this.token
//        builder.addMapping(
//            generatedLine = builder.currentGeneratedLine,
//            generatedColumn = builder.currentGeneratedColumn,
//            sourceFile = t.file,
//            sourceLine = t.line - 1,
//            sourceColumn = 0
//        )
//    }
//
//    return ""

    if (!GlobalVariables.emitSourceComments) return ""
    val t = this.token
    return "\n\t// ${t.file.name}:${t.line}\n"
}

private fun Expression.generateJsTypeMatchCondition(tmpVarName: String): String = when (this) {
    is IdentifierExpr -> {
        val typeName = this.name

        when (typeName) {
            "Int", "Float", "Double", "Long" -> "typeof $tmpVarName === \"number\""

            "String", "Char" -> "typeof $tmpVarName === \"string\""

            "Bool" -> "typeof $tmpVarName === \"boolean\""

            "Any", "Dynamic" -> "true"

            else -> "$tmpVarName instanceof ${typeName.ifJsKeywordPrefix()}"
        }
    }

    else -> "$tmpVarName instanceof ${this.generateJsExpression()}"
}

private fun StringBuilder.appendTypeMatchCondition(index: Int, br: IfBranch, tmpVarName: String) {
    val allTypes = listOf(br.ifExpression) + br.otherIfExpressions
    append(if (index == 0) "    if (" else "    else if (")
    append(allTypes.joinToString(" || ") { it.generateJsTypeMatchCondition(tmpVarName) })
    append(") {\n")
}

private fun StringBuilder.appendIfBranchNoReturn(br: IfBranch) {
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
}

private fun StringBuilder.appendIfBranchWithReturn(br: IfBranch, forceExpressionContext: Boolean = false) {
    when (br) {
        is IfBranch.IfBranchSingleExpr -> {
            appendElseBranchWithReturn(listOf(br.thenDoExpression), forceExpressionContext)
        }
        is IfBranch.IfBranchWithBody -> {
            appendElseBranchWithReturn(br.body.statements, forceExpressionContext)
        }
    }
}

private fun StringBuilder.appendElseBranchWithReturn(elseBranch: List<Statement>, forceExpressionContext: Boolean = false) {
    if (elseBranch.size == 1 && elseBranch[0] is Expression) {
        val elseExpr = elseBranch[0] as Expression
        val code = elseExpr.generateJsExpression(forceExpressionContext = forceExpressionContext)
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
                val code = last.generateJsExpression(forceExpressionContext = forceExpressionContext)
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
