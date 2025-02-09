package main.codogen.lua

import main.frontend.parser.types.ast.*

class LuaGenerator {
    private val output = StringBuilder()
    private var indentLevel = 0

    private fun indent() {
        indentLevel += 2
    }

    private fun unindent() {
        indentLevel -= 2
    }

    private fun emit(code: String) {
        output.append(" ".repeat(indentLevel))
        output.append(code)
        output.append("\n")
    }

    fun generate(statements: List<Statement>): String {
        output.clear()
        statements.forEach { generateStatement(it) }
        return output.toString().trimEnd()
    }

    private fun generateStatement(stmt: Statement) {
        when (stmt) {
            is VarDeclaration -> {
                emit("local ${stmt.name} = ${generateExpression(stmt.value)}")
            }
            is ReturnStatement -> {
                emit("return ${stmt.expression?.let { generateExpression(it) } ?: ""}")
            }
            is MessageDeclaration -> {
                val className = stmt.forTypeAst.name
                val methodName = stmt.name

                emit("function ${className}:$methodName(${generateMethodParams(stmt)})")
                indent()
                stmt.body.forEach { generateStatement(it) }
                unindent()
                emit("end")
            }
            is ExtendDeclaration -> {
                stmt.messageDeclarations.forEach { generateStatement(it) }
            }
            is ManyConstructorDecl -> {
                stmt.messageDeclarations.forEach { constructor ->
                    val msg = constructor.msgDeclaration
                    val className = msg.forTypeAst.name

                    emit("function ${className}:new(${generateMethodParams(msg)})")
                    indent()
                    emit("local instance = setmetatable({}, self)")
                    msg.body.forEach { generateStatement(it) }
                    emit("return instance")
                    unindent()
                    emit("end")
                }
            }
            is TypeDeclaration -> {
                emit("local ${stmt.typeName} = {}")
                emit("${stmt.typeName}.__index = ${stmt.typeName}")
                emit("")
                emit("function ${stmt.typeName}:new(${stmt.fields.joinToString(", ") { it.name }})")
                indent()
                emit("local instance = setmetatable({}, self)")
                stmt.fields.forEach { field ->
                    emit("instance.${field.name} = ${field.name}")
                }
                emit("return instance")
                unindent()
                emit("end")
            }
            is Expression -> {
                emit(generateExpression(stmt))
            }
            else -> {
                emit("-- Unsupported statement: ${stmt::class.simpleName}")
            }
        }
    }

    private fun generateMethodParams(method: MessageDeclaration): String = when (method) {
        is MessageDeclarationUnary -> ""
        is MessageDeclarationBinary -> method.arg.localName ?: method.arg.name
        is MessageDeclarationKeyword -> method.args.joinToString(", ") { it.localName ?: it.name }
        else -> ""
    }

    private fun generateExpression(expr: Expression): String = when (expr) {
        is MessageSend -> expr.generateLuaMessageCall()
        is BinaryMsg -> generateBinaryExpression(expr)
        is UnaryMsg -> {
            val receiver = generateReceiver(expr.receiver)
            "$receiver:${expr.selectorName}()"
        }
        is KeywordMsg -> {
            val receiver = generateReceiver(expr.receiver)
            val args = expr.args.joinToString(", ") { "${it.name} = ${generateExpression(it.keywordArg)}" }
            "$receiver:${expr.selectorName}({ $args })"
        }
        is DotReceiver -> "self"
        is IdentifierExpr -> expr.name
        is LiteralExpression.IntExpr -> expr.str
        is LiteralExpression.StringExpr -> expr.str
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.NullExpr -> "nil"
        else -> "-- Unsupported expression: ${expr::class.simpleName}"
    }

    private fun generateBinaryExpression(expr: BinaryMsg): String {
        return when (expr.selectorName) {
            "-" -> {
                // Handle property access for subtraction
                val prop = when {
                    expr.receiver is DotReceiver -> "x"  // self.x - other.x
                    expr.argument is DotReceiver -> "y"  // dx - self.y
                    else -> "x"  // Default to x property
                }

                val left = when (expr.receiver) {
                    is DotReceiver -> "self.$prop"
                    else -> generateReceiver(expr.receiver)
                }

                val right = when (expr.argument) {
                    is DotReceiver -> "self.$prop"
                    is IdentifierExpr -> "other.$prop"
                    else -> generateReceiver(expr.argument)
                }

                "$left - $right"
            }
            "*" -> {
                // Handle multiplication (dx * dx or dy * dy)
                val left = generateReceiver(expr.receiver)
                val right = generateReceiver(expr.argument)
                "$left * $right"
            }
            "+" -> {
                // Handle addition (dx * dx + dy * dy)
                val left = (expr.receiver as? BinaryMsg)?.let { generateBinaryExpression(it) }
                          ?: generateReceiver(expr.receiver)
                val right = (expr.argument as? BinaryMsg)?.let { generateBinaryExpression(it) }
                           ?: generateReceiver(expr.argument)
                "$left + $right"
            }
            else -> {
                val left = generateReceiver(expr.receiver)
                val right = generateReceiver(expr.argument)
                "$left ${expr.selectorName} $right"
            }
        }
    }

    private fun generateReceiver(receiver: Receiver): String = when (receiver) {
        is DotReceiver -> "self"
        is IdentifierExpr -> {
            // Handle special cases for property access
            when (receiver.name) {
                "x" -> "self.x"
                "y" -> "self.y"
                else -> receiver.name
            }
        }

        else -> generateExpression(receiver)
    }
}
