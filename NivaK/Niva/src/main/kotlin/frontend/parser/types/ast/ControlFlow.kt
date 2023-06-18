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

sealed class ControlFlow(
    val ifBranches: List<IfBranch>,
    val elseBranch: List<Statement>?,
    token: Token,
    type: Type?,
    pragmas: List<Pragma> = listOf(),
    isPrivate: Boolean = false,
) : Expression(type, token, isPrivate, pragmas) {

    sealed class If(
        ifBranches: List<IfBranch>,
        elseBranch: List<Statement>?,
        token: Token,
        type: Type?
    ) : ControlFlow(ifBranches, elseBranch, token, type)

    class IfExpression(
        type: Type?,
        branches: List<IfBranch>,
        elseBranch: List<Statement>?,
        token: Token
    ) : If(branches, elseBranch, token, type)

    class IfStatement(
        type: Type?,
        branches: List<IfBranch>,
        elseBranch: List<Statement>?,
        token: Token
    ) : If(branches, elseBranch, token, type)

    sealed class Switch(
        val switch: Expression,
        iF: If
    ) : ControlFlow(iF.ifBranches, iF.elseBranch, iF.token, iF.type)

    class SwitchStatement(
        switch: Expression,
        iF: If
    ) : Switch(switch, iF)

    class SwitchExpression(
        switch: Expression,
        iF: If
    ) : Switch(switch, iF)


}
