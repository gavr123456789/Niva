package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.isIdentifier
import frontend.parser.types.ast.*

// also recevier can be unary or binary message
fun Parser.receiver(): Receiver {
    fun blockConstructor() = null
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


    val tryPrimary = primary() ?: blockConstructor() ?: collectionLiteral() ?: throw Error("bruh")

    return tryPrimary
}

fun Parser.returnType(): Type? {
    if (!match(TokenType.ReturnArrow)) {
        return null
    }
    val returnType = parseType()
    return returnType
}

fun Parser.unaryDeclaration(): MessageDeclarationUnary {

    val receiverTypeNameToken =
        matchAssertAnyIdent("Its unary message Declaration, name of type expected")

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
        token = receiverTypeNameToken,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression
    )
    return result
}

fun Parser.binaryDeclaration(): MessageDeclarationBinary {

    val receiverTypeNameToken =
        matchAssertAnyIdent("Its Keyword message Declaration, name of type expected")

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
        token = receiverTypeNameToken,
        arg = arg,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression
    )
    return result
}

fun Parser.keywordDeclaration(): MessageDeclarationKeyword {

    val receiverTypeNameToken =
        matchAssertAnyIdent("Its Keyword message Declaration, name of type expected")

    val args = mutableListOf<KeywordDeclarationArg>()
    do {
        // it can be no type no local name :key
        // type, no local name key::int      key2::string
        // type and local name: to: x::int   from: y::int

        args.add(keyArg())


    } while (!check(TokenType.Equal))


    val returnType = returnType()

    // BODY PARSING
    val pair = methodBody()
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.CloseBracket)
    }

    val keywordMessageName = args.map { it.name }.joinToString("_") { it }
    val result = MessageDeclarationKeyword(
        name = keywordMessageName,
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
        val type: Type? = if (check(TokenType.DoubleColon)) {
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
    match(TokenType.Equal)
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

        if (isThereIdentColon && check(TokenType.Equal, peekCounter)) {
            isThereEqualAfterThat = true
            break
        }
        if (check(TokenType.Equal, peekCounter)) {
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
        (check(TokenType.DoubleColon, 1) || check(TokenType.Equal, 1))
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
        constructorKeyword
    )
    return result
}
