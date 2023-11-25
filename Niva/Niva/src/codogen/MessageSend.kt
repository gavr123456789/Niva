package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.typer.Type

fun MessageSend.generateMessageCall(): String {

    val b = StringBuilder()

    if (this.messages.isEmpty()) {
        this.token.compileError("Message list for ${this.str} can't be empty")
    }

    this.messages.forEachIndexed { i, it ->
        replaceNameFromPragma(it)
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

enum class Pragmas(val v: String) {
    RENAME("rename")
}

fun replaceNameFromPragma(msg: Message) {
    val value = (msg.pragmas.find { it.name == Pragmas.RENAME.v })?.value
    val replacedSelectorName =
        when (value) {
            is LiteralExpression.StringExpr ->
                value.toString()

            else -> null
        }
    if (replacedSelectorName != null) {
        msg.selectorName = replacedSelectorName
    }
}


fun generateSingleKeyword(i: Int, receiver: Receiver, keywordMsg: KeywordMsg) = buildString {
//    val value = (keywordMsg.pragmas.find { it.name == Pragmas.RENAME.v })?.value
//    val replacedSelectorName =
//        when (value) {
//            is LiteralExpression.StringExpr -> value.toString()
//            else -> null
//        }


    val receiverCode = buildString {
        val needBrackets =
            keywordMsg.kind != KeywordLikeType.Constructor && keywordMsg.kind != KeywordLikeType.CustomConstructor || keywordMsg.kind == KeywordLikeType.ForCodeBlock || receiver is ExpressionInBrackets
        if (needBrackets) append("(")

        val kwReceiver = keywordMsg.receiver
        if (kwReceiver.type?.pkg != "core" && (keywordMsg.kind == KeywordLikeType.Constructor || keywordMsg.kind == KeywordLikeType.CustomConstructor) && kwReceiver is IdentifierExpr) {
            val type = kwReceiver.type
            if (type != null) {
                append(type.pkg, ".")
            } else {
                append(".")
            }
        }
        append(
            receiver.generateExpression()
        )
        if (needBrackets) append(")")

    }

    when (keywordMsg.kind) {
        KeywordLikeType.Keyword, KeywordLikeType.CustomConstructor -> {
            if (i == 0) {
                append(receiverCode, ".")
            }
            append(keywordMsg.selectorName)
        }

        KeywordLikeType.Constructor -> {
            if (i == 0) {
                append(receiverCode)
            }
        }

        KeywordLikeType.Setter -> {
            // emptyWallet money: 20
            // emptyWallet.money = 20
            if (keywordMsg.args.count() != 1) {
                keywordMsg.token.compileError("Setters must have only one argument")
            }
            val valueArg = keywordMsg.args[0]
            if (receiver is IdentifierExpr) {
                append(receiver.name, ".", valueArg.name, " = ")
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
    val receiverType = receiver.type!!

    // generate args
    keywordMsg.args.forEachIndexed { i, it ->

        val expressionStr = it.keywordArg.generateExpression()
        if (keywordMsg.kind == KeywordLikeType.Constructor && receiverType is Type.UserLike && !receiverType.isBinding) {
            append(it.name, " = ")
        }
        append(expressionStr)
        if (i != keywordMsg.args.count() - 1)
            append(", ")

    }

    append(")")
}

fun generateSingleUnary(i: Int, receiver: Receiver, it: UnaryMsg) = buildString {
    if (i == 0) {
        val receiverCode = receiver.generateExpression()
        append(receiverCode)
    }
    when (it.kind) {
        UnaryMsgKind.Unary -> {

            append(".${it.selectorName}()")
        }

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
