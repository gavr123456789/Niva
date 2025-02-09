package main.codogen.lua

import main.frontend.parser.types.ast.*

fun Expression.generateLuaExpression(replaceLiteral: String? = null): String = buildString {
    if (isInfoRepl) {
        return@buildString
    }

    append(
        when (this@generateLuaExpression) {
            is ExpressionInBrackets -> generateLuaExpressionInBrackets()
            is MessageSend -> generateLuaMessageCall()
            is IdentifierExpr ->
                if (names.count() == 1) {
                    replaceLiteral ?: name
                } else
                    names.dropLast(1).joinToString(".") + "." + (replaceLiteral ?: name)

            // Literals
            is LiteralExpression.FalseExpr -> "false"
            is LiteralExpression.TrueExpr -> "true"
            is LiteralExpression.NullExpr -> "nil"
            is LiteralExpression.FloatExpr -> str
            is LiteralExpression.DoubleExpr -> str
            is LiteralExpression.IntExpr -> str
            is LiteralExpression.StringExpr -> str
            is LiteralExpression.CharExpr -> str
            is DotReceiver -> "self"

            // Collections
            is ListCollection -> generateLuaList()
            is SetCollection -> generateLuaSet()
            is MapCollection -> generateLuaMap()

            // Control Flow
            is ControlFlow.If -> generateLuaIf()
            is ControlFlow.Switch -> generateLuaSwitch()

            // Messages
            is BinaryMsg -> {
                val msg = this@generateLuaExpression
                val receiverCode = msg.receiver.generateLuaExpression()
                append(receiverCode)
                generateLuaBinaryMsg(msg)
            }
            is KeywordMsg -> {
                val msg = this@generateLuaExpression
                val receiverCode = msg.receiver.generateLuaExpression()
                generateLuaKeywordMsg(receiverCode, msg)
            }
            is UnaryMsg -> {
                val msg = this@generateLuaExpression
                val receiverCode = msg.receiver.generateLuaExpression()
                append(receiverCode)
                generateLuaUnaryMsg(msg)
            }

            // Code blocks and others
            is CodeBlock -> generateLuaCodeBlock()
            is StaticBuilder -> {
                // Generate Lua builder call
                val builderName = name
                val receiverCode = receiverOfBuilder?.generateLuaExpression() ?: builderName
                val argsCode = args.joinToString(", ") { "${it.name} = ${it.keywordArg.generateLuaExpression()}" }
                val action = defaultAction

                if (action != null) {
                    // If there's a default action, create a builder with the action
                    "$receiverCode:${builderName}_builder({ $argsCode }, function(it) ${action.generateLuaCodeBlock()} end)"
                } else {
                    // Simple constructor call
                    "$receiverCode:${builderName}({ $argsCode })"
                }
            }
            is MethodReference -> {
                // Generate Lua method reference
                when (this@generateLuaExpression) {
                    is MethodReference.Unary -> {
                        // For unary methods, create a function that calls the method on self
                        "function(self) return self:${name}() end"
                    }
                    is MethodReference.Binary -> {
                        // For binary operators, create a function that takes an argument
                        "function(self, arg) return self:${name}(arg) end"
                    }
                    is MethodReference.Keyword -> {
                        // For keyword methods, create a function that takes a table of named arguments
                        val argNames = keys.joinToString(", ")
                        "function(self, args) return self:${name}({ $argNames = args.$argNames }) end"
                    }
                }
            }
        }
    )
}

fun ExpressionInBrackets.generateLuaExpressionInBrackets(): String = buildString {
    append("(")
    append(expr.generateLuaExpression())
    append(")")
}

fun ListCollection.generateLuaList(): String = buildString {
    append("{")
    append(initElements.joinToString(", ") { it.generateLuaExpression() })
    append("}")
}

fun SetCollection.generateLuaSet(): String = buildString {
    // In Lua, we'll implement sets as tables with values as keys and true as values
    append("(function() local set = {}; ")
    initElements.forEach { 
        append("set[${it.generateLuaExpression()}] = true; ")
    }
    append("return set end)()")
}

fun MapCollection.generateLuaMap(): String = buildString {
    append("{")
    append(initElements.joinToString(", ") { 
        "[${it.first.generateLuaExpression()}] = ${it.second.generateLuaExpression()}"
    })
    append("}")
}

fun ControlFlow.If.generateLuaIf(): String = buildString {
    ifBranches.forEachIndexed { index, branch ->
        if (index == 0) append("if ") else append("elseif ")
        append(branch.ifExpression.generateLuaExpression())
        append(" then\n")
        when (branch) {
            is IfBranch.IfBranchSingleExpr -> {
                append("  return ${branch.thenDoExpression.generateLuaExpression()}\n")
            }
            is IfBranch.IfBranchWithBody -> {
                append(codegenLua(branch.body.statements, 2))
            }
        }
    }
    if (elseBranch?.isNotEmpty() == true) {
        append("\nelse\n")
        append(codegenLua(elseBranch, 2))
    }
    append("\nend")
}

fun ControlFlow.Switch.generateLuaSwitch(): String = buildString {
    // In Lua, we'll implement switch as if-elseif chain
    val switchExpr = switch.generateLuaExpression()
    ifBranches.forEachIndexed { index, branch ->
        if (index == 0) append("if ") else append("elseif ")
        append("$switchExpr == ${branch.ifExpression.generateLuaExpression()} then\n")
        when (branch) {
            is IfBranch.IfBranchSingleExpr -> {
                append("  return ${branch.thenDoExpression.generateLuaExpression()}\n")
            }
            is IfBranch.IfBranchWithBody -> {
                append(codegenLua(branch.body.statements, 2))
            }
        }
        append("\n")
    }
    if (elseBranch?.isNotEmpty() == true) {
        append("else\n")
        append(codegenLua(elseBranch, 2))
        append("\n")
    }
    append("end")
}
