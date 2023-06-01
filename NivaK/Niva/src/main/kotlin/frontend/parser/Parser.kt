package frontend.parser

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.types.*
import frontend.util.capitalizeFirstLetter

// Unari messages
//class OperatorTable (
//    val tokens: List<String> =
//)

data class Module(val name: String, var loaded: Boolean)

class Parser(
    val file: String,
    val tokens: MutableList<Token>,
    val source: String,
//    val lines: MutableList<Position>,

//    val binaryMessages: MutableSet<String> = hashSetOf(),
//    val unaryMessages: MutableSet<String> = hashSetOf(),
//    val keywordMessages: MutableSet<String> = hashSetOf(),
    val currentFunction: Declaration? = null,
    val scopeDepth: Int = 0,
//    val operators: OperatorTable,
    val tree: MutableList<Declaration> = mutableListOf(),
    var current: Int = 0,
    val modules: MutableList<Module> = mutableListOf(),
) {
    val lookahead: TokenType
        get() = peekKind()

}


fun Parser.getCurrent() = current
fun Parser.getCurrentToken() =
    if (getCurrent() >= tokens.size - 1 || getCurrent() - 1 < 0)
        tokens.elementAt(tokens.size - 1)
    else
        tokens.elementAt(current - 1)

fun Parser.getSource() = source
fun Parser.getCurrentFunction() = currentFunction
fun endOfFile() = Token(
    kind = TokenType.EndOfFile,
    lexeme = "",
    line = -1,
    pos = Position(-1, -1),
    relPos = Position(-1, -1)
)
//fun endOfLine(msg: String, tok: Token? = null) = expect()


fun Parser.peek(distance: Int = 0): Token =
    // check
    if (tokens.size == 0 || current + distance > tokens.size - 1 || current + distance < 0)
        endOfFile()
    else
        tokens[current + distance]

inline fun Parser.peekKind(distance: Int = 0) =
    peek(distance).kind

fun Parser.done(): Boolean =
    peekKind() == TokenType.EndOfFile

fun Parser.step(n: Int = 1): Token {
    val result =
        if (done())
            peek()
        else
            tokens[current]
    current += n
    return result
}

fun Parser.error(message: String, token: Token? = null): Nothing {
    var realToken = token ?: getCurrentToken()
    if (realToken.kind == TokenType.EndOfFile) {
        realToken = peek(-1)
    }
    throw Error("$message\ntoken: $token\nline: ${realToken.line}\nfile: $file\nparser: $this")
}

fun Parser.check(kind: TokenType, distance: Int = 0) =
    peek(distance).kind == kind

fun Parser.check(kind: String, distance: Int = 0) =
    peek(distance).lexeme == kind

fun Parser.check(kind: Iterable<TokenType>): Boolean {
    kind.forEach {
        if (check(it)) {
            return true
        }
    }
    return false
}

fun Parser.checkString(kind: Iterable<String>): Boolean {
    kind.forEach {
        if (check(it)) {
            step()
            return true
        }
    }
    return false
}

fun Parser.match(kind: TokenType) =
    if (check(kind)) {
        step()
        true
    } else {
        false
    }

fun Parser.matchAssert(kind: TokenType, errorMessage: String): Token {
    val tok = peek()

    return if (tok.kind == kind) {
        step()
        tok
    } else {
        error(errorMessage)
    }
}

fun Parser.match(kind: String) =
    if (check(kind)) {
        step() // TODO тут наверн надо делать степ на kind.length
        true
    } else {
        false
    }

fun Parser.match(kind: Iterable<TokenType>): Boolean {
    kind.forEach {
        if (match(it)) {
            return true
        }
    }
    return false
}

fun Parser.matchString(kind: Iterable<String>): Boolean {
    kind.forEach {
        if (match(it)) {
            return true
        }
    }
    return false
}

fun Parser.expect(kind: TokenType, message: String = "", token: Token? = null) {
    if (!match(kind)) {
        if (message.isEmpty()) {
            error("expecting token of kind $kind, found ${peekKind()}", token)
        } else {
            error(message)
        }
    }
}

fun Parser.expect(kind: String, message: String = "", token: Token? = null) {
    if (!match(kind)) {
        if (message.isEmpty()) {
            error("expecting token of kind $kind, found ${peekKind()}", token)
        } else {
            error(message)
        }
    }
}
//fun Parser.expect(kind: Iterable<String>, message: String = "", token: Token? = null) {
//
//}

fun Parser.primary(): Primary? =
    when (peekKind()) {
        TokenType.True -> LiteralExpression.TrueExpr(step())
        TokenType.False -> LiteralExpression.FalseExpr(step())
        TokenType.Integer -> LiteralExpression.IntExpr(step())
        TokenType.Float -> LiteralExpression.FloatExpr(step())
        TokenType.StringToken -> LiteralExpression.StringExpr(step())
        TokenType.Identifier -> {
            val x = step()
            val isTyped = check(TokenType.DoubleColon)
            if (isTyped) {
                step() // skip double colon
                val type = step().lexeme
                IdentifierExpr(x.lexeme, type, x)
            } else {
                IdentifierExpr(x.lexeme, null, x) // look for type in table
            }
        }

        TokenType.LeftParen -> TODO()
        else -> null //this.error("expected primary, but got ${peekKind()}")
    }


// for now only primary is recievers, no indentifiers or expressions
fun Parser.receiver(): Receiver {
    fun blockConstructor() = null
    fun collectionLiteral(): Receiver? {

        val result: ListCollection
        val initElements = mutableListOf<Primary>()
        // {1, 2 3}
        val leftBraceTok = peek()
        if (leftBraceTok.kind != TokenType.LeftBrace) {
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
                if (lastPrimary != null && primaryTok.type != lastPrimary.type) {
                    error("Heterogeneous collections are not supported")
                }
                initElements.add(primaryTok)
            }
            lastPrimary = primaryTok
        } while (primaryTok != null)

        match(TokenType.RightBrace)

        val type = if (initElements.isNotEmpty()) "{${initElements[0].type}}" else null
        result = ListCollection(initElements, type, leftBraceTok)
        return result
    }

    val tryPrimary = primary() ?: blockConstructor() ?: collectionLiteral() ?: throw Error("bruh")

    return tryPrimary
}


fun Parser.varDeclaration(): VarDeclaration {

    val tok = this.step()
    val typeOrEqual = step()

    val value: Expression
    val valueType: String?
    when (typeOrEqual.kind) {
        TokenType.Equal -> {
            val isNextReceiver = isNextReceiver()
            value = if (isNextReceiver) receiver() else message()
            valueType = value.type
        }
        // ::^int
        TokenType.DoubleColon -> {
            valueType = step().lexeme
            // x::int^ =
            match(TokenType.Equal)
            value = this.receiver()
        }

        else -> error("after ${peek(-1)} needed type or expression")
    }

//    val identifierExpr = IdentifierExpr(tok.lexeme, valueType, tok)
    val result = VarDeclaration(tok, tok.lexeme, value, valueType)
    return result
}

fun Token.isPrimaryToken(): Boolean =
    when (kind) {
        TokenType.Identifier,
        TokenType.True,
        TokenType.False,
        TokenType.Integer,
        TokenType.Float,
        TokenType.StringToken -> true

        else -> false
    }

// checks is next thing is receiver
// needed for var declaration to know what to parse - message or value
fun Parser.isNextReceiver(): Boolean {
    if (peek().isPrimaryToken()) {
        when {
            // x = 1
            check(TokenType.EndOfLine, 1) || check(TokenType.EndOfFile, 1) -> return true
            // x = [code]
            check(TokenType.LeftBracket, 1) -> return true
            check(TokenType.LeftParen, 1) -> return true
            check(TokenType.LeftBrace, 1) -> return true
        }
    }

    return false
}


// тк кк у кейвордов у аргументов могут быть бинарные или унарные, а у бинарных могут быть унарные
// то нужно сначала попробовать распарсить кейвордное
// если не выйдет то бинарное
// если не выйдет то унарное


fun Parser.message(): MessageCall {
    // x echo // identifier
    // 1 echo // primary
    // (1 + 1) echo // parens
    // [1 2 3] // data structure
    val receiver: Receiver = receiver()

    val x: MessageCall = anyMessageCall(receiver)

    return x

}


fun Parser.getAllUnaries(receiver: Receiver): MutableList<UnaryMsg> {
    val unaryMessages = mutableListOf<UnaryMsg>()
    while (check(TokenType.Identifier) && !check(TokenType.Colon, 1)) {
        val tok = step()
        val unaryFirstMsg = UnaryMsg(receiver, tok.lexeme, null, tok)
        unaryMessages.add(unaryFirstMsg)
    }

    return unaryMessages
}


fun Parser.unaryOrBinary(receiver: Receiver): Pair<MutableList<out Message>, MessageDeclarationType> {
    // 3 ^inc inc + 2 dec dec + ...
    val unaryMessagesForReceiver = getAllUnaries(receiver) // inc inc
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = step()
        val binaryArgument = receiver() // 2
        val unaryForArg = getAllUnaries(binaryArgument)
        val binaryMsg = BinaryMsg(
            receiver,
            if (needAddMessagesForReceiverForBinary) unaryMessagesForReceiver else listOf(),
            binarySymbol.lexeme,
            null,
            binarySymbol,
            binaryArgument,
            unaryForArg
        )
        binaryMessages.add(binaryMsg)
        needAddMessagesForReceiverForBinary = false
    }

    // if there is no binary message, that's mean there is only unary
    if (binaryMessages.isEmpty()) {
        return Pair(unaryMessagesForReceiver, MessageDeclarationType.Unary)  //as MutableList<Message>
    }
    // its binary msg
    return Pair(binaryMessages, MessageDeclarationType.Binary)
}


// возможно стоит хранить сообщения предназначенные для аргументов кейводр сообщения вместе
fun Parser.anyMessageCall(receiver: Receiver): MessageCall {


    // keyword
    if (check(TokenType.Identifier) && check(TokenType.Colon, 1)) {
        val stringBuilder = StringBuilder()
        val unaryAndBinaryMessages = mutableListOf<Message>()
        val keyWordArguments = mutableListOf<KeywordArgAndItsMessages>()
        var firstCicle = true

        do {
            val keywordPart = step()
            step()// skip colon
            // x from: ^3 inc to: 2 inc
            val keyArg = receiver()
            // x from: 3 ^inc to: 2 inc
            val unaryOrBinaryPair = unaryOrBinary(receiver)
            val unaryOrBinary = unaryOrBinaryPair.first
            stringBuilder.append(if (firstCicle) keywordPart.lexeme else keywordPart.lexeme.capitalizeFirstLetter())

            val x = KeywordArgAndItsMessages(
                selectorName = keywordPart.lexeme,
                keywordArg = keyArg,
                unaryOrBinaryMsgsForArg = unaryOrBinary
            )

            keyWordArguments.add(x)

            // if keyword was split to 2 lines
            if (check(TokenType.EndOfLine)) {
                if (check(TokenType.Identifier, 1) && check(TokenType.Colon, 2))
                    step()
            }
            firstCicle = false
        } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))

        val keywordMsg = KeywordMsg(receiver, stringBuilder.toString(), null, receiver.token, keyWordArguments)
        unaryAndBinaryMessages.add(keywordMsg)
        return MessageCall(receiver, unaryAndBinaryMessages, MessageDeclarationType.Keyword,null, receiver.token)
    }
    // unary/binary
    val unaryAndBinaryMessagePair = unaryOrBinary(receiver)
    val unaryAndBinaryMessage = unaryAndBinaryMessagePair.first
    val type = unaryAndBinaryMessagePair.second
    return MessageCall(receiver, unaryAndBinaryMessage, type, null, receiver.token)
}

fun TokenType.isPrimeToken() =
    when (this) {
        TokenType.Identifier,
        TokenType.Float,
        TokenType.StringToken,
        TokenType.Integer,
        TokenType.True,
        TokenType.False -> true

        else -> false
    }


enum class MessageDeclarationType {
    Unary,
    Binary,
    Keyword
}

// returns null if it's not a message declaration
fun Parser.isItKeywordDeclaration(): MessageDeclarationType? {
    // receiver is first
    if (!check(TokenType.Identifier)) {
        return null
    }
    // flags for keyword
    // from[:] ... [=]
    var isThereIdentColon = false
    var isThereEqualAfterThat = false
    // unary
    var isThereEqual = false
    var identifiersCounter = 0

    var peekCounter = 0

    // is there ident: and = after that before end of line?
    while (!(check(TokenType.EndOfLine, peekCounter) || check(TokenType.EndOfFile, peekCounter))) {
        val q = peek(peekCounter)


        // keyword checks
        if (q.kind == TokenType.Identifier && check(TokenType.Colon, peekCounter + 1)) {
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


// Declaration without end of line
fun Parser.declarationOnly(): Declaration {
    val tok = peek()
    val kind = tok.kind
    val result: Declaration
    // Checks for declarations that starts from keyword like type/fn

    val w = tok.kind == TokenType.Identifier
    val w2 = check(TokenType.DoubleColon, 1)
    val w3 = check(TokenType.Equal, 1)

    if (tok.kind == TokenType.Identifier &&
        (check(TokenType.DoubleColon, 1) || check(TokenType.Equal, 1))
    ) {
        return varDeclaration()
    }
    if (kind == TokenType.Type) TODO()

    val q = isItKeywordDeclaration()
    return when (isItKeywordDeclaration()) {
        MessageDeclarationType.Unary -> unaryDeclaration()
        MessageDeclarationType.Binary -> binaryDeclaration()
        MessageDeclarationType.Keyword -> keywordDeclaration()
        else -> message() // replace with expression which is switch or message
    }
}

fun Parser.messageOrVarDeclaration(): Declaration {
    val result = if (check(TokenType.Identifier) &&
        (check(TokenType.DoubleColon, 1) || check(TokenType.Equal, 1))
    ) {
        varDeclaration()
    } else {
        message()
    }

    if (check(TokenType.EndOfLine)) {
        step()
    }
    return result
}

fun Parser.returnType(): String? {
    if (!match(TokenType.ReturnArrow)) {
        return null
    }
    val returnType = matchAssert(TokenType.Identifier, "after arrow return type expected")
    return returnType.lexeme
}

fun Parser.unaryDeclaration(): MessageDeclarationUnary {

    val receiverTypeNameToken =
        matchAssert(TokenType.Identifier, "Its unary message Declaration, name of type expected")

    // int^ inc = []

    val unarySelector = matchAssert(TokenType.Identifier, "Its unary message declaration, unary selector expected")

    val returnType = returnType()
    ///// BODY PARSING

    val pair = body() // (body, is single expression)
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.RightBracket)
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
        matchAssert(TokenType.Identifier, "Its Keyword message Declaration, name of type expected")

    // int^ + x = []

    val binarySelector = matchAssert(TokenType.BinarySymbol, "Its unary message declaration, unary selector expected")
    // int + ^x = []
    // int + ^x::int = []

    // arg

    val argName = matchAssert(TokenType.Identifier, "in binary message identifier after operator expected")
    val typeName =
        if (match(TokenType.DoubleColon))
            matchAssert(TokenType.Identifier, "after double colon type expected").lexeme
        else null
    val arg = (KeywordDeclarationArg(name = argName.lexeme, type = typeName))
    val returnType = returnType()

    // BODY PARSING
    val pair = body() // (body, is single expression)
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.RightBracket)
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
        matchAssert(TokenType.Identifier, "Its Keyword message Declaration, name of type expected")

    val args = mutableListOf<KeywordDeclarationArg>()
    do {
        // it can be no type no local name :key
        // type, no local name key::int      key2::string
        // type and local name: to: x::int   from: y::int

        args.add(keyArg())


    } while (!check(TokenType.Equal))


    val returnType = returnType()

    // BODY PARSING
    val pair = body()
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.RightBracket)
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
        val typeName = step()
        if (typeName.kind != TokenType.Identifier) {
            error("You tried to declare keyword message with arg without local name, type expected after colon double foobar::type")
        }
        return (KeywordDeclarationArg(name = argName.lexeme, type = typeName.lexeme))
    }
    // key: localName(::int)?
    else {
        val key = step()
        match(TokenType.Colon)
        val local = step()
        val typename: String? = if (check(TokenType.DoubleColon)) {
            step()// skip doubleColon
            step().lexeme
        } else {
            null
        }

        return (KeywordDeclarationArg(name = key.lexeme, localName = local.lexeme, type = typename))

    }
}

private fun Parser.body(): Pair<MutableList<Declaration>, Boolean> {
    val isSingleExpression: Boolean
    val messagesOrVarDeclarations = mutableListOf<Declaration>()
    // Person from: x ^= []
    match(TokenType.Equal)
    // many expressions in body
    if (match(TokenType.LeftBracket)) {
        isSingleExpression = false

        match(TokenType.EndOfLine)
        do {
            messagesOrVarDeclarations.add(messageOrVarDeclaration())
        } while (!check(TokenType.RightBracket))
    } else {
        isSingleExpression = true
        // one expression in body
        messagesOrVarDeclarations.add(messageOrVarDeclaration())
    }
    return Pair(messagesOrVarDeclarations, isSingleExpression)
}

fun Parser.declaration(): Declaration {
    val result = this.declarationOnly()
    if (check(TokenType.EndOfLine)) {
        step()
    }
    return result
}

fun Parser.declarations(): List<Declaration> {

    while (!this.done()) {
        this.tree.add(this.declaration())
    }

    return this.tree
}
