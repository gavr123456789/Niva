package frontend.parser

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.types.*
import frontend.util.capitalizeFirstLetter
import frontend.util.isSimpleTypes

data class Module(val name: String, var loaded: Boolean)

class Parser(
    val file: String,
    val tokens: MutableList<Token>,
    val source: String,
    val currentFunction: Statement? = null,
    val scopeDepth: Int = 0,
//    val operators: OperatorTable,
    val tree: MutableList<Statement> = mutableListOf(),
    var current: Int = 0,
    val modules: MutableList<Module> = mutableListOf(),
)


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

fun Parser.done(): Boolean =
    check(TokenType.EndOfFile)

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
            error("expecting token of kind $kind, found ${peek().kind}", token)
        } else {
            error(message)
        }
    }
}

fun Parser.expect(kind: String, message: String = "", token: Token? = null) {
    if (!match(kind)) {
        if (message.isEmpty()) {
            error("expecting token of kind $kind, found ${peek().kind}", token)
        } else {
            error(message)
        }
    }
}
//fun Parser.expect(kind: Iterable<String>, message: String = "", token: Token? = null) {
//
//}

fun Parser.primary(): Primary? =
    when (peek().kind) {
        TokenType.True -> LiteralExpression.TrueExpr(step())
        TokenType.False -> LiteralExpression.FalseExpr(step())
        TokenType.Integer -> LiteralExpression.IntExpr(step())
        TokenType.Float -> LiteralExpression.FloatExpr(step())
        TokenType.String -> LiteralExpression.StringExpr(step())
        TokenType.Identifier -> {
            val x = step()
            val isTyped = check(TokenType.DoubleColon)
            if (isTyped) {
                step() // skip double colon
                val type = parseType()
                IdentifierExpr(x.lexeme, type, x)
            } else {
                IdentifierExpr(x.lexeme, null, x) // look for type in table
            }
        }

        TokenType.LeftParen -> TODO()
        TokenType.LeftBraceHash -> TODO() // set or map
        TokenType.LeftParenHash -> TODO() // ?
        else -> null
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
                if (lastPrimary != null && primaryTok.type?.name != lastPrimary.type?.name) {
                    error("Heterogeneous collections are not supported")
                }
                initElements.add(primaryTok)
            }
            lastPrimary = primaryTok
        } while (primaryTok != null)

        match(TokenType.RightBrace)

        val type = if (initElements.isNotEmpty()) initElements[0].type else null
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
    val valueType: Type?
    when (typeOrEqual.kind) {
        TokenType.Equal -> {
            val isNextReceiver = isNextReceiver()
            value = if (isNextReceiver) receiver() else messageOrControlFlow()
            valueType = value.type
        }
        // ::^int
        TokenType.DoubleColon -> {
            valueType = parseType()
            // x::int^ =
            match(TokenType.Equal)
            value = this.receiver()
        }

        else -> error("after ${peek(-1)} needed type or expression")
    }

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
        TokenType.String -> true

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


fun Parser.messageCall(): MessageCall {
    // x echo // identifier
    // 1 echo // primary
    // (1 + 1) echo // parens
    // [1 2 3] // data structure
    val receiver: Receiver = receiver()
    return anyMessageCall(receiver)
}

fun Parser.messageOrControlFlow(): Expression {
    if (check(TokenType.Pipe)) {
        val isExpression =
            current != 0 && (check(TokenType.Equal, -1) || check(TokenType.Return, -1))


        return ifOrSwitch(isExpression)
    }
    return messageCall()
}

fun Parser.ifOrSwitch(isExpression: Boolean): ControlFlow {

    val wasFirstPipe = check(TokenType.Pipe)
    var wasSecondPipe = false
    var x = 1 // because first is pipe already
    do {
        if (check(TokenType.Then, x)) {
            return ifStatementOrExpression(isExpression)
        }
        // oneline switch
        if (wasFirstPipe && check(TokenType.Pipe, x)) {
            return switchStatementOrExpression(isExpression)
        }
        x++
    } while (!check(TokenType.EndOfLine, x))
    return switchStatementOrExpression(isExpression)
}

fun Parser.getAllUnary(receiver: Receiver): MutableList<UnaryMsg> {
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
    val unaryMessagesForReceiver = getAllUnary(receiver) // inc inc
    val binaryMessages = mutableListOf<BinaryMsg>()
    // if we have more than one binary message, we don't wand unary duplicates like
    // 2 inc + 3 dec + 4 sas // first have inc and dec, second have dec and sas, we don't want dec duplicate
    var needAddMessagesForReceiverForBinary = true
    while (check(TokenType.BinarySymbol)) {

        val binarySymbol = step()
        val binaryArgument = receiver() // 2
        val unaryForArg = getAllUnary(binaryArgument)
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
            // making fun name camelCase
            stringBuilder.append(
                if (firstCicle) keywordPart.lexeme
                else keywordPart.lexeme.capitalizeFirstLetter()
            )

            val x = KeywordArgAndItsMessages(
                selectorName = keywordPart.lexeme,
                keywordArg = keyArg,
                unaryOrBinaryMsgsForArg = unaryOrBinary
            )

            keyWordArguments.add(x)

            // if keyword was split to 2 lines
            if (check(TokenType.EndOfLine)) {
                if (check(TokenType.EndOfLine) &&
                    check(TokenType.Identifier, 1) &&
                    check(TokenType.Colon, 2)
                )
                    step()
            }
            firstCicle = false
        } while (check(TokenType.Identifier) && check(TokenType.Colon, 1))

        val keywordMsg = KeywordMsg(receiver, stringBuilder.toString(), null, receiver.token, keyWordArguments)
        unaryAndBinaryMessages.add(keywordMsg)
        return MessageCall(receiver, unaryAndBinaryMessages, MessageDeclarationType.Keyword, null, receiver.token)
    }
    // unary/binary
    val unaryAndBinaryMessagePair = unaryOrBinary(receiver)
    val unaryAndBinaryMessage = unaryAndBinaryMessagePair.first
    val type = unaryAndBinaryMessagePair.second
    return MessageCall(receiver, unaryAndBinaryMessage, type, null, receiver.token)
}


enum class MessageDeclarationType {
    Unary,
    Binary,
    Keyword
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
fun Parser.statement(): Statement {
    val tok = peek()
    val kind = tok.kind
    // Checks for declarations that starts from keyword like type/fn

    if (tok.kind == TokenType.Identifier &&
        (check(TokenType.DoubleColon, 1) || check(TokenType.Equal, 1))
    ) {
        return varDeclaration()
    }
    if (kind == TokenType.Type) {
        return typeDeclaration()
    }
    if (kind == TokenType.Union) {
        return unionDeclaration()
    }
    if (kind == TokenType.Constructor) {
        return constructorDeclaration()
    }

    return when (isItKeywordDeclaration()) {
        MessageDeclarationType.Unary -> unaryDeclaration()
        MessageDeclarationType.Binary -> binaryDeclaration()
        MessageDeclarationType.Keyword -> keywordDeclaration()
        else -> messageOrControlFlow() // replace with expression which is switch or message
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

fun Parser.returnType(): Type? {
    if (!match(TokenType.ReturnArrow)) {
        return null
    }
    val returnType = parseType()
    return returnType
}

fun Parser.unaryDeclaration(): MessageDeclarationUnary {

    val receiverTypeNameToken =
        matchAssert(TokenType.Identifier, "Its unary message Declaration, name of type expected")

    // int^ inc = []

    val unarySelector = matchAssert(TokenType.Identifier, "Its unary message declaration, unary selector expected")

    val returnType = returnType()
    ///// BODY PARSING

    val pair = methodBody() // (body, is single expression)
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
    val pair = methodBody()
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
private fun Parser.methodBody(): Pair<MutableList<Statement>, Boolean> {
    val isSingleExpression: Boolean
    val messagesOrVarStatements = mutableListOf<Statement>()
    // Person from: x ^= []
    match(TokenType.Equal)
    // many expressions in body
    if (match(TokenType.LeftBracket)) {
        isSingleExpression = false

        match(TokenType.EndOfLine)
        do {
            messagesOrVarStatements.add(messageOrVarDeclaration())
        } while (!match(TokenType.RightBracket))
    } else {
        isSingleExpression = true
        // one expression in body
        messagesOrVarStatements.add(messageOrVarDeclaration())
    }
    return Pair(messagesOrVarStatements, isSingleExpression)
}


fun Parser.typeDeclaration(): TypeDeclaration {
    // type Person name: string generic age: int

    // type Person
    //   name: string

    val typeToken = step() // skip type
    val typeName = matchAssert(TokenType.Identifier, "after \"type\" type identifier expected")
    // type Person^ name: string age: int

    // if type decl separated
    val apostropheOrIdentWithColon = check(TokenType.Apostrophe) ||
            (check(TokenType.Identifier, 1) && check(TokenType.Colon, 2))
    if (check(TokenType.EndOfLine) && apostropheOrIdentWithColon) {
        step()
    }

    val typeFields = typeFields()

    val result = TypeDeclaration(
        typeName = typeName.lexeme,
        fields = typeFields,
        token = typeToken
    )
    return result
}

private fun Parser.typeFields(): MutableList<TypeField> {
    val typeFields = mutableListOf<TypeField>()

    do {
        val isGeneric = match(TokenType.Apostrophe)
        val name = step()
        val type: Type? = if (!isGeneric) {
            matchAssert(TokenType.Colon, "colon before type name expected")
            parseType()
        } else {
            null
        }

        // type declaration can be separated on many lines
        match(TokenType.EndOfFile)
        match(TokenType.EndOfLine)

        typeFields.add(TypeField(name = name.lexeme, type = type, token = name))
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1) || check(TokenType.Apostrophe))
    return typeFields
}

fun Parser.unionDeclaration(): UnionDeclaration {
    val unionTok = matchAssert(TokenType.Union, "")
    val unionName = matchAssert(TokenType.Identifier, "name of the union expected")
    val localFields = typeFields()

    matchAssert(TokenType.Equal, "Equal expected")
    match(TokenType.EndOfLine)

    fun unionFields(): List<UnionBranch> {
        val unionBranches = mutableListOf<UnionBranch>()

        do {
            // | Rectangle => width: int height: int
            val pipeTok = matchAssert(TokenType.Pipe, "pipe expected on each union branch declaration")
            val branchName = matchAssert(TokenType.Identifier, "Name of the union branch expected")

            matchAssert(TokenType.Then, "=> expected")

            val fields = typeFields()

            unionBranches.add(
                UnionBranch(
                    typeName = branchName.lexeme,
                    fields = fields,
                    token = pipeTok
                )
            )
            match(TokenType.EndOfLine)
            match(TokenType.EndOfFile)

        } while (check(TokenType.Pipe))

        return unionBranches
    }

    val unionBranches = unionFields()


    val result = UnionDeclaration(
        typeName = unionName.lexeme,
        branches = unionBranches,
        token = unionTok,
        fields = localFields
    )

    return result
}


fun Parser.ifBranches(): List<IfBranch> {
    val result = mutableListOf<IfBranch>()

    do {

        step() // skip Pipe
        val messageCall = messageCall()

        matchAssert(TokenType.Then, "\"=>\" expected")
        val (body, isSingleExpression) = methodBody()

        result.add(
            if (isSingleExpression) {
                IfBranch.IfBranchSingleExpr(
                    ifExpression = messageCall,
                    body[0] as Expression
                )
            } else {
                IfBranch.IfBranchWithBody(
                    ifExpression = messageCall,
                    body = body
                )
            }
        )


        match(TokenType.EndOfLine)
        match(TokenType.EndOfFile)
    } while (check(TokenType.Pipe))

    return result
}

fun Parser.ifStatementOrExpression(isExpression: Boolean): ControlFlow.If {

    val pipeTok = peek()
    val ifBranches = ifBranches()

    val elseBranch = if (match(TokenType.Else)) {
        methodBody().first.toList()
    } else null


    val result = if (isExpression) {
        if (elseBranch == null) {
            error("else branch is required in control flow expression")
        }
        ControlFlow.IfExpression(
            type = null,
            branches = ifBranches,
            elseBranch = elseBranch,
            token = pipeTok
        )
    } else {
        ControlFlow.IfStatement(
            type = null,
            branches = ifBranches,
            elseBranch = elseBranch,
            token = pipeTok
        )
    }

    return result
}

fun Parser.switchStatementOrExpression(isExpression: Boolean): ControlFlow.Switch {
    val pipeTok = matchAssert(TokenType.Pipe, "")

    val switchExpression = messageCall()

    match(TokenType.EndOfLine)
    val otherPart = ifStatementOrExpression(isExpression)


    return if (isExpression) {
        val result = ControlFlow.SwitchExpression(
            switch = switchExpression,
            iF = otherPart
        )
        result
    } else {
        val result = ControlFlow.SwitchStatement(
            switch = switchExpression,
            iF = otherPart
        )
        result
    }
}


// use only after ::
fun Parser.parseType(): Type {
    // {int} - list of int
    // #{int: string} - map
    // Person - identifier
    // List<Map<int, string>> - generic
    // List(Map(int, string))
    // List::Map::(int, string)
    // Person from: x::List::Map::(int, string)

    val tok = peek()

    // set or map
    if (tok.kind == TokenType.LeftBraceHash) {
        TODO()
    }
    // list
    if (tok.kind == TokenType.LeftBrace) {
        TODO()
    }


    // check for basic type
    when (tok.kind) {
        TokenType.True, TokenType.False -> return Type.InternalType(InternalTypes.boolean, tok)
        TokenType.Float -> return Type.InternalType(InternalTypes.float, tok)
        TokenType.Integer -> return Type.InternalType(InternalTypes.int, tok)
        TokenType.String -> return Type.InternalType(InternalTypes.string, tok)
        else -> {}
    }

    fun parseGenericType(): Type {

        // identifier ("(" | "::")

        // x::List::Map(int, string)
        val identifier = matchAssert(TokenType.Identifier, "in type declaration identifier expected")

        // Map^::(int, string)

        val simpleTypeMaybe = identifier.lexeme.isSimpleTypes()
        // if there is simple type, there cant be any other types like int:: is impossible
        return if (simpleTypeMaybe != null) {
            // int string float or bool
            Type.InternalType(simpleTypeMaybe, identifier)
        } else {
            if (match(TokenType.DoubleColon)) {
//                    need recursion
                return Type.UserType(identifier.lexeme, listOf(parseGenericType()), identifier)
            }
            // Map(Int, String)
            if (match(TokenType.LeftParen)) {
                val typeArgumentList: MutableList<Type> = mutableListOf()
                do {
                    typeArgumentList.add(parseGenericType())
                } while (match(TokenType.Comma))
                matchAssert(TokenType.RightParen, "closing paren in generic type expected")

                return Type.UserType(identifier.lexeme, typeArgumentList, identifier)

            }
            // ::Person

            Type.UserType(identifier.lexeme, listOf(), identifier)
        }

    }

    // tok already eaten so check on distance 0
    if (tok.kind == TokenType.Identifier && (check(TokenType.DoubleColon, 1)) || check(TokenType.LeftParen, 1)) {
        // generic
        // x::List::Map::(int, string)
        return parseGenericType()
    } else if (tok.kind == TokenType.Identifier) {
        step() // skip ident
        // one identifier
        return Type.UserType(
            name = tok.lexeme,
            typeArgumentList = listOf(),
            token = tok
        )
    }


    error("type declaration expected")
}


fun Parser.constructorDeclaration(): ConstructorDeclaration {
    TODO()
}


fun Parser.statementWithEndLine(): Statement {
    val result = this.statement()
    if (check(TokenType.EndOfLine)) {
        step()
    }
    return result
}

fun Parser.statements(): List<Statement> {

    while (!this.done()) {
        this.tree.add(this.statementWithEndLine())
    }

    return this.tree
}
