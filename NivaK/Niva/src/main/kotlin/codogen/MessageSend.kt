package codogen

import frontend.parser.types.ast.*

fun MessageSend.generateMessageCall(): String {

    val b = StringBuilder()

    this.messages.forEachIndexed { i, it ->
        when (it) {
            is UnaryMsg -> b.append(generateSingleUnary(i, receiver, it))
            is BinaryMsg -> b.append(generateSingleBinary(i, receiver, it))
            is KeywordMsg -> b.append(generateSingleKeyword(i, receiver, it))
        }
    }
    return b.toString()

//    return when (this) {
//        is MessageSendUnary -> generateUnarySend(receiver, messages)
//        is MessageSendBinary -> generateBinarySend(receiver, messages)
//        is MessageSendKeyword -> generateKeywordSend(receiver, messages)
//    }


    // Unary
    // 4 inc = 4.inc()
    // 4 inc dec = 4.inc().dec()

    // Binary
    // 4 + 4 = 4 + 4
    // 4 inc + 4 dec = 4.inc() + 4.dec()
}


fun generateSingleKeyword(i: Int, receiver: Receiver, keywordMsg: KeywordMsg) = buildString {

    if (i == 0) {
        append(receiver.str, ".")
    }
    append(keywordMsg.selectorName)
    append("(")

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

            val messagesForArg = it.unaryOrBinaryMsgsForArg


            if (messagesForArg != null && messagesForArg.isNotEmpty()) {
                val messageForArg = messagesForArg[0]
                if (messageForArg is BinaryMsg) {
                    append(generateBinarySend(receiver, messagesForArg as List<BinaryMsg>))
//                    append(generateSingleBinary(i, receiver,messageForArg))
                } else if (messageForArg is UnaryMsg) {
                    append(generateUnarySends(receiver, messagesForArg as List<UnaryMsg>))
//                    append(generateSingleUnary(i, receiver,messageForArg))
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

fun generateSingleUnary(i: Int, receiver: Receiver, it: UnaryMsg) = buildString {
    if (i == 0) {
        append(receiver.str)
    }
    append(".${it.selectorName}()")
}

fun generateUnarySends(receiver: Receiver, messages: List<UnaryMsg>) = buildString {

    messages.forEachIndexed { i, it ->
        append(generateSingleUnary(i, receiver, it))
    }
    if (messages.isEmpty()) {
        append(receiver.str)
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
        append(generateSingleBinary(i, receiver, it))
    }
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