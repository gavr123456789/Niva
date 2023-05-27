package frontend.parser.types

import frontend.meta.Token


sealed class MessageDeclaration(
    val name: String,
    token: Token,
    val isSingleExpression: Boolean,
    val body: List<Declaration>,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(token, isPrivate, pragmas)

class MessageDeclarationUnary(
    name: String,
    token: Token,
    isSingleExpression: Boolean,
    body: List<Declaration>,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, token, isSingleExpression, body, isPrivate, pragmas)

class MessageDeclarationBinary(
    name: String,
    token: Token,
    body: List<Declaration>,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, token, isSingleExpression, body, isPrivate, pragmas)


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val type: String? = null,
)

class MessageDeclarationKeyword(
    name: String,
    token: Token,
    val args: List<KeywordDeclarationArg>,
    body: List<Declaration>,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
) : MessageDeclaration(name, token, isSingleExpression, body, isPrivate, pragmas)
