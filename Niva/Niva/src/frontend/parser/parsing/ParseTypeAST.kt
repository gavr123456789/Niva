package main.frontend.parser.parsing

import frontend.meta.*
import frontend.parser.parsing.*
import frontend.parser.types.ast.InternalTypes
import frontend.parser.types.ast.TypeAST
import frontend.util.createFakeToken
import frontend.util.isSimpleTypes

// use only after ::
fun Parser.parseType(extensionTypeOfLambda: String? = null): TypeAST {
    // {int} - list of int
    // #{int: string} - map
    // Person - identifier
    // List::Map::(int, string)
    // Person from: x::List::Map::(int, string)
    // x::int?

    val tok = peek()

    // literal collections type set or map
    if (match(TokenType.OpenBraceHash)) {
        TODO()
    }
    // list
    if (match(TokenType.OpenBrace)) {
        TODO()
    }

    // lambda
    if (match(TokenType.OpenBracket)) {

        fun listOfInputTypes(): List<TypeAST> {
            val result = mutableListOf<TypeAST>()
            // anyType, anyType, ...
            do {
                result.add(parseType())
            } while (match(TokenType.Comma))

            return result
        }


        // [int -> string]?
        // [anyType, anyType -> anyType]?
        // [ -> anyType]
        val thereIsReturnArrowAndCloseBracket = check(TokenType.ReturnArrow) || check(TokenType.CloseBracket)
        val listOfInputTypes = if (!thereIsReturnArrowAndCloseBracket) listOfInputTypes() else listOf()
//        matchAssert(TokenType.ReturnArrow, "-> expected after list of input types in lambda type declaration")
        match(TokenType.ReturnArrow)

        val returnType = if (!check(TokenType.CloseBracket)) parseType() else createUnitAstType(createFakeToken())
        matchAssert(TokenType.CloseBracket, "Closing paren expected in codeblock type declaration")
        val isNullable = match("?")


        return TypeAST.Lambda(
            name = ("$extensionTypeOfLambda.") + "[" + listOfInputTypes.joinToString(", ") { it.name } + " -> " + returnType.name + "]",
            inputTypesList = listOfInputTypes,
            extensionOfType = extensionTypeOfLambda,
            token = tok,
            returnType = returnType,
            isNullable = isNullable,
        )
    }


    // check for basic type
    when (tok.kind) {
        TokenType.True, TokenType.False -> return TypeAST.InternalType(InternalTypes.Boolean, tok)
        TokenType.Null -> return TypeAST.InternalType(InternalTypes.Null, tok)

        TokenType.Float -> return TypeAST.InternalType(InternalTypes.Float, tok)
        TokenType.Double -> return TypeAST.InternalType(InternalTypes.Double, tok)
        TokenType.Integer -> return TypeAST.InternalType(InternalTypes.Int, tok)
        TokenType.String -> return TypeAST.InternalType(InternalTypes.String, tok)
        TokenType.Char -> return TypeAST.InternalType(InternalTypes.Char, tok)
        else -> {}
    }

    fun parseGenericType(): TypeAST {
        // identifier ("(" | "::")

        // x::List::Map(int, string)
        val identifier = matchAssertAnyIdent("in type declaration identifier expected, but found ${peek().lexeme}")
        val isIdentifierNullable = identifier.kind == TokenType.NullableIdentifier
        val simpleTypeMaybe = identifier.lexeme.isSimpleTypes()
        // if there is simple type, there cant be any other types like int:: is impossible
        return if (simpleTypeMaybe != null) {
            // int string float or bool
            TypeAST.InternalType(simpleTypeMaybe, identifier, isIdentifierNullable)
        } else {
            if (match(TokenType.DoubleColon)) {
//                    need recursion
                return TypeAST.UserType(identifier.lexeme, listOf(parseGenericType()), isIdentifierNullable, identifier)
            }
            // Map(Int, String)
            if (match(TokenType.OpenParen)) {
                val typeArgumentList: MutableList<TypeAST> = mutableListOf()
                do {
                    typeArgumentList.add(parseGenericType())
                } while (match(TokenType.Comma))
                matchAssert(TokenType.CloseParen, "closing paren in generic type expected")

                return TypeAST.UserType(identifier.lexeme, typeArgumentList, isIdentifierNullable, identifier)
            }
            // ::Person
            TypeAST.UserType(identifier.lexeme, listOf(), isIdentifierNullable, identifier)
        }

    }

    // tok already eaten so check on distance 0
    if (tok.isIdentifier() && (check(TokenType.DoubleColon, 1)) || check(TokenType.OpenParen, 1)) {
        // generic
        // x::List::Map::(int, string)
        return parseGenericType()
    } else if (tok.isIdentifier()) {
        step() // skip tok ident
        // can be dot separated

        val path = mutableListOf(tok.lexeme)
        while (match(TokenType.Dot)) {
            path.add(matchAssert(TokenType.Identifier, "Identifier after dot expected").lexeme)
        }

        // one identifier
        return TypeAST.UserType(
            name = path.last(),
            names = path,
            typeArgumentList = listOf(),
            isNullable = tok.isNullable(),
            token = tok
        )
    }

    tok.compileError("Syntax error: type declaration expected")
}

fun createUnitAstType(token: Token): TypeAST.InternalType = TypeAST.InternalType(
    name = InternalTypes.Unit,
    isNullable = false,
    token = token,
)
