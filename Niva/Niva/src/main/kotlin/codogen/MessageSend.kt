@file:Suppress("unused")

package main.codogen

import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.SingleWordPragma
import frontend.resolver.Type
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.*


val evalPragmas: (Message) -> Pair<Boolean, List<String>?> = { it: Message ->
    if (it.pragmas.isNotEmpty()) {
        val keyPragmas = mutableListOf<KeyPragma>()
        val singleWordPragmas = mutableListOf<SingleWordPragma>()
        it.pragmas.forEach {
            if (it is KeyPragma)
                keyPragmas.add(it)
            else
                singleWordPragmas.add(it as SingleWordPragma)
        }

        replaceNameFromPragma(it, keyPragmas)
        emitFromPragma(it, keyPragmas)
        val newInvisibleArgs = ctNames(it, keyPragmas)
        Pair(it.pragmas.find { it.name == Pragmas.EMIT.v } != null, newInvisibleArgs)
    } else Pair(false, null)
}

fun MessageSend.generateMessageCall(withNullChecks: Boolean = false): String {
    val b = StringBuilder()

    if (GlobalVariables.needStackTrace) {
        val tok = this.token
        b.append("\n//@ ", tok.file.name, ":::", tok.line, "\n")
    }

    if (this.messages.isEmpty()) {
        this.token.compileError("Message list for ${YEL}${this.str}${RESET} can't be empty")
    }

    val isThisACascade = messages.find { it.isCascade } != null

    if (!isThisACascade) b.append("(".repeat(messages.count { it.isPiped }))


    val fakeReceiver = if (isThisACascade) {
        b.append(receiver.generateExpression())
        b.append(".also { cascade_receiver ->\n") // then generate function calls on this receiver
        IdentifierExpr(name = "cascade_receiver", token = token).also {
                it.type = receiver.type
            } // because next we will read type of receiver
    } else null

    // refactor to function and call it recursive for binary arguments
    this.messages.forEachIndexed { i, it ->
        var newInvisibleArgs: List<String>? = null

        var isThereEmitPragma = false
        // TODO replace pragmas with unions and switch on them
        if (it.pragmas.isNotEmpty()) {
            val (isThereEmit, ctArgs) = evalPragmas(it)
            isThereEmitPragma = isThereEmit
            if (ctArgs != null) {
                newInvisibleArgs = ctArgs
            }
        }
        // pragmas for binary args
        if (it is BinaryMsg) {
            it.unaryMsgsForArg.forEach { binary ->
                isThereEmitPragma = evalPragmas(binary).first
            }
            it.unaryMsgsForReceiver.forEach { binary ->
                isThereEmitPragma = evalPragmas(binary).first
            }
        }

        if (isThereEmitPragma) {
            if (newInvisibleArgs?.isNotEmpty() == true) {
                it.token.compileError("You cant combine Compiler with emit pragma")
            }
            b.append(it.selectorName)
        } else {
            if (isThisACascade) {
                // send with i + 1, to not trigger the receiver generation
                if (!it.isPiped) generateMessages(it, b, 0, fakeReceiver!!, withNullChecks, newInvisibleArgs)
                else {
//                    val i2 = if (messages[i-1].isPiped) i else 0
                    generateMessages(it, b, i, receiver, withNullChecks, newInvisibleArgs)
                }

                b.append("\n")
            } else
                generateMessages(it, b, i, receiver, withNullChecks, newInvisibleArgs)
        }

        if (it.isPiped && !isThisACascade) b.append(")")
    }

    if (isThisACascade) b.append("\n}") // close also
    return b.toString()


    // Unary
    // 4 inc = 4.inc()
    // 4 inc dec = 4.inc().dec()

    // Binary
    // 4 + 4 = 4 + 4
    // 4 inc + 4 dec = 4.inc() + 4.dec()
}

fun generateMessages(
    msg: Message,
    b: StringBuilder,
    i: Int,
    receiver: Receiver,
    withNullChecks: Boolean,
    newInvisibleArgs: List<String>?,
): StringBuilder = when (msg) {
    is UnaryMsg -> b.append(
        generateSingleUnary(
            i,
            receiver,
            msg,
            withNullChecks,
            newInvisibleArgs,
        )
    )

    is BinaryMsg -> b.append(
        generateSingleBinary(
            i,
            receiver,
            msg,
            newInvisibleArgs,
        )
    )

    is KeywordMsg -> b.append(
        generateSingleKeyword(
            i,
            receiver,
            msg.kind == KeywordLikeType.Constructor,
            msg,
            withNullChecks,
            newInvisibleArgs,
        )
    )

    is StaticBuilder -> msg.token.compileError("TODO")
}

enum class Pragmas(val v: String) {
    RENAME("rename"), EMIT("emit"), NO_PKG_EMIT("noPkgEmit"), CT_NAME("arg")
}

val setOfPragmaNames = setOf("rename", "emit", "arg")


fun ctNames(msg: Message, keyPragmas: List<KeyPragma>): List<String>? {

    val ctPragmas = keyPragmas.filter { it.name == Pragmas.CT_NAME.v }

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
                    "'$expr"
                }
            }
            if (num > 0) {
                // add all args, 1 for binary, all for kw
                val kwMsg = msg as? KeywordMsg
                    ?: msg.token.compileError("Compiler getName: with more than 0 arg can be used only with binary and keyword messages")
                val argN = try {
                    kwMsg.args[num - 1]
                } catch (e: Exception) {
                    kwMsg.token.compileError("Compiler get: was used with $num, but $kwMsg has only ${kwMsg.args.count()} args")
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
    if (value is LiteralExpression.StringExpr) {
        msg.selectorName = value.toString()
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
            val receiverCode = if (msg.isCascade) "cascade_receiver"
            else when (msg.receiver) {
                is Message -> {
                    if (msg.receiver.isPiped) msg.receiver.generateExpression()
                    else "" // if there are messages already, then do not generate duplicates
                }

                !is LiteralExpression.StringExpr -> msg.receiver.generateExpression()
                else -> msg.receiver.token.lexeme
            }

            map["0"] = receiverCode

            msg.selectorName = replacePatternsWithValues(value.toString(), map)
        }

        else -> msg.token.compileError("String literal expected for emit pragma")
    }

}


fun generateSingleKeyword(
    i: Int,
    receiver: Receiver,
    isConstructor: Boolean,
    keywordMsg: KeywordMsg,
    withNullChecks: Boolean = false,
    invisibleArgs: List<String>? = null,
) = buildString {
    // generate receiver
    val receiverIsDot = receiver is DotReceiver
    val receiverCode = {
        buildString {
            val needBrackets =
                keywordMsg.kind != KeywordLikeType.Constructor && keywordMsg.kind != KeywordLikeType.CustomConstructor && !receiverIsDot || keywordMsg.kind == KeywordLikeType.ForCodeBlock || receiver is ExpressionInBrackets

            if (needBrackets) append("(")

            val kwReceiver = keywordMsg.receiver

            val isConstructor =
                keywordMsg.kind == KeywordLikeType.Constructor || keywordMsg.kind == KeywordLikeType.CustomConstructor
            // if it is a.b.Person already, then generateExpression will add this names
            val hasNoDotNames = kwReceiver is IdentifierExpr && kwReceiver.names.count() == 1

            if (!receiverIsDot && kwReceiver.type?.pkg != "core" && isConstructor && hasNoDotNames) {
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

            if (receiver is IdentifierExpr && receiver.typeAST != null && receiver.typeAST.name.isGeneric()) {
                append("<", receiver.typeAST.name, ">")
            }

            if (needBrackets) append(")")

        }
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
            TODO("getType not implemented yet")
        } else throw Exception("unexpected Compiler arg: $CYAN${firstArg.name}")
    }
    // end of Compiler

    val receiverType = receiver.type
        ?: receiver.token.compileError("Compiler error: type of receiver: $WHITE$receiver$RESET is unresolved")


    // args
    when (keywordMsg.kind) {
        KeywordLikeType.Keyword, KeywordLikeType.CustomConstructor -> {
            if ((i == 0) && !receiverIsDot) {
                val receiverCode2 = receiverCode()
                append(receiverCode2)
                dotAppend(this, withNullChecks)
            } else if (keywordMsg.isPiped) {
                dotAppend(this, withNullChecks)
            }
            append(keywordMsg.selectorName.ifKtKeywordAddBackTicks())

            // Json::Person encode = Json.encode<Person>
            if (receiver is IdentifierExpr && receiver.typeAST != null) {
                append("<", receiver.typeAST.name, ">")
            }
        }

        KeywordLikeType.Constructor -> {
            if (i == 0) {
                val recCode = receiverCode()
                append(recCode)
            }
        }

        KeywordLikeType.Setter -> {
            // emptyWallet money: 20
            // emptyWallet.money = 20
            val valueArg = keywordMsg.args[0]
            if (receiver is IdentifierExpr) {
                append(receiver.name, ".", valueArg.name, " = ")


            } else if (receiverIsDot) {
                append(valueArg.name, " = ")
            }
        }

        KeywordLikeType.SetterImmutableCopy -> {
            // p age: 1
            // Person(age = 1, name = p.name)
            val valueArg = keywordMsg.args[0]

            if (receiverType !is Type.UserLike) {
                keywordMsg.token.compileError("Only type with fields can be used in immutable copy assign")
            }
            if (receiver !is IdentifierExpr) {
                keywordMsg.token.compileError("Receiver must be identifier to be used in immutable copy assign")
            }

            val typeConstructor = receiverType.toKotlinString(true)
            val newValueAssign = ", ${valueArg.name} = ${valueArg.keywordArg.generateExpression()}"

            // all args except the changing one
            val args = receiverType.fields.asSequence().filter { it.name != valueArg.name }
                .joinToString { "${it.name} = ${receiver.name}.${it.name}" } + newValueAssign

            append(typeConstructor, "(", args, ")")
            return@buildString
        }

        KeywordLikeType.ForCodeBlock -> {

            // when its arg type like [Int -> Int] Int: 1 then we generate lambda(1)
            // when its real message extension for lambda then lambda.at(1)
            val receiverType2 = keywordMsg.receiver.type as Type.Lambda
            val isExtensionForLambda = receiverType2.alias != null
            // printingClient Request: request // here we dont need to generate .Request()
            // type Filter = [HttpHandler -> HttpHandler]
            // we use "Request:" in niva just because we don't have real name for arg, and it still can be alias
            val firstArgIsSelectorName =
                receiverType2.args.isNotEmpty() && receiverType2.args[0].name == keywordMsg.selectorName
            // generate receiver for keyword if this is first msg in a row
            if (keywordMsg.selectorName == "whileTrue" || keywordMsg.selectorName == "whileFalse" || (isExtensionForLambda && !firstArgIsSelectorName)) {
                if (i == 0)
                    append(receiverCode())
                append(".", keywordMsg.selectorName)
            } else {
                if (i == 0) {
                    val receiverCode2 = receiverCode()
                    append(receiverCode2)
                }
            }
        }
    }


    // if single lambda, no brackets needed
    // but not needed for contructors call, there is no such things as builder constructors, I hope
    val isNotSingleLambdaArg = !(keywordMsg.args.count() == 1 && keywordMsg.args[0].keywordArg is CodeBlock && !isConstructor)
    if (isNotSingleLambdaArg) append("(")

    // generate args
    keywordMsg.args.forEachIndexed { i, it ->

        val expressionStr = it.keywordArg.generateExpression(isArgument = true)
        if (keywordMsg.kind == KeywordLikeType.Constructor && receiverType is Type.UserLike && !receiverType.isBinding) {
            append(it.name, " = ")
        }
        append(expressionStr)
        if (i != keywordMsg.args.count() - 1) append(", ")

    }
    if (invisibleArgs != null) {
        if (keywordMsg.args.isNotEmpty()) append(", ")
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
    invisibleArgs: List<String>? = null,
) = buildString {
    if (i == 0) {
        val receiverCode = receiver.generateExpression()
        append(receiverCode)

//        val type = receiver.type!!
//        if (receiver is IdentifierExpr && receiver.isType && type is Type.UserLike) {
//            if (type.typeArgumentList.count() == 1)
//                append("<", type.typeArgumentList[0].name + ">")
//        }
    }



    when (it.kind) {
        UnaryMsgKind.Unary -> {
            fun isThatDefaultConstructor(): Boolean {
                if (it.selectorName != "new") return false
                val w = it.receiver.type!!
                if (w !is Type.UserLike) return false
                // has new static method
                if (w.protocols.values.find { it.staticMsgs.containsKey("new") } != null) return false
                return w.fields.isEmpty()
            }
            val isThatDefaultConstructor = isThatDefaultConstructor()


            if (!isThatDefaultConstructor) {
                if (receiver !is DotReceiver) dotAppend(this, withNullChecks)
                append(it.selectorName.ifKtKeywordAddBackTicks())

                // Json::Person encode = Json.encode<Person>
                if (receiver is IdentifierExpr && receiver.typeAST != null) {
                    append("<", receiver.typeAST.name, ">")
                }

                if (invisibleArgs == null) {
                    append("()")
                } else {
                    // add invisible args
                    append("(")
                    append(invisibleArgs.joinToString(", "))
                    append(")")
                }
            } else {
                append("()")
            }
        }

        UnaryMsgKind.Getter -> {
            if (receiver !is DotReceiver) dotAppend(this, withNullChecks)
            append(it.selectorName.ifKtKeywordAddBackTicks())
        }

        UnaryMsgKind.ForCodeBlock -> {
            append("()")
        }
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
    @Suppress("UNUSED_PARAMETER") invisibleArgs: List<String>? = null,
) = buildString {

    if (i == 0) {

        // 1 inc + 2 dec + 3 sas
        // 1 inc^ + 2 dec + 3 sas
        if (receiver !is DotReceiver) {
            append(
                generateUnarySends(
                    receiver, it.unaryMsgsForReceiver
                )
            )
        } else {
            append("this")
        }

        append(" ${it.selectorName.ifKtKeywordAddBackTicks()} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    } else {
        append(" ${it.selectorName.ifKtKeywordAddBackTicks()} ")
        append(generateUnarySends(it.argument, it.unaryMsgsForArg))
    }

}
