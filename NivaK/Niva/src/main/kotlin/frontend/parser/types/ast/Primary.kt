package frontend.parser.types.ast

import frontend.meta.Token

// PRIMARY
// identifier | LiteralExpression
sealed class Primary(type: Type?, token: Token) : Receiver(type, token)

// LITERALS
sealed class LiteralExpression(type: Type?, literal: Token) : Primary(type, literal) {

    class IntExpr(literal: Token) : LiteralExpression(Type.InternalType(InternalTypes.int, false, literal), literal)
    class StringExpr(literal: Token) :
        LiteralExpression(Type.InternalType(InternalTypes.string, false, literal), literal)

    class FalseExpr(literal: Token) :
        LiteralExpression(Type.InternalType(InternalTypes.boolean, false, literal), literal)

    class TrueExpr(literal: Token) :
        LiteralExpression(Type.InternalType(InternalTypes.boolean, false, literal), literal)

    class FloatExpr(literal: Token) : LiteralExpression(Type.InternalType(InternalTypes.float, false, literal), literal)
}

class IdentifierExpr(
    name: String,
    type: Type?,
    token: Token,
//    val depth: Int,
) : Primary(type, token)

sealed class Collection(type: Type?, token: Token) : Receiver(type, token)
class ListCollection(
    val initElements: List<Primary>,
    type: Type?,
    token: Token,
) : Collection(type, token)

@JvmInline
value class LiteralExpr(val literal: Token)
