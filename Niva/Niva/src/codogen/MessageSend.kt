@file:Suppress("unused")

package codogen

import frontend.meta.compileError
import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.SingleWordPragma
import frontend.parser.types.ast.*
import frontend.resolver.Type
import main.CYAN
import main.RESET
import main.WHITE
import main.YEL


object GlobalDebugNeeded {
    var needStackTrace = true
    var printTime = false
}

val evalPragmas: (Message) -> Pair<Boolean, List<String>?> = { it: Message ->
    if (it.pragmas.isNotEmpty()) {
        val keyPragmas = mutableListOf<KeyPragma>()
        val singleWordPragmas = mutableListOf<SingleWordPragma>()
        it.pragmas.forEach { if (it is KeyPragma) keyPragmas.add(it) else singleWordPragmas.add(it as SingleWordPragma) }

        replaceNameFromPragma(it, keyPragmas)
        emitFromPragma(it, keyPragmas)
        noPkgEmit(it)
        val newInvisibleArgs = ctNames(it, keyPragmas)
        Pair(it.pragmas.find { it.name == Pragmas.EMIT.v } != null, newInvisibleArgs)
    } else Pair(false, null)
}

fun MessageSend.generateMessageCall(withNullChecks: Boolean = false): String {

    val b = StringBuilder()

    if (GlobalDebugNeeded.needStackTrace) {
        val tok = this.token
        b.append("\n//@ ", tok.file.name, ":::", tok.line, "\n")
    }


    if (this.messages.isEmpty()) {
        this.token.compileError("Message list for ${YEL}${this.str}${RESET} can't be empty")
    }

    b.append("(".repeat(messages.count { it.isPiped }))
    var newInvisibleArgs: MutableList<String>? = null

    // refactor to function and call it recursive for binary arguments
    this.messages.forEachIndexed { i, it ->
        var isThereEmitPragma = false
        // TODO replace pragmas with unions and switch on them
        if (it.pragmas.isNotEmpty()) {
            (isThereEmitPragma) = evalPragmas(it).first
        }
        // do same for binary args
        if (it is BinaryMsg) {
            it.unaryMsgsForArg.forEach { binary ->
                isThereEmitPragma = evalPragmas(binary).first
            }
            it.unaryMsgsForReceiver.forEach { binary ->
                isThereEmitPragma = evalPragmas(binary).first
            }
        }

        if (isThereEmitPragma) {
            b.append(it.selectorName)
        } else {
            when (it) {
                is UnaryMsg -> b.append(generateSingleUnary(i, receiver, it, withNullChecks, newInvisibleArgs))
                is BinaryMsg -> b.append(generateSingleBinary(i, receiver, it, newInvisibleArgs))
                is KeywordMsg -> b.append(generateSingleKeyword(i, receiver, it, withNullChecks, newInvisibleArgs))
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
    NO_PKG_EMIT("noPkgEmit"),
    CT_NAME("arg")
}

val setOfPragmaNames = setOf("rename", "emit", "noPkgEmit", "arg")

fun noPkgEmit(@Suppress("UNUSED_PARAMETER") msg: Message) {
//    TODO()
}


fun ctNames(msg: Message, keyPragmas: List<KeyPragma>): List<String>? {

    val ctPragmas = keyPragmas
        .filter { it.name == Pragmas.CT_NAME.v}

    if (ctPragmas.isEmpty()) return null

    val listOfArgs: MutableList<String> = mutableListOf()
    ctPragmas.forEach {
        val value = it.value as? LiteralExpression.IntExpr
        if (value != null) {
            val num = value.token.lexeme.toInt()

            val getStrFromArg = { expr: Expression ->
                if (expr is IdentifierExpr) {
                    expr.token.lexeme
                } else {
                    //expr.str.removeDoubleQuotes()
                    "($expr)"
                }
            }
            if (num > 0) {
                // add all args, 1 for binary, all for kw
                val kwMsg = msg as? KeywordMsg
                    ?: msg.token.compileError("Compiler getName: with more than 0 arg can be used only with binary and keyword messages")
                val argN = try {
                    kwMsg.args[num - 1]
                } catch (e: Exception) {
                    msg.token.compileError("Compiler get: was used with $num, but $msg has only ${msg.args.count()} args")
                }


                listOfArgs.add(buildString { append('"', getStrFromArg(argN.keywordArg), '"') })
            } else {
                listOfArgs.add(buildString { append('"', getStrFromArg(msg.receiver), '"') })
            }

        }
    }

    return listOfArgs
}

fun replaceNameFromPragma(msg: Message, keyPragmas: List<KeyPragma>) {
    val renamePragmas = keyPragmas.filter { it.name == Pragmas.RENAME.v }
    if (renamePragmas.isEmpty()) return
    if (renamePragmas.count() > 1) {
        msg.token.compileError("You can't have more than one rename pragma, its pointless")
    }

    val value = renamePragmas[0].value
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

// replace $N to arguments and replace the selectorName of the message
fun emitFromPragma(msg: Message, keyPragmas: List<KeyPragma>) {

    val emitPragmas = keyPragmas.filter { it.name == Pragmas.EMIT.v }
    if (emitPragmas.isEmpty()) return
    if (emitPragmas.count() > 1) {
        msg.token.compileError("You can't have more than one emit pragma, its pointless")
    }

    val value = emitPragmas[0].value

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
            val map = mutableMapOf<String, String>()
            if (msg is KeywordMsg) {
                msg.args.forEachIndexed { i, it ->
                    map[(i + 1).toString()] = it.keywordArg.generateExpression()
                }
            }
            val receiverCode =

                when (msg.receiver) {
                    is Message -> {
                        "" // if there are messages already, then do not generate duplicates
                    }

                    !is LiteralExpression.StringExpr -> msg.receiver.generateExpression()
                    else -> msg.receiver.token.lexeme
                }

            map["0"] = receiverCode
            val replaced = replacePatternsWithValues(value.toString(), map)
            msg.selectorName = replaced

//            if (msg is KeywordMsg) {
//
//                val str = value.toString()
//                val map = mutableMapOf<String, String>()
//                msg.args.forEachIndexed { i, it -> map[(i + 1).toString()] = it.keywordArg.generateExpression() }
//                map["0"] =
//                    if (msg.receiver !is LiteralExpression.StringExpr) msg.receiver.generateExpression() else msg.receiver.token.lexeme
//                val q = replacePatternsWithValues(str, map)
//                msg.selectorName = q
//            } else {
//                val str = value.toString()
//                val qwe =
//                    if (msg.receiver !is LiteralExpression.StringExpr) msg.receiver.generateExpression() else msg.receiver.token.lexeme
//                val map = mutableMapOf("0" to qwe)
//                val q = replacePatternsWithValues(str, map)
//
//                msg.selectorName = q
//
//            }
        }

        else ->
            msg.token.compileError("String literal expected for emit pragma")
    }

}


fun generateSingleKeyword(
    i: Int,
    receiver: Receiver,
    keywordMsg: KeywordMsg,
    withNullChecks: Boolean = false,
    invisibleArgs: List<String>? = null
) = buildString {

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
            && hasNoDotNames
        ) {
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

    // if Compiler
    if (receiver.token.lexeme == "Compiler") {
        val firstArg = keywordMsg.args[0]
        if (firstArg.name == "getName") {
            val getArg = {
                if (firstArg.keywordArg !is LiteralExpression.IntExpr) {
                    firstArg.keywordArg.token.compileError("Int argument expected for `getName`")
                }

                firstArg.keywordArg.token.lexeme.toInt()
            }
            val arg = getArg()
            append("(__arg$arg)")
            return@buildString
        } else if (firstArg.name == "getType") {
            TODO()
        } else throw Exception("unexpected Compiler arg: $CYAN${firstArg.name}")
    }
    // end of Compiler

    // args
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
    if (invisibleArgs != null) {
        append(", ")
        append(invisibleArgs.joinToString(", "))
    }

    if (isNotSingleLambdaArg) append(")")


}

val dotAppend = { b: StringBuilder, withNullChecks: Boolean ->
    if (!withNullChecks) b.append(".") else b.append("?.")
}

fun generateSingleUnary(
    i: Int,
    receiver: Receiver,
    it: UnaryMsg,
    withNullChecks: Boolean = false,
    invisibleArgs: List<String>? = null
) = buildString {
    if (i == 0) {
        val receiverCode = receiver.generateExpression()
        append(receiverCode)
    }



    when (it.kind) {
        UnaryMsgKind.Unary -> {
            if (it.selectorName != "new") {
                if (receiver !is DotReceiver) dotAppend(this, withNullChecks)
                if (invisibleArgs == null)
                    append(it.selectorName, "()")
                else {
                    // add invisible args
                    append(it.selectorName, "(")
                    append(invisibleArgs.joinToString(", "))
                    append(")")
                }
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
    invisibleArgs: List<String>? = null
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
