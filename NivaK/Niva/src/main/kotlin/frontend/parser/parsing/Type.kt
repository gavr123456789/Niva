package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.isIdentifier
import frontend.meta.isNullable
import frontend.parser.types.ast.InternalTypes
import frontend.parser.types.ast.Type
import frontend.util.isSimpleTypes

// use only after ::
fun Parser.parseType(): Type {
    // {int} - list of int
    // #{int: string} - map
    // Person - identifier
    // List<Map<int, string>> - generic
    // List(Map(int, string))
    // List::Map::(int, string)
    // Person from: x::List::Map::(int, string)
    // x::int?

    val tok = peek()

    // set or map
    if (match(TokenType.OpenBraceHash)) {
        TODO()
    }
    // list
    if (match(TokenType.OpenBrace)) {
        TODO()
    }

    // lambda
    if (match(TokenType.OpenBracket)) {

        fun listOfInputTypes(): List<Type> {
            val result = mutableListOf<Type>()
            // anyType, anyType, ...
            do {
                result.add(parseType())
            } while (match(TokenType.Comma))

            return result
        }


        // [int -> string]?
        // [anyType, anyType -> anyType]?
        val listOfInputTypes = listOfInputTypes()
        matchAssert(TokenType.ReturnArrow, "-> expected after list of input types in lambda type declaration")

        val returnType = parseType()
        matchAssert(TokenType.CloseBracket, "closing paren expected in lambda type declaration")
        val isNullable = match("?")


        return Type.Lambda(
            name = "",
            inputTypesList = listOfInputTypes,
            returnType,
            isNullable,
            token = tok,
        )
    }


    // check for basic type
    when (tok.kind) {
        TokenType.True, TokenType.False -> return Type.InternalType(InternalTypes.boolean, false, tok)
        TokenType.Float -> return Type.InternalType(InternalTypes.float, false, tok)
        TokenType.Integer -> return Type.InternalType(InternalTypes.int, false, tok)
        TokenType.String -> return Type.InternalType(InternalTypes.string, false, tok)
        else -> {}
    }

    fun parseGenericType(): Type {

        // identifier ("(" | "::")

        // x::List::Map(int, string)
        val identifier = matchAssertAnyIdent("in type declaration identifier expected")
        val isIdentifierNullable = identifier.kind == TokenType.NullableIdentifier
        val simpleTypeMaybe = identifier.lexeme.isSimpleTypes()
        // if there is simple type, there cant be any other types like int:: is impossible
        return if (simpleTypeMaybe != null) {
            // int string float or bool
            Type.InternalType(simpleTypeMaybe, isIdentifierNullable, identifier)
        } else {
            if (match(TokenType.DoubleColon)) {
//                    need recursion
                return Type.UserType(identifier.lexeme, listOf(parseGenericType()), isIdentifierNullable, identifier)
            }
            // Map(Int, String)
            if (match(TokenType.OpenParen)) {
                val typeArgumentList: MutableList<Type> = mutableListOf()
                do {
                    typeArgumentList.add(parseGenericType())
                } while (match(TokenType.Comma))
                matchAssert(TokenType.CloseParen, "closing paren in generic type expected")

                return Type.UserType(identifier.lexeme, typeArgumentList, isIdentifierNullable, identifier)

            }
            // ::Person

            Type.UserType(identifier.lexeme, listOf(), isIdentifierNullable, identifier)
        }

    }

    // tok already eaten so check on distance 0
    if (tok.isIdentifier() && (check(TokenType.DoubleColon, 1)) || check(TokenType.OpenParen, 1)) {
        // generic
        // x::List::Map::(int, string)
        return parseGenericType()
    } else if (tok.isIdentifier()) {
        step() // skip ident
        // one identifier
        return Type.UserType(
            name = tok.lexeme,
            typeArgumentList = listOf(),
            isNullable = tok.isNullable(),
            token = tok
        )
    }


    error("type declaration expected")
}
