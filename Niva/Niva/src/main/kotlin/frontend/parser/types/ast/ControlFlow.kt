package main.frontend.parser.types.ast

import frontend.parser.types.ast.Pragma
import frontend.resolver.Type
import main.frontend.meta.Token


sealed class IfBranch(
    val ifExpression: Expression,
    val otherIfExpressions: List<Expression>
) {
    class IfBranchSingleExpr(
        ifExpression: Expression,
        val thenDoExpression: Expression,
        otherIfExpressions: List<Expression>
    ) : IfBranch(ifExpression, otherIfExpressions)

    class IfBranchWithBody(
        ifExpression: Expression,
        val body: CodeBlock,//List<Statement> // replace with code block
        otherIfExpressions: List<Expression>
    ) : IfBranch(ifExpression, otherIfExpressions) {
        init {
            body.isStatement = true
        }
    }
}


enum class ControlFlowKind {
    ExpressionTypeMatch,
    StatementTypeMatch,
    Expression,
    Statement
}

sealed class ControlFlow(
    val ifBranches: List<IfBranch>,
    val elseBranch: List<Statement>?,
    var kind: ControlFlowKind,
    token: Token,
    type: Type?,
    pragmas: MutableList<Pragma> = mutableListOf(),
) : Expression(type, token, pragmas) {

    class If(
        ifBranches: List<IfBranch>,
        elseBranch: List<Statement>?,
        kind: ControlFlowKind,
        token: Token,
        type: Type?
    ) : ControlFlow(ifBranches, elseBranch, kind, token, type) {
        override fun toString(): String {
            return ifBranches.joinToString { it.ifExpression.toString() + " => ...\n" }
        }
    }

    class Switch(
        val switch: Expression,
        iF: If,
    ) : ControlFlow(iF.ifBranches, iF.elseBranch, iF.kind, iF.token, iF.type)

}
