package codogen

import frontend.parser.MessageDeclarationType
import frontend.parser.types.*

fun codogenKt(declarations: List<Declaration>): String = buildString {
    declarations.forEach {
        append(getStringFromDeclaration(it))
    }


}

fun getStringFromDeclaration(declaration: Declaration): String = buildString {
    when (declaration) {
        is MessageCall -> append(declaration.generateMessageCall())
        is VarDeclaration -> append(declaration.generateVarDeclaration())
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
        is MessageCall -> this.generateMessageCall()
        is IdentifierExpr -> this.str
        is LiteralExpression.FalseExpr -> "false"
        is LiteralExpression.TrueExpr -> "true"
        is LiteralExpression.FloatExpr -> this.str
        is LiteralExpression.IntExpr -> this.str
        is LiteralExpression.StringExpr -> this.str
        is ListCollection -> TODO()
    }

}

fun MessageCall.generateMessageCall(): String {

    return when (this.mainMessageType) {
        MessageDeclarationType.Unary -> generateUnaryCall(receiver, messages)
        MessageDeclarationType.Binary -> generateBinaryCall(receiver, messages)
        MessageDeclarationType.Keyword -> generateKeywordCall(receiver, messages)
    }

    // Unary
    // 4 inc = 4.inc()
    // 4 inc dec = 4.inc().dec()

    // Binary
    // 4 + 4 = 4 + 4
    // 4 inc + 4 dec = 4.inc() + 4.dec()

}


fun generateKeywordCall(receiver: Receiver, messages: List<Message>): String {
    return buildString {
        // when there will be cascade, it will fail
        assert(messages.count() == 1)
        val keywordMsg = messages[0] as KeywordMsg

        append(receiver.str, ".")

        // 1 from: 2 inc to: 3
        // 1.fromTo(from = 2.inc(), to = 3)

        // 1.^
        // generate function name
        append(keywordMsg.selectorName)
        // 1.fromTo

        append("(")

        keywordMsg.args.forEachIndexed { i, it ->
            if (it.unaryOrBinaryMsgsForArg.isNotEmpty()) {
                append(it.selectorName, " = ")
                if (it.unaryOrBinaryMsgsForArg[0] is BinaryMsg) {
                    val q = this.toString()
                    append(generateBinaryCall(it.keywordArg, it.unaryOrBinaryMsgsForArg))
                    val w = this.toString()

                } else if (it.unaryOrBinaryMsgsForArg[0] is UnaryMsg) {
                    append(generateUnaryCall(it.keywordArg, it.unaryOrBinaryMsgsForArg))
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


fun generateUnaryCall(receiver: Receiver, messages: List<Message>): String {

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

fun generateBinaryCall(receiver: Receiver, messages: List<Message>): String {
    // 1 inc dec + 2 inc dec + 5 sas
    // 1.inc().dec() + 2.inc().dec() + 5.sas()
    // 1             + 2             + 5
    //  .inc().dec()    .inc().dec()    .sas()

    return buildString {

        // TODO make messageCall Union
        (messages as List<BinaryMsg>).forEachIndexed { i, it ->
            if (i == 0) {
                // 1 inc + 2 dec + 3 sas
                // 1 inc^ + 2 dec + 3 sas
                append(generateUnaryCall(receiver, it.unaryMsgsForReceiver))
                append(" ${it.selectorName} ")
                append(generateUnaryCall(it.argument, it.unaryMsgsForArg))
            } else {
                append(" ${it.selectorName} ")
                append(generateUnaryCall(it.argument, it.unaryMsgsForArg))
            }
        }
    }

}

