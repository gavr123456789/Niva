package main.frontend.parser.parsing


import frontend.parser.parsing.*
import main.frontend.meta.Token
import main.utils.WHITE
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.meta.parsingError
import main.frontend.parser.types.ast.*
import main.utils.RESET

// simple means not message
fun Parser.simpleReceiver(typeAst: TypeAST? = null): Receiver {

    if (check(TokenType.OpenBracket)) {
        return codeBlock()
    }
    if (check(TokenType.OpenParen)) {
        val bracketsExpr = bracketExpression()
        return bracketsExpr
    }

//    val readPrimaryCollection = readPrimaryCollection(typeAst)

//    val readPrimaryMap = readPrimaryMap(typeAst)

    // builder
    if (checkMany(TokenType.Identifier, TokenType.OpenBracket))
        return staticBuilder()
    if (checkMany(TokenType.Identifier, TokenType.OpenParen))
        return staticBuilderWithArgs()



    var tryPrimary: Receiver? = primary(typeAst)
    // check for collections
    if (tryPrimary == null) {
        val token = step() // open
        skipOneEndOfLineOrComment()

        tryPrimary = when (token.kind) {
            TokenType.OpenBrace -> {
                return parseListCollection(typeAst, token)
            }

            TokenType.OpenBraceHash -> {
                // #{"a" 1 "b" 2}
                return parseMapCollection(typeAst, token)
            }

            TokenType.OpenParenHash -> {
                // #(1, 2 3)
                return parseSetCollection(typeAst, token)
            }


            else -> null
        }

    }

    if (tryPrimary == null) {
        step(-2)
        this.parsingError("Primary was expected but received '$WHITE${peek(0).lexeme} ${peek(1).lexeme}$RESET'")
    }
    return tryPrimary
}

private fun Parser.parseSetCollection(
    typeAst: TypeAST?,
    token: Token
): SetCollection {
    val initElements = readPrimaryCollection(typeAst)
    skipNewLinesAndComments()
    match(TokenType.CloseParen)

    val type = if (initElements.isNotEmpty()) initElements[0].type else null
    return SetCollection(initElements, type, token, isMutable = match("!"))
}

fun Parser.parseMapCollection(
    typeAst: TypeAST?,
    token: Token
): MapCollection {
    val initElements = readPrimaryMap(typeAst)
    skipNewLinesAndComments()
    match(TokenType.CloseBrace)

    return MapCollection(initElements, null, token, isMutable = match("!"))
}

fun Parser.parseListCollection(
    typeAst: TypeAST?,
    token: Token
): ListCollection {
    // {1, 2 3}
    // for now, messages inside collection literals are impossible

    //if there are keyword call, then read collection of constructors
    val initElements = readPrimaryCollection(typeAst)
    skipNewLinesAndComments()
    match(TokenType.CloseBrace)

    val type = if (initElements.isNotEmpty()) initElements[0].type else null
    val result = ListCollection(initElements, type, token, isMutable = match("!"))
    this.lastListCollection = result
    return result
}

private fun Parser.readPrimaryMap(typeAst: TypeAST?): MutableList<Pair<Receiver, Receiver>> {
    val initElementsPairs: MutableList<Pair<Receiver, Receiver>> = mutableListOf()
    do {
//            val primaryTok = primary(typeAst)
        val isExprInBrackets = check(TokenType.OpenParen)
        val primaryTok = if (isExprInBrackets) {
            bracketExpression()
        } else primary(typeAst)

        if (match(TokenType.Comma)) {
            peek().compileError("Only map pairs can be separated by commas")
        }

        val isExprInBrackets2 = check(TokenType.OpenParen)
        val primaryTok2 = if (isExprInBrackets2) {
            bracketExpression()
        } else primary(typeAst)

        match(TokenType.Comma)
        skipNewLinesAndComments()

        if (primaryTok != null && primaryTok2 != null) {
            initElementsPairs.add(Pair(primaryTok, primaryTok2))
        }

        if (primaryTok != null && primaryTok2 == null) {
            peek().compileError("Map must contain even elements")
        }

    } while (primaryTok != null)

   return initElementsPairs
}

private fun Parser.readPrimaryCollection(typeAst: TypeAST?): MutableList<Receiver> {

    val initElements = mutableListOf<Receiver>()

    var lastCollectionElem: Receiver? = null
    do {
        // can be expression in brackets in
        val isExprInBrackets = check(TokenType.OpenParen)
        val primaryTok = if (isExprInBrackets) {
            bracketExpression()
        } else primary(typeAst)

        match(TokenType.Comma)
        skipNewLinesAndComments()

        if (primaryTok != null) {
            if (lastCollectionElem != null && primaryTok.type?.name != lastCollectionElem.type?.name) {
                error("Heterogeneous collections are not supported")
            }
            initElements.add(primaryTok)
        }
        lastCollectionElem = primaryTok
    } while (primaryTok != null)

    match(TokenType.Comma)

    return initElements

}
