package frontend.parser.types.ast

import frontend.meta.Token


sealed class MessageDeclaration(
    val name: String,
    val forType: TypeAST,
    token: Token,
    val isSingleExpression: Boolean,
    val body: List<Statement>,
    val returnType: TypeAST?,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : Declaration(token, isPrivate, pragmas)

class MessageDeclarationUnary(
    name: String,
    forType: TypeAST,
    token: Token,
    isSingleExpression: Boolean,
    body: List<Statement>,
    returnType: TypeAST?,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas)

class MessageDeclarationBinary(
    name: String,
    forType: TypeAST,
    token: Token,
    val arg: KeywordDeclarationArg,
    body: List<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf()
) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas)


// key: localName::type
class KeywordDeclarationArg(
    val name: String,
    val localName: String? = null,
    val type: TypeAST? = null,
)

class MessageDeclarationKeyword(
    name: String,
    forType: TypeAST,
    token: Token,
    val args: List<KeywordDeclarationArg>,
    body: List<Statement>,
    returnType: TypeAST?,
    isSingleExpression: Boolean,
    isPrivate: Boolean = false,
    pragmas: List<Pragma> = listOf(),
) : MessageDeclaration(name, forType, token, isSingleExpression, body, returnType, isPrivate, pragmas)

class ConstructorDeclaration(
    val msgDeclarationKeyword: MessageDeclaration,
    forType: TypeAST,
    token: Token
) : MessageDeclaration(
    msgDeclarationKeyword.name,
    forType,
    token,
    msgDeclarationKeyword.isSingleExpression,
    msgDeclarationKeyword.body,
    msgDeclarationKeyword.returnType,
    msgDeclarationKeyword.isPrivate,
    msgDeclarationKeyword.pragmas
)
