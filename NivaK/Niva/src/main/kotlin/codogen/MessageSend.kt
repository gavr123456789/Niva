package codogen

import frontend.parser.types.ast.*

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
//                append(it.selectorName, " = ")
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
    return keywordArg.str
//    return "$selectorName = ${keywordArg.str}"
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
