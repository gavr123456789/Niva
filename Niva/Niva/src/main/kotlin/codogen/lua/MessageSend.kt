package main.codogen.lua

import main.frontend.parser.types.ast.*


fun generateLuaMessage(msg: Message) = buildString {
    when (msg) {
        is UnaryMsg -> {
            generateLuaUnaryMsg(msg)
        }
        is BinaryMsg -> generateLuaBinaryMsg(msg)
        is KeywordMsg -> TODO()
        is StaticBuilder -> TODO()
    }
}
fun MessageSend.generateLuaMessageCall(): String = buildString {
    val receiverCode = receiver.generateLuaExpression()
    append(receiverCode)

    messages.forEachIndexed { i, m ->
        append(generateLuaMessage(m))
    }

    when (this@generateLuaMessageCall) {
        is MessageSendUnary -> {
//            val msg = messages.first() as UnaryMsg // тут везде должно быть не first а форыч по всем, на один уровень выше
//            append(receiverCode)
//            messages.forEach {
//                generateLuaUnaryMsg(it)
//            }
//            append(generateLuaUnaryMsg(msg))
        }
        is MessageSendBinary -> {
            val msg = messages.first() as BinaryMsg
            append(generateLuaBinaryMsg(msg))
        }
        is MessageSendKeyword -> {
            val msg = messages.first() as KeywordMsg
            append(generateLuaKeywordMsg(receiverCode, msg))
        }
    }
}

fun generateLuaUnaryMsg(msg: UnaryMsg): String = buildString {
    when (msg.kind) {
        UnaryMsgKind.Getter -> append(".${msg.selectorName}")
        UnaryMsgKind.ForCodeBlock -> append(":${msg.selectorName}()")
        UnaryMsgKind.Unary -> append(":${msg.selectorName}()")
    }
}

fun generateLuaBinaryMsg(msg: BinaryMsg): String = buildString {
    val argCode = msg.argument.generateLuaExpression()

    // Handle common operators
    when (msg.selectorName) {
        "+" -> append(" + $argCode")
        "-" -> append(" - $argCode")
        "*" -> append(" * $argCode")
        "/" -> append(" / $argCode")
        "%" -> append(" % $argCode")
        "==" -> append(" == $argCode")
        "!=" -> append(" ~= $argCode")
        "<" -> append(" < $argCode")
        ">" -> append(" > $argCode")
        "<=" -> append(" <= $argCode")
        ">=" -> append(" >= $argCode")
        else -> append(":${msg.selectorName}($argCode)")
    }
}

fun generateLuaKeywordMsg(receiver: String, msg: KeywordMsg): String = buildString {
    when {
        msg.args.size == 2 && msg.args.any { it.name == "ifTrue" } && msg.args.any { it.name == "ifFalse" } -> {
            // Handle if/else control flow
            append("if ")
            // Remove any extra parentheses from the condition
            val condition = receiver.trim().removeSurrounding("(", ")")
            append(condition)
            append(" then\n")

            // Get the ifTrue block
            val thenBlock = msg.args.find { it.name == "ifTrue" }?.keywordArg
            if (thenBlock is CodeBlock) {
                val stmt = thenBlock.statements.first()
                if (stmt is Expression) {
                    append("  return ")
                    append(stmt.generateLuaExpression())
                    append("\n")
                }
            }

            // Get the ifFalse block
            val elseBlock = msg.args.find { it.name == "ifFalse" }?.keywordArg
            if (elseBlock is CodeBlock) {
                val stmt = elseBlock.statements.first()
                if (stmt is Expression) {
                    append("else\n")
                    append("  return ")
                    append(stmt.generateLuaExpression())
                    append("\n")
                }
            }

            append("end")
        }
        msg.kind == KeywordLikeType.Constructor -> {
            // For constructors, create a new table with the arguments
            append("${msg.path.last()}:new{")
            append(msg.args.joinToString(", ") { "${it.name} = ${it.keywordArg.generateLuaExpression()}" })
            append("}")
        }
        msg.kind == KeywordLikeType.Setter -> {
            // For setters, use direct assignment
            append("$receiver.${msg.selectorName} = ${msg.args.first().keywordArg.generateLuaExpression()}")
        }
        else -> {
            // For regular method calls with named arguments, pass them as a table
            append("$receiver:${msg.selectorName}({")
            append(msg.args.joinToString(", ") { "${it.name} = ${it.keywordArg.generateLuaExpression()}" })
            append("})")
        }
    }
}
