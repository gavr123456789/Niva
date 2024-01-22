package frontend.parser.types.ast

import frontend.meta.Token
import frontend.resolver.Type

// PRIMARY
// identifier | LiteralExpression
sealed class Primary(val typeAST: TypeAST?, token: Token) : Receiver(null, token)

// LITERALS
sealed class LiteralExpression(typeAST: TypeAST?, literal: Token) : Primary(typeAST, literal) {

    class IntExpr(literal: Token) : LiteralExpression(TypeAST.InternalType(InternalTypes.Int, literal), literal)

    class StringExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.String, literal), literal) {
        override fun toString(): String {
            return this.token.lexeme.slice(1 until token.lexeme.count() - 1)
        }
    }

    class CharExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Char, literal), literal) {
        override fun toString(): String {
            return this.token.lexeme.slice(1 until token.lexeme.count() - 1)
        }
    }

    class FalseExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Boolean, literal), literal)

    class TrueExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Boolean, literal), literal)

    class NullExpr(typeAST: TypeAST, literal: Token) :
        LiteralExpression(typeAST, literal)

    class FloatExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Float, literal), literal)

    class DoubleExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Double, literal), literal)

}

class IdentifierExpr(
    val name: String,
    val names: List<String> = listOf(name),
    type: TypeAST? = null,
    token: Token,
    var isType: Boolean = false
) : Primary(type, token) {
    override fun toString(): String {
        return names.joinToString(".")
    }
}

sealed class Collection(val initElements: List<Receiver>, type: Type?, token: Token) : Receiver(type, token)
class ListCollection(
    initElements: List<Receiver>,
    type: Type?,
    token: Token,
) : Collection(initElements, type, token)

class SetCollection(
    initElements: List<Receiver>,
    type: Type?,
    token: Token,
) : Collection(initElements, type, token)

class MapCollection(
    val initElements: List<Pair<Receiver, Receiver>>,
    type: Type?,
    token: Token,
) : Receiver(type, token)
