package frontend.parser.parsing

import frontend.resolver.KeywordArgAst
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.utils.capitalizeFirstLetter
import main.utils.CYAN
import main.utils.RED
import main.utils.WHITE
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.types.ast.*
import main.utils.GlobalVariables

////   // MESSAGES
////  messages = (unaryMessage+ binaryMessage* keywordMessage?)  -- unaryFirst
////  		   | (binaryMessage+ keywordMessage?) -- binaryFirst
////           | (keywordMessage) -- keywordFirst

//    // x echo // identifier
//    // 1 echo // primary
//    // (1 + 1) echo // parens
//    // [1 2 3] // data structure
//
//    // 3 + (1 / 2)
//    // 3 + 8 to: 7


// 1^ sas sus sos -> {sas sus sos}
fun Parser.unaryMessagesMatching(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()

    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val identifier = identifierMayBeTyped()
        if (identifier.typeAST != null) {
            identifier.token.compileError("Error: You can't put type on a unary message send: $CYAN${identifier.token.lexeme + "::" + identifier.typeAST.name}$RED, line: $WHITE${identifier.token.line}")
        }
        if (check(TokenType.Colon)) {
            throw Exception("This is not unary, but a keyword with path")
        }
        // each unary message must have previous unary as receiver because
        // person name echo -- receiver of echo is name, not person
        val receiver2 = if (unaryMessages.isNotEmpty()) unaryMessages.last() else receiver
        val unaryFirstMsg = UnaryMsg(
            receiver2,
            identifier.name,
            identifier.names,
            null,
            identifier.token,
            declaration = null
        )
        unaryMessages.add(unaryFirstMsg)


//        if (check(TokenType.EndOfLine) && check(TokenType.Identifier, 1) && !check(TokenType.Colon, 2) &&
//            peek(1).spaces > receiver.token.spaces
//        ) {
//            step()
//        }

    }

    return unaryMessages
}

// 1^ + 2 + 3
fun Parser.binaryMessagesMatching(
    receiver: Receiver,
    unaryMessagesForReceiver: MutableList<UnaryMsg>
): MutableList<BinaryMsg> {
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    var previousBinaryParsed: BinaryMsg? = null
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = matchAssert(TokenType.BinarySymbol) // +
        skipNewLinesAndComments()
        val binaryArgument = simpleReceiver() // 2
        val unaryForArg = unaryMessagesMatching(binaryArgument)
        val binaryMsg = BinaryMsg(
            previousBinaryParsed ?: receiver,
            if (needAddMessagesForReceiverForBinary) unaryMessagesForReceiver else emptyList(),
            binarySymbol.lexeme,
            null,
            binarySymbol,
            binaryArgument,
            unaryForArg,
            declaration = null

        )
        binaryMessages.add(binaryMsg)
        needAddMessagesForReceiverForBinary = false
        previousBinaryParsed = binaryMsg
    }
    return binaryMessages
}

fun Parser.unaryOrBinary(
    customReceiver: Receiver? = null,
    parsePipe: Boolean = true,
    parseCascade: Boolean = true,
): MessageSend {
    var wasPipe = false

    val firstReceiver: Receiver = customReceiver ?: simpleReceiver()


    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessages = unaryMessagesMatching(firstReceiver)

    // 3 inc inc^ + 2 dec dec + ...
    val binaryMessages = binaryMessagesMatching(firstReceiver, unaryMessages)

    val takeLastMessage = {
        if (binaryMessages.isNotEmpty()) {
            binaryMessages.last()
        } else if (unaryMessages.isNotEmpty()) {
            unaryMessages.last()
        } else null //throw Exception("Both unary and binary messages are empty")
    }

    // 1 inc |> inc useless!
    // 1 + 2 to: 3 useless!
    // 1 + 2 |> inc to: 3

    // Pipe operator
    val pipedMsgs = mutableListOf<Message>()
    // x |> y
    // x must become a receiver for y
    while (parsePipe && matchAfterSkip(TokenType.PipeOperator)) {
        wasPipe = true
        // 1 inc
        // |>       // matchAfterSkip skipped to this
        //   inc    // but we also need to skip after
        skipNewLinesAndComments()
        when {

            // Unary
            check(TokenType.Identifier) -> {
                // x |> inc
                var lastMsgOrFirstReceiver = takeLastMessage() ?: firstReceiver
                val unary = unaryMessagesMatching(lastMsgOrFirstReceiver)

                unary.forEach {
                    it.receiver = lastMsgOrFirstReceiver
                    lastMsgOrFirstReceiver = it
                }
                binaryMessages.forEach {
                    it.isPiped = true
                }
                unaryMessages.forEach {
                    it.isPiped = true
                }
                pipedMsgs.addAll(unary)

                if (check(TokenType.BinarySymbol)) {
                    val binary = binaryMessagesMatching(lastMsgOrFirstReceiver, mutableListOf())
                    binaryMessages.addAll(binary)
                }
            }
            // Binary
            check(TokenType.BinarySymbol) -> {
                val binary = binaryMessagesMatching(takeLastMessage() ?: firstReceiver, mutableListOf())
                binaryMessages.forEach {
                    it.isPiped = true
                }
                binaryMessages.addAll(binary)
            }

            checkForKeyword() -> {
                error("sas!")
            }

            else -> {
                if (GlobalVariables.isLspMode && unaryMessages.isNotEmpty()) {
                    unaryMessages.last().isPiped = true
                } else
                    firstReceiver.token.compileError("message after pipe expected")
                // we got |> but nothing afth
            }


        }
    }


    val cascadedMsgs = mutableListOf<Message>()
    // Cascade operator
    while (parseCascade && matchAfterSkip(TokenType.Cascade)) {
        if (wasPipe) {
            error("Don't use pipe with cascade operator, better create different variables")
        }
        skipNewLinesAndComments()

        when {
            check(TokenType.BinarySymbol) -> {
                val binary = binaryMessagesMatching(firstReceiver, mutableListOf()).onEach { it.isCascade = true }
                cascadedMsgs.addAll(binary)
            }

            checkForKeyword() -> {
                val keyword = keywordMessageParsing(firstReceiver).also { it.isCascade = true }
                cascadedMsgs.add(keyword)
            }

            check(TokenType.Identifier) -> {
                val unary = unaryMessagesMatching(firstReceiver).onEach { it.isCascade = true }
                cascadedMsgs.addAll(unary)
            }
        }
        if (check(TokenType.PipeOperator)) {
            peek().compileError("Don't use pipe with cascade operator, better create different variables")
        }
    }


    // if there is no binary message, that's mean there is only unary
    if (binaryMessages.isEmpty()) {
        return MessageSendUnary(
            firstReceiver,
            messages = (unaryMessages + pipedMsgs + cascadedMsgs).toMutableList(),
            null,
            firstReceiver.token
        )
    }
    // its binary msg
    return MessageSendBinary(firstReceiver, binaryMessages + cascadedMsgs + pipedMsgs, null, firstReceiver.token)
}

// Receiver ^ from: to:
fun Parser.checkForKeyword(): Boolean {
    val savePoint = current
    var result = false
    skipNewLinesAndComments()
    val identTok = match(TokenType.Identifier)
    if (identTok) {
        val dot = match(TokenType.Dot)
        if (dot) {
            while (match(TokenType.Identifier)) {
                match(TokenType.Dot)
            }
            if (match(TokenType.Colon)) {
                result = true
            }

        } else {
//            skipNewLinesAndComments()
            if (match(TokenType.Colon)) {
                result = true
            }
        }
    }


    current = savePoint
    return result
}


fun Parser.messageSend(
    dontParseKeywords: Boolean, // true if we inside keyword argument
    dotReceiver: Boolean = false
): MessageSend {

    // 1 from: 2 // 1 is receiver without unary\binary messages
    val keywordOnReceiverWithoutMessages = if (dontParseKeywords) false else {
        if (check(TokenType.OpenParen))
            false
        else {
            val savepoint = current
            dotSeparatedIdentifiers()
            val result = checkForKeyword()
            current = savepoint
            result
        }

    }

    val receiver = when {
        // not keyword then parse unaryBinary with custom receiver
        !keywordOnReceiverWithoutMessages && check(TokenType.Dot) && dotReceiver -> {

            unaryOrBinaryMessageOrPrimaryReceiver(
                IdentifierExpr("this", listOf("this"), type = null, token = matchAssert(TokenType.Dot), isType = false)
            )
        }
        // pure keyword, then add dot receiver
        keywordOnReceiverWithoutMessages && check(TokenType.Dot) && dotReceiver ->
            IdentifierExpr("this", listOf("this"), type = null, token = matchAssert(TokenType.Dot), isType = false)

        !keywordOnReceiverWithoutMessages ->
            unaryOrBinaryMessageOrPrimaryReceiver(insideKeywordArgument = dontParseKeywords)

        else -> simpleReceiver()
    }
    // it can be keyword on simple receiver or keyword on receiver with messages
    val isNextKeyword = if (dontParseKeywords) false else keywordOnReceiverWithoutMessages || checkForKeyword()
    val isReceiverUnaryOrBinaryOrCodeBlock =
        receiver is MessageSendBinary || receiver is MessageSendUnary || receiver is CodeBlock

    val savepoint = current


    val result = when {
        isReceiverUnaryOrBinaryOrCodeBlock && isNextKeyword -> {
            keyword(receiver)
        }

        isNextKeyword -> {
            val kw = keyword(receiver)

            if (check(TokenType.Assign)) {
                current = savepoint
                MessageSendUnary(receiver, mutableListOf(), token = receiver.token)
            } else
                kw
        }

        !isNextKeyword && (receiver is MessageSendBinary) -> {
            receiver
        }

        else -> {
            MessageSendUnary(receiver, mutableListOf(), token = receiver.token)
        }
    }

    return result
}


fun Parser.keyword(
    customReceiver: Receiver? = null,
): MessageSend {
    // if unary/binary message receiver then we already parsed it somewhere on a higher level
    val receiver: Receiver = customReceiver ?: simpleReceiver()
    skipNewLinesAndComments()

    val keyColonCycle = {
        keywordMessageParsing(receiver)
    }

    val messages = mutableListOf<Message>(keyColonCycle())

    // checkForKeyword for `1 to: 2 |> kek ^ from: 4` situation
    while (checkAfterSkip(TokenType.PipeOperator) || checkAfterSkip(TokenType.Cascade) || checkForKeyword()) {
        val tok = step() // |> or ;
        val isPipe = tok.kind == TokenType.PipeOperator
        val isCascade = tok.kind == TokenType.Cascade
        val isKeywordAfterUnary = tok.kind == TokenType.Identifier //
        if (isKeywordAfterUnary) step(-1) // we don't need to step on keyword beginning now, after checkForKeyword is added
        skipNewLinesAndComments()
        val nextIsIdent = check(TokenType.Identifier)
        // any msg
        if (nextIsIdent && check(TokenType.Colon, 1)) {
            var last = messages.last()
            // keyword pipe
            messages.add(keyColonCycle().also {
                if (isPipe) {
                    it.isPiped = true
                    it.receiver = last
                    last = it
                } else if (isCascade) {
                    it.isCascade = true
                } else if (isKeywordAfterUnary) {
                    it.receiver = last
                    last = it
                }
            })
        } else if (nextIsIdent) {
            var last = messages.last()
            // unary pipe
            messages.addAll(unaryMessagesMatching(receiver).onEach {
                if (isPipe) {
                    it.isPiped = true
                    it.receiver = last
                    last = it
                } else if (isCascade) {
                    it.isCascade = true
                }
            })
        } else if (check(TokenType.BinarySymbol)) {
            var last = messages.last()
            // binary pipe
            messages.addAll(binaryMessagesMatching(receiver, mutableListOf()).onEach {
                if (isPipe) {
                    it.isPiped = true
                    it.receiver = last
                    last = it
                } else if (isCascade) {
                    it.isCascade = true
                }
            })
        } else {
            if (!GlobalVariables.isLspMode)
                peek().compileError("Can't parse message after pipe operator |>")
            else if (messages.isNotEmpty()) {
                val last = messages.last()
                last.isPiped = true
            }
        }

    }


    return MessageSendKeyword(
        receiver,
        messages,
        null,
        receiver.token
    )
}

fun Parser.keywordSendArgs(stringBuilder: StringBuilder): Triple<MutableList<KeywordArgAst>, IdentifierExpr?, MutableList<IdentifierExpr>> {

    val keyWordArguments = mutableListOf<KeywordArgAst>()
    // next 2 we need for save path like x `Sas.from`: 1 to: 2
    var firstCycle = true
    var firstKeywordIdentifierExpr: IdentifierExpr? = null
    val keysBeforeColons = mutableListOf<IdentifierExpr>()
    do {
        val keywordPart =
            identifierMayBeTyped() //matchAssert(TokenType.Identifier, "Identifier expected inside keyword message send")
        keysBeforeColons.add(keywordPart)

        matchAssert(TokenType.Colon, "Colon expected before argument, inside keyword message send") // skip colon
        // x from: ^3 inc to: 2 inc
        if (firstCycle) {
            firstKeywordIdentifierExpr = keywordPart
        }
        skipNewLinesAndComments()
        val argument = expression(dontParseKeywordsAndUnaryNewLines = true, dot = true, parseSingleIf = true)

        if (argument is KeywordMsg) {
            argument.token.compileError("Argument can't be another keyword message, use ${WHITE}()$RED, ${CYAN}foo: $WHITE(x ${CYAN}bar: ${WHITE}y)")
        }

        // making fun name camelCase
        stringBuilder.append(
            if (firstCycle) keywordPart.name
            else keywordPart.name.capitalizeFirstLetter()
        )

        val x = KeywordArgAst(
            name = keywordPart.name,
            keywordArg = argument,
        )

        keyWordArguments.add(x)

        // if the keyword was split to 2 lines
        if (check(TokenType.EndOfLine) &&
            check(TokenType.Identifier, 1) &&
            check(TokenType.Colon, 2) // next line starting from "key:"
        )
            step() // skip EndOfLine

        firstCycle = false
        skipNewLinesAndComments()
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))

    return Triple(keyWordArguments, firstKeywordIdentifierExpr, keysBeforeColons)
}

fun Parser.keywordMessageParsing(
    receiver: Receiver,
): KeywordMsg {
    val stringBuilder = StringBuilder()
    val (keyWordArguments, firstKeywordIdentifierExpr, keysBeforeColons) = keywordSendArgs(stringBuilder)

    // first "key:"
    val msgTok = keysBeforeColons.first().token

    msgTok.apply {
        val lastKv = keysBeforeColons.last().token
        relPos.end = lastKv.relPos.end
        lineEnd = lastKv.line
    }

    val keywordMsg = KeywordMsg(
        receiver,
        stringBuilder.toString(),
        null,
        msgTok,
        keyWordArguments,
        firstKeywordIdentifierExpr?.names!!,
        declaration = null
    )
    return keywordMsg
}

