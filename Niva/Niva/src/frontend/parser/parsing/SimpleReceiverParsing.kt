package main.frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.parsing.*
import frontend.parser.types.ast.*
import main.WHITE

// simple means not message
fun Parser.simpleReceiver(typeAst: TypeAST? = null): Receiver {

    if (check(TokenType.OpenBracket)) {
        return codeBlock()
    }
    if (check(TokenType.OpenParen)) {
        return bracketExpression()
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
            skipOneEndOfLineOrFile()

            if (primaryTok != null && primaryTok2 != null) {
                initElementsPairs.add(Pair(primaryTok, primaryTok2))
            }

            if (primaryTok != null && primaryTok2 == null) {
                peek().compileError("Map must contain even elements")
            }

        } while (primaryTok != null)

        initElementsPairs
    }

    var tryPrimary: Receiver? = primary(typeAst)
    // check for collections
    if (tryPrimary == null) {
        val token = step()
        skipOneEndOfLineOrFile()

        tryPrimary = when (token.kind) {
            TokenType.OpenBrace -> {
                // {1, 2 3}
                // for now, messages inside collection literals are impossible

                //if there are keyword call, then read collection of constructors
                val initElements = readPrimaryCollection()
                match(TokenType.CloseBrace)

                val type = if (initElements.isNotEmpty()) initElements[0].type else null
                return ListCollection(initElements, type, token)
            }

            TokenType.OpenBraceHash -> {
                // #{"a" 1 "b" 2}
                val initElements = readPrimaryMap()
                skipOneEndOfLineOrFile()

                match(TokenType.CloseBrace)

                return MapCollection(initElements, null, token)
            }

            TokenType.OpenParenHash -> {
                // #(1, 2 3)
                val initElements = readPrimaryCollection()
                skipOneEndOfLineOrFile()

                match(TokenType.CloseParen)

                val type = if (initElements.isNotEmpty()) initElements[0].type else null
                return SetCollection(initElements, type, token)
            }


            else -> null
        }

    }

    if (tryPrimary == null) {
        peek().compileError("Can't parse primary token, got $WHITE${peek().lexeme}")
    }
    return tryPrimary
}
