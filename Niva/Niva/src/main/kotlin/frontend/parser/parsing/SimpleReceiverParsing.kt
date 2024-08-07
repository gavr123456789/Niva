package main.frontend.parser.parsing


import frontend.parser.parsing.*
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

    val readPrimaryCollection = {
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

        initElements
    }

    val readPrimaryMap = {
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
            skipOneEndOfLineOrComment()

            if (primaryTok != null && primaryTok2 != null) {
                initElementsPairs.add(Pair(primaryTok, primaryTok2))
            }

            if (primaryTok != null && primaryTok2 == null) {
                peek().compileError("Map must contain even elements")
            }

        } while (primaryTok != null)

        initElementsPairs
    }

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
                // {1, 2 3}
                // for now, messages inside collection literals are impossible

                //if there are keyword call, then read collection of constructors
                val initElements = readPrimaryCollection()
                skipNewLinesAndComments()
                match(TokenType.CloseBrace)

                val type = if (initElements.isNotEmpty()) initElements[0].type else null

                return ListCollection(initElements, type, token)
            }

            TokenType.OpenBraceHash -> {
                // #{"a" 1 "b" 2}
                val initElements = readPrimaryMap()
                skipNewLinesAndComments()
                match(TokenType.CloseBrace)

                return MapCollection(initElements, null, token)
            }

            TokenType.OpenParenHash -> {
                // #(1, 2 3)
                val initElements = readPrimaryCollection()
                skipNewLinesAndComments()
                match(TokenType.CloseParen)

                val type = if (initElements.isNotEmpty()) initElements[0].type else null
                return SetCollection(initElements, type, token)
            }


            else -> null
        }

    }

    if (tryPrimary == null) {
        step(-2)
        this.parsingError("Primary was expected but received '$WHITE${peek(0).lexeme} ${peek(1).lexeme}$RESET'")
//        peek().compileError("Can't parse primary token, got $WHITE${peek().lexeme}")
    }
    return tryPrimary
}
