package codogen

import frontend.parser.types.ast.*

fun codogenKt(statements: List<Statement>): String = buildString {
    statements.forEach {
        append(getStringFromDeclaration(it), "\n")
    }


}

fun getStringFromDeclaration(statement: Statement): String = buildString {
    when (statement) {
        is MessageSend -> append(statement.generateMessageCall())
        is VarDeclaration -> append(statement.generateVarDeclaration())
        is IdentifierExpr -> TODO()
        is LiteralExpression.FalseExpr -> TODO()
        is LiteralExpression.FloatExpr -> TODO()
        is LiteralExpression.IntExpr -> TODO()
        is LiteralExpression.StringExpr -> TODO()
        is LiteralExpression.TrueExpr -> TODO()
        is MessageDeclarationBinary -> TODO()
        is MessageDeclarationKeyword -> TODO()
        is MessageDeclarationUnary -> TODO()
        else -> {
            TODO()
        }
    }
}

private fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateKotlinCode()
    return "val ${this.name} = $valueCode"
}

fun Expression.generateKotlinCode(): String {
    return when (this) {
        is MessageSend -> this.generateMessageCall()
        is IdentifierExpr -> this.str
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> this.str

        is ListCollection -> TODO()
        is ControlFlow.IfExpression -> TODO()
        is ControlFlow.IfStatement -> TODO()
        is ControlFlow.SwitchExpression -> TODO()
        is ControlFlow.SwitchStatement -> TODO()

        // when receiver
        is BinaryMsg -> TODO()
        is KeywordMsg -> TODO()
        is UnaryMsg -> TODO()
    }

}

fun MessageSend.generateMessageCall(): String {

    return when (this) {
        is MessageSendUnary -> generateUnarySend(receiver, messages)
        is MessageSendBinary -> generateBinarySend(receiver, messages)
        is MessageSendKeyword -> generateKeywordSend(receiver, messages)
    }


    // Unary
    // 4 inc = 4.inc()
    // 4 inc dec = 4.inc().dec()

    // Binary
    // 4 + 4 = 4 + 4
    // 4 inc + 4 dec = 4.inc() + 4.dec()
}


fun generateKeywordSend(receiver: Receiver, messages: List<KeywordMsg>): String {
    return buildString {
        // when there will be cascade, it will fail
        assert(messages.count() == 1)
        val keywordMsg = messages[0]

        append(receiver.str, ".")

        // 1 from: 2 inc to: 3
        // 1.fromTo(from = 2.inc(), to = 3)

        // 1.^
        // generate function name
        append(keywordMsg.selectorName)
        // 1.fromTo

        append("(")



        keywordMsg.args.forEachIndexed { i, it ->

            val messageForArg = it.unaryOrBinaryMsgsForArg


            if (messageForArg != null && messageForArg.isNotEmpty()) {
                append(it.selectorName, " = ")
                if (messageForArg[0] is BinaryMsg) {
                    append(generateBinarySend(it.keywordArg, messageForArg as List<BinaryMsg>))

                } else if (messageForArg[0] is UnaryMsg) {
                    append(generateUnarySend(it.keywordArg, messageForArg as List<UnaryMsg>))
                }

                if (i != keywordMsg.args.count() - 1)
                    append(", ")
            } else {
                // no unaryOrBinary args
                append(it.generateCallPair())
                if (i != keywordMsg.args.count() - 1)
                    append(", ")
            }

        }

        append(")")
    }
}

private fun KeywordArgAndItsMessages.generateCallPair(): String {
    // from: 1 to: 2
    // from = 1, to = 2
    return "$selectorName = ${keywordArg.str}"
}


fun generateUnarySend(receiver: Receiver, messages: List<UnaryMsg>): String {

    return if (messages.count() == 1) {
        val unaryMsg = messages[0]
        // 1 inc
        "${receiver.str}.${unaryMsg.selectorName}()"
    } else {
        buildString {
            append(receiver.str)
            messages.forEach {
                // 1 inc inc dec
                // 1.inc().inc().dec()
                append(".${it.selectorName}()")
            }
        }
    }
}


fun generateBinarySend(receiver: Receiver, messages: List<BinaryMsg>): String {
    return buildString {

        // TODO make messageCall Union
        messages.forEachIndexed { i, it ->
            if (i == 0) {

                // 1 inc + 2 dec + 3 sas
                // 1 inc^ + 2 dec + 3 sas
                append(generateUnarySend(receiver, it.unaryMsgsForReceiver))
                append(" ${it.selectorName} ")
                append(generateUnarySend(it.argument, it.unaryMsgsForArg))
            } else {
                append(" ${it.selectorName} ")
                append(generateUnarySend(it.argument, it.unaryMsgsForArg))
            }
        }
    }
}
