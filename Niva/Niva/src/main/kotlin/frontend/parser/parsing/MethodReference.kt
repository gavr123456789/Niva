package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.MethodReference


// &Pkg.String unary
// &Pkg.String +
// &Pkg.String from:to:
fun Parser.methodReference(): MethodReference {
    val tok = peek(-1)
    val forType = parseType()

    // binary
    if (check(TokenType.BinarySymbol)) {
        val w = matchAssert(TokenType.BinarySymbol)

        return MethodReference.Binary(
            forType = forType,
            name = w.lexeme,
            token = tok,
        )
    }

    //kw
    if (checkMany(TokenType.Identifier, TokenType.Colon)) {
        val list = mutableListOf<String>()
        while (check(TokenType.Identifier)) {

            list.add(matchAssert(TokenType.Identifier).lexeme)
            match(TokenType.Colon)
        }
        val name = list.joinToString(":") + ":"
        return MethodReference.Keyword(
            keys = list,
            forType = forType,
            name = name,
            token = tok,
        )
    }

    // unary
    if (check(TokenType.Identifier)) {
        val w = matchAssert(TokenType.Identifier)

        return MethodReference.Unary(
            forType = forType,
            name = w.lexeme,
            token = tok,
        )
    }

    tok.compileError("Syntax error: can't recognize method reference type(examples &String +, &String toInt, &String from:to:)")
}
