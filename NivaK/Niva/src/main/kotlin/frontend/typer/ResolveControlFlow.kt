package frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Resolver.resolveControlFlow(
    statement: ControlFlow,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {
    if (statement.ifBranches.isEmpty()) {
        statement.token.compileError("If must contain at least one branch")
    }

    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

    val detectExprOrStatement = {
        when (rootStatement) {
            null -> {
                ControlFlowKind.Statement
            }

            // we inside argument probably
            is MessageSend, is ExpressionInBrackets -> ControlFlowKind.Expression
            is ControlFlow -> {
                rootStatement.kind
            }

            else -> ControlFlowKind.Statement
        }

    }

    when (statement) {
        is ControlFlow.If -> {
            var firstBranchReturnType: Type? = null

            statement.kind = detectExprOrStatement()


            statement.ifBranches.forEachIndexed { i, it ->
                /// resolving if
                resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                /// resolving then
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        resolve(listOf(it.thenDoExpression), previousAndCurrentScope, statement)
                    }

                    is IfBranch.IfBranchWithBody -> {
                        if (it.body.isNotEmpty()) {
                            currentLevel++
                            resolve(it.body, previousAndCurrentScope, statement)
                            currentLevel--
                            if (statement.kind == ControlFlowKind.Expression) {
                                val lastExpr = it.body.last()
                                if (lastExpr !is Expression) {
                                    lastExpr.token.compileError("In switch expression body last statement must be an expression")
                                }
                            }
                        }
                    }
                }

                // compare the current branch type with the last one
                if (i > 0) {
                    val prev = statement.ifBranches[i - 1]

                    val prevType: Type = prev.getReturnTypeOrThrow()
                    val currType = it.getReturnTypeOrThrow()
                    if (prevType.name != currType.name) {
                        it.ifExpression.token.compileError(
                            "In if Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} " +
                                    "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                        )
                    }
                } else {
                    firstBranchReturnType = it.getReturnTypeOrThrow()
                }
            }


            if (statement.kind == ControlFlowKind.Expression && statement.elseBranch == null) {
                statement.token.compileError("If expression must contain else branch")
            }

            statement.type = if (statement.elseBranch != null) {
                resolve(statement.elseBranch, previousAndCurrentScope, statement)
                if (statement.kind == ControlFlowKind.Expression) {
                    val lastExpr = statement.elseBranch.last()
                    if (lastExpr !is Expression) {
                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
                    }
                    val elseReturnType = lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)")
                    }
                    elseReturnType
                } else {
                    Resolver.defaultTypes[InternalTypes.Unit]!!
                }

            } else {
                // if no else branch, its not an expression already
                Resolver.defaultTypes[InternalTypes.Unit]!!
            }


        }


        is ControlFlow.Switch -> {
            if (statement.switch.type == null) {
                resolve(listOf(statement.switch), previousAndCurrentScope, statement)
            }

            // TODO check if this expression of statement
            var firstBranchReturnType: Type? = null

            statement.ifBranches.forEachIndexed { i, it ->
                /// resolving if
                resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                val currentType = it.ifExpression.type?.name
                if (currentType != statement.switch.type!!.name) {
                    val curTok = it.ifExpression.token
                    curTok.compileError("If branch ${curTok.lexeme} on line: ${curTok.line} is not of switching Expr type: ${statement.switch.type!!.name}")
                }
                /// resolving then
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        resolve(listOf(it.thenDoExpression), previousAndCurrentScope, statement)
                    }

                    is IfBranch.IfBranchWithBody -> {
                        if (it.body.isNotEmpty()) {
                            currentLevel++
                            resolve(it.body, previousAndCurrentScope, statement)
                            currentLevel--

                            val lastExpr = it.body.last()
                            if (lastExpr !is Expression) {
                                lastExpr.token.compileError("In switch expression body last statement must be an expression")
                            }
                        }
                    }
                }

                // compare the current branch type with the last one
                if (i > 0) {
                    val prev = statement.ifBranches[i - 1]

                    val prevType: Type = prev.getReturnTypeOrThrow()
                    val currType = it.getReturnTypeOrThrow()
                    if (prevType.name != currType.name) {
                        it.ifExpression.token.compileError(
                            "In switch Expression return type of branch on line: ${prev.ifExpression.token.line} is ${prevType.name} "
                                    + "but return type of branch on line ${it.ifExpression.token.line} is ${currType.name}, all branches must return the same type"
                        )
                    }
                } else {
                    firstBranchReturnType = it.getReturnTypeOrThrow()
                }
            }


            if (statement.elseBranch == null) {
                statement.token.compileError("If expression must contain else branch")
            }


            resolve(statement.elseBranch, previousAndCurrentScope, statement)
            val lastExpr = statement.elseBranch.last()
            if (lastExpr !is Expression) {
                lastExpr.token.compileError("In switch expression body last statement must be an expression")
            }
            val elseReturnType = lastExpr.type!!
            val elseReturnTypeName = elseReturnType.name
            val firstReturnTypeName = firstBranchReturnType!!.name
            if (elseReturnTypeName != firstReturnTypeName) {
                lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($firstReturnTypeName != $elseReturnTypeName)")

            }

            statement.type = elseReturnType
        }

    }


    if (currentLevel == 0) topLevelStatements.add(statement)
}