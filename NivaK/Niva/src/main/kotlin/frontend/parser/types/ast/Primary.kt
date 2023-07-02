package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

// PRIMARY
// identifier | LiteralExpression
sealed class Primary(val typeAST: TypeAST?, token: Token) : Receiver(null, token)

// LITERALS
sealed class LiteralExpression(type: TypeAST?, literal: Token) : Primary(type, literal) {

    class IntExpr(literal: Token) : LiteralExpression(TypeAST.InternalType(InternalTypes.Int, false, literal), literal)
    class StringExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.String, false, literal), literal)

    class FalseExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Boolean, false, literal), literal)

    class TrueExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Boolean, false, literal), literal)

    class FloatExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Float, false, literal), literal)
}

class IdentifierExpr(
    val name: String,
    val names: List<String>,
    type: TypeAST?,
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
