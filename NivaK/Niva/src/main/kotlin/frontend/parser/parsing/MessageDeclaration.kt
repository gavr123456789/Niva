package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.isIdentifier
import frontend.parser.types.ast.*
import frontend.util.capitalizeFirstLetter

// also recevier can be unary or binary message

// receiver examples - (receiver)
// (1) sas // simple unary
// (1) + 2 // simple binary
// (1) to: 2 // simple keyword
// (1 sas) to: 2 // keyword with unary receiver
// (1 + 2) to: 2 // keyword with binary receiver
// (1 inc + 2 inc) to: 2 // keyword with binary receiver
// So unary and binary messages can be the receiver,
// also collections
// also code blocks


fun Parser.messageOrPrimaryReceiver(): Receiver {

    val safePoint = current
    try {
        val q = unaryOrBinary(false)

        val nextIsKeywordSend = {
            val q = check(TokenType.Identifier)
            val w = check(TokenType.Colon, 1)
//            check(TokenType.Identifier) && check(TokenType.Colon, 1)
            checkForKeyword()
        }

        when (q) {
            is MessageSendUnary, is MessageSendBinary -> {
                if (q.messages.isNotEmpty() && nextIsKeywordSend()) {
                    assert(q.messages.count() == 1)
                    // if followed by keyword
                    return q.messages[0]
                }
            }
            is MessageSendKeyword -> error("keyword cant be a receiver, for now")// need pipe operator
        }
    } catch (e: Throwable) {
        current = safePoint
    }
    current = safePoint
    return receiver()
}


// receiver like collection, code block, identifier,

// this is also expression
fun Parser.receiver(): Receiver {

    if (check(TokenType.OpenBracket)) {
        return codeBlock()
    }


    fun collectionLiteral(): Receiver? {

        val result: ListCollection
        val initElements = mutableListOf<Primary>()
        // {1, 2 3}
        val leftBraceTok = peek()
        if (leftBraceTok.kind != TokenType.OpenBrace) {
            return null
        }

        step() // skip leftBrace

        // cycle that eats primary with optional commas
        // for now, messages inside collection literals are impossible

        var lastPrimary: Primary? = null
        do {
            val primaryTok = primary()
            match(TokenType.Comma)
            if (primaryTok != null) {
                if (lastPrimary != null && primaryTok.type?.name != lastPrimary.type?.name) {
                    error("Heterogeneous collections are not supported")
                }
                initElements.add(primaryTok)
            }
            lastPrimary = primaryTok
        } while (primaryTok != null)

        match(TokenType.CloseBrace)

        val type = if (initElements.isNotEmpty()) initElements[0].type else null
        result = ListCollection(initElements, type, leftBraceTok)
        return result
    }


    val tryPrimary = primary()
        ?: collectionLiteral()
        ?: throw Error("bruh")

    return tryPrimary
}

fun Parser.returnType(): TypeAST? {
    if (!match(TokenType.ReturnArrow)) {
        return null
    }
    val returnType = parseType()
    return returnType
}

fun Parser.unaryDeclaration(): MessageDeclarationUnary {

//    val receiverTypeNameToken =
//        matchAssertAnyIdent("Its unary message Declaration, name of type expected")
    val receiverTypeNameToken = peek()
    val forType = parseType()
    // int^ inc = []

    val unarySelector = matchAssertAnyIdent("Its unary message declaration, unary selector expected")

    val returnType = returnType()
    ///// BODY PARSING

    val pair = methodBody() // (body, is single expression)
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.CloseBracket)
    }

    val result = MessageDeclarationUnary(
        name = unarySelector.lexeme,
        forType = forType,
        token = receiverTypeNameToken,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression
    )
    return result
}

fun Parser.binaryDeclaration(): MessageDeclarationBinary {

    val receiverTypeNameToken = peek()
//        matchAssertAnyIdent("Its Keyword message Declaration, name of type expected")
    val forType = parseType()

    // int^ + x = []

    val binarySelector = matchAssert(TokenType.BinarySymbol, "Its unary message declaration, unary selector expected")
    // int + ^x = []
    // int + ^x::int = []

    // arg

    val argName = matchAssertAnyIdent("in binary message identifier after operator expected")
    val typeName =
        if (match(TokenType.DoubleColon))
            parseType()
        else null
    val arg = (KeywordDeclarationArg(name = argName.lexeme, type = typeName))
    val returnType = returnType()

    // BODY PARSING
    val pair = methodBody() // (body, is single expression)
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.CloseBracket)
    }

    val result = MessageDeclarationBinary(
        name = binarySelector.lexeme,
        forType = forType,
        token = receiverTypeNameToken,
        arg = arg,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression
    )
    return result
}

/**
 * Parses a keyword message declaration, which follows the format:
 *  - Receiver type, followed by arguments.
 *  - Optional return type.
 *  - Body with messages or variable declarations.
 * The message name for the keyword message is produced by concatenating the argument names with capitalized first letters.
 * The function returns a [MessageDeclarationKeyword] object representing the parsed keyword message declaration.
 */
fun Parser.keywordDeclaration(): MessageDeclarationKeyword {

    val receiverTypeNameToken = peek()
//        matchAssertAnyIdent("Its Keyword message Declaration, name of type expected")
    val forType = parseType()

    val args = mutableListOf<KeywordDeclarationArg>()

    do {
        // it can be no type no local name :key
        // type, no local name key::int      key2::string
        // type and local name: to: x::int   from: y::int

        args.add(keyArg())


    } while (!(check(TokenType.Assign) || check(TokenType.ReturnArrow)))


    val returnType = returnType()

    // BODY PARSING
    val pair = methodBody()
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.CloseBracket)
    }

    val keywordMessageName = args[0].name + args.drop(1).map { it.name.capitalizeFirstLetter() }.joinToString("") { it }
    val result = MessageDeclarationKeyword(
        name = keywordMessageName,
        forType = forType,
        token = receiverTypeNameToken,
        args = args,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression
    )
    return result
}

// x::int or x: local::int or x: local or :x
private fun Parser.keyArg(): KeywordDeclarationArg {
    val noLocalNameNoType = check(TokenType.Colon)
    val noLocalName = check(TokenType.Identifier) && check(TokenType.DoubleColon, 1)
    // :foo
    if (noLocalNameNoType) {
        step() //skip colon
        val argName = step()
        if (argName.kind != TokenType.Identifier) {
            error("You tried to declare keyword message with arg without type and local name, identifier expected after colon :foobar")
        }
        return (KeywordDeclarationArg(name = argName.lexeme))
    }
    // arg::int
    else if (noLocalName) {
        val argName = step()
        if (argName.kind != TokenType.Identifier) {
            error("You tried to declare keyword message with arg without local name, identifier expected before double colon foobar::type")
        }
        match(TokenType.DoubleColon)
        val type = parseType()
        return (KeywordDeclarationArg(name = argName.lexeme, type = type))
    }
    // key: localName(::int)?
    else {
        val key = step()
        match(TokenType.Colon)
        val local = step()
        val type: TypeAST? = if (check(TokenType.DoubleColon)) {
            step()// skip doubleColon
            parseType()
        } else {
            null
        }

        return (KeywordDeclarationArg(name = key.lexeme, localName = local.lexeme, type = type))

    }
}

// returns true if it's single expression
fun Parser.methodBody(): Pair<MutableList<Statement>, Boolean> {
    val isSingleExpression: Boolean
    val messagesOrVarStatements = mutableListOf<Statement>()
    // Person from: x ^= []
    match(TokenType.Assign)
    // many expressions in body
    if (match(TokenType.OpenBracket)) {
        isSingleExpression = false

        match(TokenType.EndOfLine)
        do {
            messagesOrVarStatements.add(messageOrVarDeclaration())
        } while (!match(TokenType.CloseBracket))
    } else {
        isSingleExpression = true
        // one expression in body
        messagesOrVarStatements.add(messageOrVarDeclaration())
    }
    return Pair(messagesOrVarStatements, isSingleExpression)
}

// returns null if it's not a message declaration
// very bad function
fun Parser.isItKeywordDeclaration(): MessageDeclarationType? {
    // receiver is first
    if (!check(TokenType.Identifier)) {
        return null
    }
    // flags for keyword
    // from[:] ... [=]
    var isThereIdentColon = false
    var isThereEqualAfterThat = false
    // for unary
    var isThereEqual = false

    var peekCounter = 0

    // is there ident: and = after that before end of line?
    while (!(check(TokenType.EndOfLine, peekCounter) || check(TokenType.EndOfFile, peekCounter))) {
        val q = peek(peekCounter)


        // keyword checks
        if (q.isIdentifier() && check(TokenType.Colon, peekCounter + 1)) {
            isThereIdentColon = true
        }

        if (isThereIdentColon && check(TokenType.Assign, peekCounter)) {
            isThereEqualAfterThat = true
            break
        }
        if (check(TokenType.Assign, peekCounter)) {
            isThereEqual = true
        }
        peekCounter++

    }
    if (isThereIdentColon && isThereEqualAfterThat) {
        return MessageDeclarationType.Keyword
    }

    // unary and binary
    // Identifier checked already

    // int inc = []
    if (check(TokenType.Identifier, 1) && isThereEqual) {
        return MessageDeclarationType.Unary
    }

    // int + arg =

    if (check(TokenType.BinarySymbol, 1) && check(TokenType.Identifier, 2) && isThereEqual) {
        return MessageDeclarationType.Binary
    }

    return null
}

enum class MessageDeclarationType {
    Unary,
    Binary,
    Keyword
}

fun Parser.messageDeclaration(type: MessageDeclarationType): MessageDeclaration {
    return when (type) {
        MessageDeclarationType.Unary -> unaryDeclaration()
        MessageDeclarationType.Binary -> binaryDeclaration()
        MessageDeclarationType.Keyword -> keywordDeclaration()
    }
}

fun Parser.messageOrVarDeclaration(): Statement {
    val result = if (check(TokenType.Identifier) &&
        (check(TokenType.DoubleColon, 1) || check(TokenType.Assign, 1))
    ) {
        varDeclaration()
    } else {
        messageOrControlFlow()
    }

    if (check(TokenType.EndOfLine)) {
        step()
    }
    return result
}

// constructor TYPE messageDeclaration
fun Parser.constructorDeclaration(): ConstructorDeclaration {
    val constructorKeyword = matchAssert(TokenType.Constructor, "Constructor expected")

    val isItKeywordDeclaration = isItKeywordDeclaration()
    val msgDecl = if (isItKeywordDeclaration != null) {
        messageDeclaration(isItKeywordDeclaration)
    } else null

    if (msgDecl == null) {
        error("message declaration after constructor expected")
    }

    val result = ConstructorDeclaration(
        msgDeclarationKeyword = msgDecl,
        forType = msgDecl.forType,
        constructorKeyword
    )
    return result
}
