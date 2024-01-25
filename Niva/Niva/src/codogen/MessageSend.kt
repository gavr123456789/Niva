@file:Suppress("unused")

package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.resolver.Type
import main.RESET
import main.WHITE
import main.YEL


object GlobalDebugNeeded {
    var needStackTrace = true
    var printTime = false
}

fun MessageSend.generateMessageCall(withNullChecks: Boolean = false): String {

    val b = StringBuilder()

    if (GlobalDebugNeeded.needStackTrace) {
        val tok = this.token
        b.append("\n//@ ",tok.file.name,":::",tok.line,"\n")
    }


    if (this.messages.isEmpty()) {
        this.token.compileError("Message list for ${YEL}${this.str}${RESET} can't be empty")
    }

    b.append("(".repeat(messages.count { it.isPiped }))
    this.messages.forEachIndexed { i, it ->
        if (it.pragmas.isNotEmpty()) {
            replaceNameFromPragma(it)
            emitFromPragma(it)
            noPkgEmit(it)
        }



        if (it.pragmas.isNotEmpty() && it.pragmas.find { it.name == Pragmas.EMIT.v } != null) {
            b.append(it.selectorName)
        } else {
            when (it) {
                is UnaryMsg -> b.append(generateSingleUnary(i, receiver, it, withNullChecks))
                is BinaryMsg -> b.append(generateSingleBinary(i, receiver, it))
                is KeywordMsg -> b.append(generateSingleKeyword(i, receiver, it, withNullChecks))
            }
        }
        if (it.isPiped)
            b.append(")")
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
    RENAME("rename"),
    EMIT("emit"),
    NO_PKG_EMIT("noPkgEmit")
}

fun noPkgEmit(@Suppress("UNUSED_PARAMETER") msg: Message) {
//    TODO()
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

fun emitFromPragma(msg: Message) {
    val value = (msg.pragmas.find { it.name == Pragmas.EMIT.v })?.value

    fun replacePatternsWithValues(inputString: String, valueMap: Map<String, String>): String {
        var resultString = inputString
        val pattern = Regex("\\$(\\d+)")
        val matches = pattern.findAll(inputString)

        for (match in matches) {
            val patternMatch = match.value
            val number = match.groupValues[1]
            val replacement = valueMap[number] ?: patternMatch
            resultString = resultString.replace(patternMatch, replacement)
        }

        return resultString
    }


    when (value) {
        is LiteralExpression.StringExpr -> {
            if (msg is KeywordMsg) {

                val str = value.toString()
                val map = mutableMapOf<String, String>()
                msg.args.forEachIndexed { i, it -> map[(i + 1).toString()] = it.keywordArg.generateExpression() }
                map["0"] = msg.receiver.generateExpression()
                val q = replacePatternsWithValues(str, map)
                msg.selectorName = q
            } else {
                val str = value.toString()
                val qwe =
                    if (msg.receiver !is LiteralExpression.StringExpr) msg.receiver.generateExpression() else msg.receiver.token.lexeme
                val map = mutableMapOf("0" to qwe)
                val q = replacePatternsWithValues(str, map)

                msg.selectorName = q

            }
        }

        else -> {}
    }

}


fun generateSingleKeyword(i: Int, receiver: Receiver, keywordMsg: KeywordMsg, withNullChecks: Boolean = false) = buildString {

    // generate receiver
    val receiverIsDot = receiver is DotReceiver
    val receiverCode = buildString {
        val needBrackets =
            keywordMsg.kind != KeywordLikeType.Constructor &&
                    keywordMsg.kind != KeywordLikeType.CustomConstructor && !receiverIsDot ||
                    keywordMsg.kind == KeywordLikeType.ForCodeBlock ||
                    receiver is ExpressionInBrackets

        if (needBrackets) append("(")

        val kwReceiver = keywordMsg.receiver

        val isConstructor =
            keywordMsg.kind == KeywordLikeType.Constructor || keywordMsg.kind == KeywordLikeType.CustomConstructor
        // if it is a.b.Person already, then generateExpression will add this names
        val hasNoDotNames = kwReceiver is IdentifierExpr && kwReceiver.names.count() == 1

        if (!receiverIsDot && kwReceiver.type?.pkg != "core" &&
            isConstructor
            && hasNoDotNames)
        {
            val type = kwReceiver.type
            if (type != null) {
                append(type.pkg)
                dotAppend(this, withNullChecks)
            } else {
                dotAppend(this, withNullChecks)
            }
        }
        append(
            receiver.generateExpression()
        )
        if (needBrackets) append(")")

    }
    // end of receiver

    when (keywordMsg.kind) {
        KeywordLikeType.Keyword, KeywordLikeType.CustomConstructor -> {
            if ((i == 0) && !receiverIsDot) {
                append(receiverCode)
                dotAppend(this, withNullChecks)
            } else if (keywordMsg.isPiped) {
                dotAppend(this, withNullChecks)
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


            } else if (receiverIsDot) {
                append(valueArg.name, " = ")
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


    // if single lambda, no brackets needed
    val isNotSingleLambdaArg = !(keywordMsg.args.count() == 1 && keywordMsg.args[0].keywordArg is CodeBlock)
    if (isNotSingleLambdaArg) append("(")
    val receiverType = receiver.type
        ?: receiver.token.compileError("Compiler error: type of receiver: $WHITE$receiver$RESET is unresolved")

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

    if (isNotSingleLambdaArg) append(")")


}

val dotAppend = { b: StringBuilder, withNullChecks: Boolean ->
    if (!withNullChecks) b.append(".") else b.append("?.")
}

fun generateSingleUnary(i: Int, receiver: Receiver, it: UnaryMsg, withNullChecks: Boolean = false) = buildString {
    if (i == 0) {
        val receiverCode = receiver.generateExpression()
        append(receiverCode)
    }



    when (it.kind) {
        UnaryMsgKind.Unary -> {
            if (it.selectorName != "new") {
                if (receiver !is DotReceiver) dotAppend(this, withNullChecks)
                append("${it.selectorName}()")
            } else {
                append("()")
            }
        }

        UnaryMsgKind.Getter -> {
            if (receiver !is DotReceiver) dotAppend(this, withNullChecks)
            append(it.selectorName)
        }

        UnaryMsgKind.ForCodeBlock -> append("()")
    }
}

fun generateUnarySends(receiver: Receiver, messages: List<UnaryMsg>) = buildString {

    messages.forEachIndexed { i, it ->
        append(generateSingleUnary(i, it.receiver, it))
    }
    if (messages.isEmpty()) {
//        val expr =
        append(receiver.generateExpression())
    }
}

fun generateSingleBinary(
    i: Int,
    receiver: Receiver,
    it: BinaryMsg,
) = buildString {

    if (i == 0) {

        // 1 inc + 2 dec + 3 sas
        // 1 inc^ + 2 dec + 3 sas
        if (receiver !is DotReceiver) {
            append(
                generateUnarySends(
                    receiver,
                    it.unaryMsgsForReceiver
                )
            )
        } else {
            append("this")
        }

        append(" ${it.selectorName} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    } else {
        append(" ${it.selectorName} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    }

}
