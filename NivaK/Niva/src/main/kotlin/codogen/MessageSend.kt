package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.*

fun MessageSend.generateMessageCall(): String {

    val b = StringBuilder()

    if (this.messages.isEmpty()) {
        this.token.compileError("Message list for ${this.str} can't be empty")
    }

    this.messages.forEachIndexed { i, it ->
        when (it) {
            is UnaryMsg -> b.append(generateSingleUnary(i, receiver, it))
            is BinaryMsg -> b.append(generateSingleBinary(i, receiver, it))
            is KeywordMsg -> b.append(generateSingleKeyword(i, receiver, it))
        }
    }
    return b.toString()


    // Unary
    // 4 inc = 4.inc()
    // 4 inc dec = 4.inc().dec()

    // Binary
    // 4 + 4 = 4 + 4
    // 4 inc + 4 dec = 4.inc() + 4.dec()
}


fun generateSingleKeyword(i: Int, receiver: Receiver, keywordMsg: KeywordMsg) = buildString {

    val receiverCode =
        if (keywordMsg.kind == KeywordLikeType.Constructor)
            receiver.generateExpression()
        else {
            if (receiver !is ExpressionInBrackets)
                "(" + receiver.generateExpression() + ")"
            else
                receiver.generateExpression()
        }
    when (keywordMsg.kind) {
        KeywordLikeType.Keyword -> {
            if (i == 0) {
                append(receiverCode, ".")
            }
            append(keywordMsg.selectorName)
        }

        KeywordLikeType.Constructor -> {
            if (i == 0) {
                append(receiverCode)
            }
//            append(keywordMsg.receiver.str)
        }

        KeywordLikeType.Setter -> {
            // emptyWallet money: 20
            // emptyWallet.money = 20
            if (keywordMsg.args.count() != 1) {
                keywordMsg.token.compileError("Setters must have only one argument")
            }
            val valueArg = keywordMsg.args[0]
            if (receiver is IdentifierExpr) {
                append(receiver.name, ".", valueArg.selectorName, " = ")
//                val valueCode = valueArg.generateCallPair()
//                append(valueCode)
            } else {
                TODO()
            }
        }

        KeywordLikeType.ForCodeBlock -> {
            // if whileTrue we still need to add .name
            if (keywordMsg.selectorName == "whileTrue" || keywordMsg.selectorName == "whileFalse") {
                append(receiverCode, ".", keywordMsg.selectorName)
            } else {
                append(receiverCode)
            }
        }
    }

    append("(")

    // generate args
    keywordMsg.args.forEachIndexed { i, it ->

        val expressionStr = it.keywordArg.generateExpression()
        append(expressionStr)
        if (i != keywordMsg.args.count() - 1)
            append(", ")

    }

    append(")")
}

private fun KeywordArgAndItsMessages.generateCallPair(): String {
    // from: 1 to: 2
    // from = 1, to = 2
    return keywordArg.generateExpression()
//    return keywordArg.str
//    return "$selectorName = ${keywordArg.str}"
}

fun generateSingleUnary(i: Int, receiver: Receiver, it: UnaryMsg) = buildString {
    if (i == 0) {
        val receiverCode = receiver.generateExpression()
        append(receiverCode)
    }
    when (it.kind) {
        UnaryMsgKind.Unary -> append(".${it.selectorName}()")
        UnaryMsgKind.Getter -> append(".${it.selectorName}")
        UnaryMsgKind.ForCodeBlock -> append("()")
    }
}

fun generateUnarySends(receiver: Receiver, messages: List<UnaryMsg>) = buildString {

    messages.forEachIndexed { i, it ->
        append(generateSingleUnary(i, it.receiver, it))
    }
    if (messages.isEmpty()) {
        append(receiver.generateExpression())
    }

//    return if (messages.count() == 1) {
//        val unaryMsg = messages[0]
//        // 1 inc
//        "${receiver.str}.${unaryMsg.selectorName}()"
//    } else {
//        buildString {
//            append(receiver.str)
//            messages.forEach {
//                // 1 inc inc dec
//                // 1.inc().inc().dec()
//                append(".${it.selectorName}()")
//            }
//        }
//    }
}

// 1 + 2 |> inc |> to: "sas"
// can be unary after binary
fun generateBinarySend(receiver: Receiver, messages: List<BinaryMsg>) = buildString {
    messages.forEachIndexed { i, it ->
        append(generateSingleBinary(i, it.receiver, it))
    }
//    if (messages.isEmpty()) {
//        append(receiver.str)
//    }
}

fun generateSingleBinary(
    i: Int,
    receiver: Receiver,
    it: BinaryMsg
) = buildString {
    if (i == 0) {
        // 1 inc + 2 dec + 3 sas
        // 1 inc^ + 2 dec + 3 sas

        append(generateUnarySends(receiver, it.unaryMsgsForReceiver))
        append(" ${it.selectorName} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    } else {
        append(" ${it.selectorName} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    }
}
