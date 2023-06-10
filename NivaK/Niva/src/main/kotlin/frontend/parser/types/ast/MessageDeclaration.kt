package frontend.parser.types.ast

import frontend.meta.Token


sealed class MessageDeclaration(
    val name: String,
    token: Token,
    val isSingleExpression: Boolean,
    val body: List<Statement>,
    val returnType: Type?,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Statement(token, isPrivate, pragmas)

class MessageDeclarationUnary(
    name: String,
    token: Token,
    isSingleExpression: Boolean,
    body: List<Statement>,
    returnType: Type?,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, token, isSingleExpression, body,returnType, isPrivate, pragmas)

class MessageDeclarationBinary(
    name: String,
    token: Token,
    val arg: KeywordDeclarationArg,
    body: List<Statement>,
    returnType: Type?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, token, isSingleExpression, body, returnType,isPrivate, pragmas)


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val type: Type? = null,
)

class MessageDeclarationKeyword(
    name: String,
    token: Token,
    val args: List<KeywordDeclarationArg>,
    body: List<Statement>,
    returnType: Type?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
) : MessageDeclaration(name, token, isSingleExpression, body, returnType, isPrivate, pragmas)

class ConstructorDeclaration(
    val msgDeclarationKeyword: MessageDeclaration,
    token: Token
) : MessageDeclaration(
    msgDeclarationKeyword.name,
    token,
    msgDeclarationKeyword.isSingleExpression,
    msgDeclarationKeyword.body,
    msgDeclarationKeyword.returnType,
    msgDeclarationKeyword.isPrivate,
    msgDeclarationKeyword.pragmas
)
