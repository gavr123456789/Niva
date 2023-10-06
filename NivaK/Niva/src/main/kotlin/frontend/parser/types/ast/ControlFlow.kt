package frontend.parser.types.ast

import frontend.meta.Token
import frontend.typer.Type

sealed class IfBranch(
    val ifExpression: Expression,
) {
    class IfBranchSingleExpr(
        ifExpression: Expression,
        val thenDoExpression: Expression
    ) : IfBranch(ifExpression)

    class IfBranchWithBody(
        ifExpression: Expression,
        val body: List<Statement>
    ) : IfBranch(ifExpression)
}


enum class ControlFlowKind {
    Expression,
    Statement
}

sealed class ControlFlow(
    val ifBranches: List<IfBranch>,
    val elseBranch: List<Statement>?,
    var kind: ControlFlowKind,
    token: Token,
    type: Type?,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Expression(type, token, isPrivate, pragmas) {

    class If(
        ifBranches: List<IfBranch>,
        elseBranch: List<Statement>?,
        kind: ControlFlowKind,
        token: Token,
        type: Type?
    ) : ControlFlow(ifBranches, elseBranch, kind, token, type)

    class Switch(
        val switch: Expression,
        iF: If,
    ) : ControlFlow(iF.ifBranches, iF.elseBranch, iF.kind, iF.token, iF.type)

}
