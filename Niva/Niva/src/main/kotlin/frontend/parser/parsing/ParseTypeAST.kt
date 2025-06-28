package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.*
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.ListCollection
import main.frontend.parser.types.ast.TypeAST
import main.utils.isSimpleTypes


// lambda
fun Parser.parseLambda(tok: Token, extensionTypeName: List<String>? = null): TypeAST.Lambda {
    fun listOfInputTypes(): MutableList<TypeAST> {
        val result = mutableListOf<TypeAST>()
        // anyType, anyType, ...
        do {
            result.add(parseTypeAST())
        } while (match(TokenType.Comma))

        return result
    }


    // [int -> string]?
    // [anyType, anyType -> anyType]?
    // [ -> anyType]
    val thereIsReturnArrowAndCloseBracket = check(TokenType.ReturnArrow) || check(TokenType.CloseBracket)
    val listOfInputTypes = if (!thereIsReturnArrowAndCloseBracket) listOfInputTypes() else mutableListOf()
    match(TokenType.ReturnArrow)

    val returnType = if (!check(TokenType.CloseBracket)) parseTypeAST() else createUnitAstType(createFakeToken())
    matchAssert(TokenType.CloseBracket, "Closing paren expected in codeblock type declaration")
    val isNullable = match("?")

    val extensionNameOrNothing = extensionTypeName?.last() ?: ""

    val extensionType = if (extensionTypeName != null) {
        val x = TypeAST.UserType(
            name = extensionNameOrNothing,
            names = extensionTypeName,
            token = tok
        )
        listOfInputTypes.addFirst(x)
        x
    } else null

    return TypeAST.Lambda(
        name = extensionNameOrNothing + "[" + listOfInputTypes.joinToString(", ") { it.name } + " -> " + returnType.name + "]",
        inputTypesList = listOfInputTypes,
        extensionOfType = extensionType,
        token = tok,
        returnType = returnType,
        isNullable = isNullable,
    )
}
// use only after :: is parsed
fun Parser.parseTypeAST(isExtendDeclaration: Boolean = false): TypeAST {
    // {int} - list of int
    // #{int: string} - map
    // Person - identifier
    // List::Map::(int, string)
    // Person from: x::List::Map::(int, string)
    // x::int?
    // x::Int!ErrorType


    // literal collections type set or map
//    if (match(TokenType.OpenBraceHash)) {
//        TODO()
//    }
    // list
//    if (match(TokenType.OpenBrace)) {
//        TODO()
//    }


    val mutableType = match(TokenType.Mut)
    val tok = peek()

    if (match(TokenType.OpenBracket)) {
        return parseLambda(tok)
    }


    // check for basic type
    when (tok.kind) {
        TokenType.True, TokenType.False -> return TypeAST.InternalType(InternalTypes.Bool, tok).also { it.isMutable = mutableType }
        TokenType.Null -> return TypeAST.InternalType(InternalTypes.Null, tok).also { it.isMutable = mutableType }

        TokenType.Float -> return TypeAST.InternalType(InternalTypes.Float, tok).also { it.isMutable = mutableType }
        TokenType.Double -> return TypeAST.InternalType(InternalTypes.Double, tok).also { it.isMutable = mutableType }
        TokenType.Integer -> return TypeAST.InternalType(InternalTypes.Int, tok).also { it.isMutable = mutableType }
        TokenType.String -> return TypeAST.InternalType(InternalTypes.String, tok).also { it.isMutable = mutableType }
        TokenType.Char -> return TypeAST.InternalType(InternalTypes.Char, tok).also { it.isMutable = mutableType }
        else -> {}
    }
    val checkForErrors = {
        val bang = match("!")
        val errors = if (bang) {
            val erTok = peek()
            when (erTok.kind) {
                TokenType.OpenBrace -> {
                    val errorsList = (simpleReceiver() as ListCollection).initElements.map { it.token.lexeme }
                    if (errorsList.isEmpty()) null else errorsList
                }

                TokenType.Identifier -> {
                    listOf(step().lexeme)
                }

                else -> {
                    listOf<String>()
                }
            }
            // Int!{Error1, Error2} // multiple errors
            // Int!Error // single error
            // Int! // errors in empty list
        } else null
        errors
    }

    fun parseGenericType(): TypeAST {
        // identifier ("(" | "::")

        // x::List::Map(int, string)
        val isMutableGenericParam = match(TokenType.Mut)

        val identifier = matchAssertAnyIdent("in type declaration identifier expected, but found ${peek().lexeme}")
        val isIdentifierNullable = identifier.kind == TokenType.NullableIdentifier
        val simpleTypeMaybe = identifier.lexeme.isSimpleTypes()
        // if there is simple type, there cant be any other types like int:: is impossible
        return if (simpleTypeMaybe != null) {
            // int string float or bool
            TypeAST.InternalType(simpleTypeMaybe, identifier, isIdentifierNullable).also {
                it.isMutable = isMutableGenericParam
            }
        } else {
            if (match(TokenType.DoubleColon)) {
//                    need recursion
                val recursiveGenerics = parseGenericType()
                val errors = checkForErrors()
                return TypeAST.UserType(identifier.lexeme, mutableSetOf(recursiveGenerics), isIdentifierNullable, identifier, errors = errors).also {
                    it.isMutable = isMutableGenericParam
                }
            }
            // Map(Int, String)
            if (match(TokenType.OpenParen)) {
                val typeArgumentList: MutableSet<TypeAST> = mutableSetOf()
                do {
                    typeArgumentList.add(parseGenericType())
                } while (match(TokenType.Comma))
                matchAssert(TokenType.CloseParen, "closing paren in generic type expected")

                return TypeAST.UserType(identifier.lexeme, typeArgumentList, isIdentifierNullable, identifier)
            }
            // ::Person
            TypeAST.UserType(identifier.lexeme, mutableSetOf(), isIdentifierNullable, identifier).also {
                it.isMutable = isMutableGenericParam
            }
        }

    }
    val isIdentifier = tok.isIdentifier()
    // tok already eaten so check on distance 0
    if (isIdentifier && (check(TokenType.DoubleColon, 1)) || check(TokenType.OpenParen, 1)) {
        // generic
        // x::List::Map::(int, string)
        val result = parseGenericType().also { it.isMutable = mutableType }
        return result
    } else if (isIdentifier) {
        step() // skip tok ident
        // can be dot separated

        val path = mutableListOf(tok.lexeme)

        if (!isExtendDeclaration && match(TokenType.OpenBracket)) {
            return parseLambda(tok, path).also { it.isMutable = mutableType }
        }
        while (match(TokenType.Dot)) {
            path.add(matchAssert(TokenType.Identifier, "Identifier after dot expected").lexeme)
        }


        val errors = checkForErrors()
        // one identifier
        return TypeAST.UserType(
            name = path.last(),
            names = path,
            typeArgumentList = mutableSetOf(),
            isNullable = tok.isNullable(),
            token = tok,
            errors = errors
        ).also { it.isMutable = mutableType }
    }

    tok.compileError("Syntax error: type declaration expected")
}

fun createUnitAstType(token: Token): TypeAST.InternalType = TypeAST.InternalType(
    name = InternalTypes.Unit,
    isNullable = false,
    token = token,
)
