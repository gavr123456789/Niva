package frontend.parser.parsing

import frontend.parser.types.ast.*
import main.utils.RED
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.parsing.parseTypeAST
import main.frontend.parser.types.ast.*
import main.frontend.parser.types.ast.TypeAST
import main.utils.capitalizeFirstLetter


fun Parser.returnType(): TypeAST? {
    if (!match(TokenType.ReturnArrow)) {
        return null
    }
    val returnType = parseTypeAST()
    return returnType
}

fun Parser.unaryDeclaration(forTypeAst: TypeAST): MessageDeclarationUnary {

    // int^ inc = []

    val unarySelector = matchAssertAnyIdent("Its unary message declaration, unary selector expected")

    val returnType = returnType()
    ///// BODY PARSING

    val isInline = match(TokenType.Return)
    val isSuspend = match(">>") // Int sas >>= []

    val pair = methodBody() // (body, is single expression)
    val messagesOrVarDeclarations = pair.first
    val isSingleExpression = pair.second
    // end of body parsing

    if (!isSingleExpression) {
        match(TokenType.CloseBracket)
    }

    val result = MessageDeclarationUnary(
        name = unarySelector.lexeme,
        forType = forTypeAst,
        token = unarySelector,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression,
        isInline = isInline,
        isSuspend = isSuspend

    )
    return result
}

fun Parser.binaryDeclaration(forType: TypeAST): MessageDeclarationBinary {
    // int^ + x = []

    val binarySelector = matchAssert(TokenType.BinarySymbol, "Its binary message declaration, binary selector expected")
    // int + ^x = []
    // int + ^x::int = []

    // arg

    val argName = matchAssertAnyIdent("in binary message identifier after operator expected")
    val typeName =
        if (match(TokenType.DoubleColon))
            parseTypeAST()
        else null
    val arg = (KeywordDeclarationArg(name = argName.lexeme, argName, typeAST = typeName))
    val returnType = returnType()

    // BODY PARSING

    val isInline = match(TokenType.Return)
    val isSuspend = match(">>") // Int sas >>= []

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
        token = binarySelector,
        arg = arg,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression,
        isInline = isInline,
        isSuspend = isSuspend
    )
    return result
}


// returns kw args before `->` or `=`
fun Parser.keywordArgs(): MutableList<KeywordDeclarationArg> {
    val args = mutableListOf<KeywordDeclarationArg>()

    do {
        // it can be no type no local name :key
        // type, no local name key::int      key2::string
        // type and local name: to: x::int   from: y::int
        skipNewLinesAndComments()
        args.add(keyArg())
        skipNewLinesAndComments()

    } while (!(check(TokenType.Assign) || check(TokenType.ReturnArrow) || check(">>")))
    return args
}

/**
 * Parses a keyword message declaration, which follows the format:
 *  - Receiver type, followed by arguments.
 *  - Optional return type.
 *  - Body with messages or variable declarations.
 * The message name for the keyword message is produced by concatenating the argument names with capitalized first letters.
 * The function returns a [MessageDeclarationKeyword] object representing the parsed keyword message declaration.
 */
fun Parser.keywordDeclaration(forType: TypeAST): MessageDeclarationKeyword {
    val args = keywordArgs()

    val returnType = returnType()

    // BODY PARSING
    val isInline = match(TokenType.Return)
    val isSuspend = match(">>") // Int sas >>= []

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
        token = if (args.isNotEmpty()) args.first().tok else forType.token,
        args = args,
        body = messagesOrVarDeclarations,
        returnType = returnType,
        isSingleExpression = isSingleExpression,
        isInline = isInline,
        isSuspend = isSuspend
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
            argName.compileError("You tried to declare keyword message with arg without type and local name, identifier expected after colon :foobar")
        }
        return (KeywordDeclarationArg(name = argName.lexeme, argName))
    }
    // arg::int
    else if (noLocalName) {
        val argName = step()
        if (argName.kind != TokenType.Identifier) {
            error("You tried to declare keyword message with arg without local name, identifier expected before double colon foobar::type")
        }
        match(TokenType.DoubleColon)
        val type = parseTypeAST()
        return (KeywordDeclarationArg(name = argName.lexeme, argName, typeAST = type))
    }

    // key: localName(::int)?
    else {
        val key = matchAssert(TokenType.Identifier)
        match(TokenType.Colon)
        val local = step()
        val type: TypeAST? = if (check(TokenType.DoubleColon)) {
            step()// skip doubleColon
            parseTypeAST()
        } else {
            null
        }

        val result = KeywordDeclarationArg(name = key.lexeme, key, localName = local.lexeme, typeAST = type)
        return result

    }
}


// returns true if it's single expression
fun Parser.methodBody(
    parseOnlyOneLineIfNoBody: Boolean = false // methodBody also used in control flow, where `=` is not needed
): Pair<MutableList<Statement>, Boolean> {
    val isSingleExpression: Boolean
    val messagesOrVarStatements = mutableListOf<Statement>()
    // Person from: x ^= []
    val isThereAssignOrThen = match(TokenType.Assign) || parseOnlyOneLineIfNoBody
    if (!isThereAssignOrThen) {
        return Pair(mutableListOf(), false)
    }

    // many expressions in body
    if (match(TokenType.OpenBracket)) {
        isSingleExpression = false

        skipNewLinesAndComments()
        while (!match(TokenType.CloseBracket)) {
            messagesOrVarStatements.add(statementWithEndLine())
        }
    } else {
        isSingleExpression = true
        // one expression in body
        // if we inside control flow then dont skip lines, because
        // switch with if after, without new line can't be parsed
        // | switch
        // | cond => do
        // | cond => do // I wanna If here, but it will be another case
        if (peek().kind == TokenType.EndOfFile) {
            peek(-1).compileError("body expected")
        }
        if (parseOnlyOneLineIfNoBody) {
            messagesOrVarStatements.add(statementWithEndLine(false)) // expression(parseSingleIf = true)
        } else {
            val docComment = parseDocComment()
            val statement = statement(parseMsgDecls = false).also { if (docComment != null) it.docComment = docComment }
            messagesOrVarStatements.add(statement)
        }
    }

    val realIsSingleExpression = isSingleExpression && messagesOrVarStatements[0] is Expression

    return Pair(messagesOrVarStatements, realIsSingleExpression)
}


// Int sas ^ (-> Type)? (^)?(>>)?(=)?
fun Parser.isThereEndOfMessageDeclaration(isConstructorOrOn: Boolean): Boolean {
    if (isConstructorOrOn) return true

    var isThereEqual = false

    val returnArrow = match(TokenType.ReturnArrow)
    if (returnArrow) {
        return true
    }
    match(TokenType.Return) // (^)?
    match(">>")             // (>>)?
    val equal = match(TokenType.Assign)
    if (equal) isThereEqual = true

//    return

    return isThereEqual
}

fun Parser.tryUnary(isConstructor: Boolean): Boolean {
    val savepoint = current

    if (check(TokenType.Identifier) && (!check(TokenType.DoubleColon, 1) && !check(TokenType.Identifier, 1) && !check(
            TokenType.Colon,
            1
        ))
    ) {
        match(TokenType.Identifier)
        val isThereEndOfMsgDecl = isThereEndOfMessageDeclaration(isConstructor)
        if (isThereEndOfMsgDecl)
            return true
    }
    current = savepoint
    return false
}

fun Parser.tryBinary(isConstructor: Boolean): Boolean {
    val savepoint = current

    if (match(TokenType.BinarySymbol) && check(TokenType.Identifier)) {
        // + ^x::Type
        identifierMayBeTyped()
        // + x::Type^
        if (isThereEndOfMessageDeclaration(isConstructor))
            return true
    }


    current = savepoint
    return false
}


fun Parser.kwArgsAndEndOfMessageDeclaration(isConstructor: Boolean): Boolean {
    var keyArgsCounter = 0
    while (!(check(TokenType.Assign) || check(TokenType.ReturnArrow))) {
        try {
            skipNewLinesAndComments()
            if ((checkMany(TokenType.Identifier, TokenType.DoubleColon)) ||
                (checkMany(TokenType.Identifier, TokenType.Colon, TokenType.Identifier))
            ) {
                keyArg()
                keyArgsCounter++
            } else {
                return if (keyArgsCounter > 0)
                    isThereEndOfMessageDeclaration(isConstructor)
                else
                    false
            }
        } catch (_: Exception) {
            return isThereEndOfMessageDeclaration(isConstructor)
        }
    }
    return isThereEndOfMessageDeclaration(isConstructor)

}

fun Parser.tryKeyword(isConstructor: Boolean): Boolean {
    val savepoint = current

    if (kwArgsAndEndOfMessageDeclaration(isConstructor)) {
        return true
    }


    current = savepoint
    return false
}

fun Parser.checkTypeOfMessageDeclaration2(
    isConstructor: Boolean = false,
    parseReceiver: Boolean = true
): MessageDeclarationType? {
    val savepoint = current
    match(TokenType.Mut)
    val t = peek()
    // any message declaration starts with identifier or binary symbol when "on + x::sas"
    if (t.kind != TokenType.Identifier && t.kind != TokenType.NullableIdentifier && t.kind != TokenType.BinarySymbol)
        return null

    // receiver can be typed List::Int sas = []
    if (parseReceiver)
    // parse complex receiver like Map(Int, Int)
        if (check(TokenType.OpenParen, 1) && !check(TokenType.Colon, 3))
            parseTypeAST()
        else identifierMayBeTyped() // simple List::Int

    if (tryUnary(isConstructor)) {
        current = savepoint
        return MessageDeclarationType.Unary
    }

    if (tryKeyword(isConstructor)) {
        current = savepoint
        return MessageDeclarationType.Keyword
    }

    if (tryBinary(isConstructor)) {
        current = savepoint
        return MessageDeclarationType.Binary
    }

    current = savepoint

    return null
}

enum class MessageDeclarationType {
    Unary,
    Binary,
    Keyword
}

fun Parser.messageDeclaration(
    type: MessageDeclarationType,
    pragmas: MutableList<Pragma>? = null,
    customForTypeAst: TypeAST? = null,
): MessageDeclaration {

    val forTypeAst = customForTypeAst ?: parseTypeAST()
    val result = when (type) {
        MessageDeclarationType.Unary -> unaryDeclaration(forTypeAst)
        MessageDeclarationType.Binary -> binaryDeclaration(forTypeAst)
        MessageDeclarationType.Keyword -> keywordDeclaration(forTypeAst)
    }
    if (pragmas != null) {
        result.pragmas = pragmas
    }
    return result
}


// extend Person [ ^ on unary = [] ...]
fun Parser.onMessageDeclList(
    forTypeAst: TypeAST,
    pragmasForExtend: MutableList<Pragma>
): MutableList<MessageDeclaration> {
    val list = mutableListOf<MessageDeclaration>()
    do {
        val pragmas = if (check("@")) pragmas() else mutableListOf()
        pragmas.addAll(pragmasForExtend)
        val docComment = parseDocComment()
        matchAssert(TokenType.On)
        val isItMsgDeclaration = checkTypeOfMessageDeclaration2(parseReceiver = false)
            ?: peek().compileError("Can't parse message declaration $RED${peek().lexeme}")

        val msgDecl = messageDeclaration(isItMsgDeclaration, pragmas, forTypeAst)
        msgDecl.docComment = docComment
        list.add(msgDecl)

        skipNewLinesAndComments()
    } while (!match(TokenType.CloseBracket))
    return list
}

fun Parser.extendDeclaration(pragmasForExtend: MutableList<Pragma>): ExtendDeclaration {
    // extend Person [
    match("extend")

    val forTypeAst = parseTypeAST(true)
    skipNewLinesAndComments()
    matchAssert(TokenType.OpenBracket)
    skipNewLinesAndComments()

    val list = onMessageDeclList(forTypeAst, pragmasForExtend)

    return ExtendDeclaration(
        messageDeclarations = list,
        token = forTypeAst.token
    )

}


const val ConstructorExpected = "Constructor expected"

// constructor TYPE messageDeclaration
fun Parser.constructorDeclaration(pragmas: MutableList<Pragma>): ConstructorDeclaration {
    val isFun = check(TokenType.Fun)
    val constructorKeyword = if (isFun) matchAssert(TokenType.Fun) else matchAssert(TokenType.Constructor, ConstructorExpected)

    val messageDeclarationType =
        checkTypeOfMessageDeclaration2(true)//checkTypeOfMessageDeclaration(isConstructor = true)
    val msgDecl = if (messageDeclarationType != null) {
        messageDeclaration(messageDeclarationType, pragmas)
    } else null

    if (msgDecl == null) {
        error("message declaration after constructor expected")
    }

    val result = ConstructorDeclaration(
        msgDeclaration = msgDecl,
        constructorKeyword,
    )
    return result
}

fun Parser.manyConstructorsDecl(pragmas: MutableList<Pragma>): ManyConstructorDecl {
    matchAssert(TokenType.Constructor, ConstructorExpected)
    val forTypeAst = parseTypeAST(true)
    skipNewLinesAndComments()
    matchAssert(TokenType.OpenBracket)
    skipNewLinesAndComments()

    val list = this.onMessageDeclList(forTypeAst, pragmas)

    val result = ManyConstructorDecl(
        messageDeclarations = list.map {
            ConstructorDeclaration(
                msgDeclaration = it,
                token = it.token
            )
        },
        token = forTypeAst.token,
        pragmas = pragmas
    )
    return result
}


fun Parser.builderDeclarationWithReceiver(pragmas: MutableList<Pragma>): StaticBuilderDeclaration {
    val receiver = parseTypeAST()
    val builderDecl = builderDeclaration(pragmas, receiver)
    return builderDecl
}

// builder name key-args lambdaArg -> Type = []
// receiver is null when its builder without receiver `builder StringBuilder buildString = []`
fun Parser.builderDeclaration(pragmas: MutableList<Pragma>, receiver: TypeAST? = null): StaticBuilderDeclaration {
    val builderKeyword = matchAssert(TokenType.Builder)
    val receiverTypeOrName = parseTypeAST()
    // if next is not arguments and this is not builder with receiver
    // builder StringBuilder^ (arg::Int)? (buildStr)? -> String = []

    val withReceiver = receiver != null || check(TokenType.DoubleColon, 1)
    val name = if (withReceiver)
        receiverTypeOrName.name
    else
        (dotSeparatedIdentifiers() ?: peek(-1).compileError("Name of the builder expected")).name


    skipNewLinesAndComments()

    // args
    val args = if (check(TokenType.ReturnArrow) || check(TokenType.Assign))
        emptyList()
    else
        keywordArgs()

    args.forEach { arg ->
        if (arg.typeAST == null) builderKeyword.compileError("You forgot to declare type of arg $arg")
    }

    val returnType = returnType()
    matchAssert(TokenType.Assign)
    matchAssert(TokenType.OpenBracket, "builder cant be single expression")
    val (body, defaultAction) = statementsUntilCloseBracketWithDefaultAction(TokenType.CloseBracket)

    val x = MessageDeclarationKeyword(
        name = name,
        forType = receiver
            ?: receiverTypeOrName, // if this is builder with receiver "Type builder name from::Int = []", then use Type as receiver, not name(`receiverType`)
        returnType = returnType,
        args = args,
        body = body,
        token = builderKeyword,
        isSingleExpression = false,
        isSuspend = false,
        pragmas = pragmas
    )

    val result = StaticBuilderDeclaration(
        msgDeclaration = x,
        defaultAction = defaultAction,
        withReceiver = withReceiver,
        receiver,
        null,
        builderKeyword,

        )



    return result
}
