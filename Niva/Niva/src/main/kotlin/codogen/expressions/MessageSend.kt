@file:Suppress("unused")

package main.codogen

import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.SingleWordPragma
import frontend.resolver.CompilerMessages
import frontend.resolver.Type
import main.frontend.meta.compileError
import main.frontend.meta.prettyCodePlace
import main.frontend.parser.types.ast.*
import main.utils.*


data class EvaledPragmas(val isThereEmit: Boolean, val invisibleArgs: List<String>?)

// second in pair is a list of invisible args
val evalPragmas: (Message) -> EvaledPragmas = { it: Message ->
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
//        val codePlaceArg = codePlace(it, keyPragmas)

//        val combined = (newInvisibleArgs.orEmpty() + codePlaceArg.orEmpty())
        EvaledPragmas(it.pragmas.find { it.name == Pragmas.EMIT.v } != null, newInvisibleArgs)
    } else EvaledPragmas(false, null)
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
                isThereEmitPragma = evalPragmas(binary).isThereEmit
            }
            it.unaryMsgsForReceiver.forEach { binary ->
                isThereEmitPragma = evalPragmas(binary).isThereEmit
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
    // rename the emitted name of the class or method
    RENAME("rename"),
    // for methods = replace call with kotlin code, takes string arg
    EMIT("emit"),
    // NOT IMPLEMENTED, - do not auto-import this method or type, good for methods for default types
    NO_PKG_EMIT("noPkgEmit"),
    // ???
    CT_NAME("arg"),
    // do not generate getter method
    NO_GETTER("noGetters")

}

// List of pragmas that wont be generated in kotlin code(all the others will just appear on the same place, even if they do not exist
val builtInPragmas = setOf("rename", "emit", "arg", "debug", "noGetters", "emitJs", "renameJs")

// adding invisible arg of codeplace
//fun codePlace(msg: Message, keyPragmas: List<KeyPragma>): List<String>?  {
//    val codePlaces = keyPragmas.filter { it.name == Pragmas.CODE_PLACE.v }
//
//    if (codePlaces.isEmpty()) return null
//
//    if (codePlaces.count() != 1) msg.token.compileError("There should be only one GetCodePlace pragma per method call")
//
//    val codePlace = codePlaces.first()
//
//    return listOf(codePlace.value.token.prettyCodePlace())
//}
// adding invisible args like for Compiler getName: "getName"
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
                    "$expr"
                }
            }
            if(num == 100) {
                // special num for caller name arg
                listOfArgs.add(buildString { append("\"\"\"", msg.token.prettyCodePlace(), "\"\"\"") })

            }
            else if (num > 0) {
                // add all args, 1 for binary, all for kw
                val kwMsg = msg as? KeywordMsg
                    ?: msg.token.compileError("Compiler getName: with more than 0 arg can be used only with binary and keyword messages")
                val argN = try {
                    kwMsg.args[num - 1]
                } catch (e: Exception) {
                    kwMsg.token.compileError("Compiler get: was used with $num, but $kwMsg has only ${kwMsg.args.count()} args")
                }

                listOfArgs.add(buildString { append("\"\"\"", getStrFromArg(argN.keywordArg), "\"\"\"") })
            } else {
                listOfArgs.add(buildString { append("\"\"\"", getStrFromArg(msg.receiver), "\"\"\"") })
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

        return if (msg.isPiped || msg.isCascade) //msg.receiver is Message
            ".$resultString"
        else
            resultString
    }

    when (value) {
        is LiteralExpression.StringExpr -> {
            val map = mutableMapOf<String, String>()
            if (msg is KeywordMsg) {
                msg.args.forEachIndexed { i, it ->
                    map[(i + 1).toString()] = it.keywordArg.generateExpression()
                }
            }
            val receiver = msg.receiver
            val receiverCode = if (msg.isCascade) "cascade_receiver"
            else when (receiver) {
                is Message -> {
                    if (receiver.isPiped) {
                        val res = msg.receiver.generateExpression()
                        res
                    } else if (receiver.receiver is Message) {
                        // (1 inc) inc
                        // (1 inc) is receiver
                        // first inc already generated, so we dont need to do a copy of receivre
//                        receiver.generateExpression()
                        ""
                    } else if (receiver.receiver is MessageSend) {
                        msg.receiver.generateExpression()
                    } else "" // if there are messages already, then do not generate duplicates
                }

                !is LiteralExpression.StringExpr -> msg.receiver.generateExpression()
                else -> msg.receiver.token.lexeme
            }

            map["0"] = receiverCode
            val result = replacePatternsWithValues(value.toString(), map)
            msg.selectorName = result
        }

        else -> msg.token.compileError("String literal expected for emit pragma")
    }

}

// pragma "arg" Compiler "getName" "getName:"
fun generateSingleKeyword(
    i: Int,
    receiver: Receiver,
    isConstructor: Boolean,
    keywordMsg: KeywordMsg,
    withNullChecks: Boolean = false,
    invisibleArgs: List<String>? = null, // "arg" "getName" "getName:"
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
        val f = try {
            CompilerMessages.valueOf(firstArg.name)
        } catch (e: Exception) {
            firstArg.keywordArg.token.compileError("unexpected Compiler arg: $CYAN${firstArg.name}")
        }
        when (f) {
            CompilerMessages.getName -> {
                val getArg = {
                    if (firstArg.keywordArg !is LiteralExpression.IntExpr) {
                        firstArg.keywordArg.token.compileError("Int argument expected for `${CompilerMessages.getName.name}`")
                    }

                    firstArg.keywordArg.token.lexeme.toIntOrNull()
                        ?: firstArg.keywordArg.token.compileError("Int argument expected for `${CompilerMessages.getName.name}`")
                }
                val arg = getArg()
                append("(__arg$arg)")
                return@buildString
            }
            CompilerMessages.getPlace -> {
                firstArg.keywordArg.token.compileError("Cant send getPlace as Keyword msg")
            }

            CompilerMessages.debug -> {}
            CompilerMessages.cliArgs -> {}
        }

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
                // Special handling for Object
                val kwReceiver = keywordMsg.receiver
                val isObjectReceiver = kwReceiver is IdentifierExpr && kwReceiver.name == "Object"
                
                if (isObjectReceiver) {
                    val resultType = keywordMsg.type
                    // Check if this is anonymous object (Object_xxx) or concrete type (Person)
                    if (resultType != null && resultType.name.startsWith("Object_")) {
                        // Generate anonymous Kotlin object { val field = value ... }
                        append("object {\n")
                        keywordMsg.args.forEachIndexed { index, arg ->
                            append("val ${arg.name} = ${arg.keywordArg.generateExpression()}")
                            if (index < keywordMsg.args.size - 1) {
                                append("\n")
                            }
                        }
                        append("\n}")
                        return@buildString
                    } else if (resultType != null) {
                        // Generate concrete type constructor: Person(...)
                        if (!receiverIsDot && resultType.pkg != "core") {
                            append(resultType.pkg)
                            append(".")
                        }
                        append(resultType.name)
                    } else {
                        val recCode = receiverCode()
                        append(recCode)
                    }
                } else {
                    val recCode = receiverCode()
                    append(recCode)
                }
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
            // we don't need comma for 0 or 1 args Person(sas)
            val newValueAssign = "${if (receiverType.fields.count() > 1) "," else ""} ${valueArg.name} = ${valueArg.keywordArg.generateExpression()}"

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
    val isNotSingleLambdaArg =
        !(keywordMsg.args.count() == 1 && keywordMsg.args[0].keywordArg is CodeBlock && !isConstructor)
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
    // "arg" pragma,  Compiler "getName" "getName:"
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
        if (receiver.token.lexeme == "Compiler") {
            val f = try {
                CompilerMessages.valueOf(it.selectorName)
            } catch (e: Exception) {
                it.token.compileError("unexpected Compiler arg: $CYAN${it.selectorName}")
            }
            when (f) {
                CompilerMessages.getName -> it.token.compileError("cant get name from unary msg")
                CompilerMessages.getPlace -> {
                    append("(__arg100)")
                    return@buildString
                }
                CompilerMessages.debug -> {
                    it.pragmas.forEach {
                        when (it) {
                            is SingleWordPragma -> {
                                val q = it.name
                                appendLine("println(\"$q = $$q\")")
                            }
                            is KeyPragma -> {}
                        }
                    }
                    return@buildString

                }
                CompilerMessages.cliArgs -> {}
                
            }
        }
        append(receiverCode)
    }



    when (it.kind) {
        UnaryMsgKind.Unary -> {
            fun isThatDefaultConstructor(): Boolean {
                if (it.selectorName != "new")
                    return false
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
