package main.frontend.parser.types.ast

import frontend.resolver.Type
import main.frontend.meta.Token

// PRIMARY
// identifier | LiteralExpression
sealed class Primary(val typeAST: TypeAST?, token: Token) : Receiver(null, token)

// LITERALS
sealed class LiteralExpression(typeAST: TypeAST?, literal: Token) : Primary(typeAST, literal) {

    class IntExpr(literal: Token) : LiteralExpression(TypeAST.InternalType(InternalTypes.Int, literal), literal)

    class StringExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.String, literal), literal) {
        override fun toString(): String {
            val lex = token.lexeme
            val removeN = if (lex.startsWith("\"\"\"")) 3 else 1

            return this.token.lexeme.slice(removeN until token.lexeme.count() - removeN)
//            return this.token.lexeme//.slice(removeN until token.lexeme.count() - removeN)
        }
    }

    class CharExpr(literal: Token) :
        LiteralExpression(TypeAST.InternalType(InternalTypes.Char, literal), literal) {
        override fun toString(): String {
            return this.token.lexeme.slice(1 until token.lexeme.count() - 1)
//            return "\"" + this.token.lexeme + "\""//.slice(1 until token.lexeme.count() - 1)
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
//    fun toTypeAST(): TypeAST.UserType =
//        TypeAST.UserType(name = this.name, names = this.names, token = this.token)
}

sealed class CollectionAst(val initElements: List<Receiver>, type: Type?, token: Token, val isMutable: Boolean) : Receiver(type, token)
class ListCollection(
    initElements: List<Receiver>,
    type: Type?,
    token: Token,
    isMutable: Boolean
) : CollectionAst(initElements, type, token, isMutable) {
    override fun toString(): String {
        return "[${initElements.joinToString(", ")}]"
    }
}

class SetCollection(
    initElements: List<Receiver>,
    type: Type?,
    token: Token,
    isMutable: Boolean
) : CollectionAst(initElements, type, token, isMutable) {
    override fun toString(): String {
        return "#(${initElements.joinToString(", ")})"
    }
}

class MapCollection(
    val initElements: List<Pair<Receiver, Receiver>>,
    type: Type?,
    token: Token,
    val isMutable: Boolean
) : Receiver(type, token) {
    override fun toString(): String {
        return "#{${initElements.joinToString(", ") {"${it.first}: ${it.second}"}}}"
    }
}
