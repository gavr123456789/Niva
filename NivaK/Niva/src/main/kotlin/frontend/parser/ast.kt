package frontend.parser

import frontend.meta.Token


sealed class ASTNode(
    val file: String,
    val token: Token
)

data class Declaration(
    val isPrivate: Boolean,
    val pragmas: List<Pragma>
//    val generics:
)

data class Pragma(
    val name: IdentExpr,
    val args: List<LiteralExpr>
)

data class IdentExpr(
    val name: Token,
    val depth: Int
)

@JvmInline
value class LiteralExpr(val literal: Token)
