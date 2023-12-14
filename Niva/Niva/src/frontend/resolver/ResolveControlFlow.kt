package frontend.resolver

import frontend.meta.compileError
import frontend.parser.types.ast.*
import main.RED
import main.WHITE
import main.YEL


fun Statement.isNotExpression(): Boolean =
    this !is Expression && this !is ReturnStatement && this !is Assign


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
            is MessageSend, is ExpressionInBrackets, is ReturnStatement, is VarDeclaration -> ControlFlowKind.Expression
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

            val isStatement =
                statement.kind == ControlFlowKind.StatementTypeMatch || statement.kind == ControlFlowKind.Statement

            statement.ifBranches.forEachIndexed { i, it ->
                /// resolving if
                currentLevel++
                resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                currentLevel--

                if (isStatement) {
                    val ifType = it.ifExpression.type!!
                    if (ifType != Resolver.defaultTypes[InternalTypes.Boolean]) {
                        it.ifExpression.token.compileError("if branch ${WHITE}${it.ifExpression} must be of the `${YEL}Boolean$RED` type, but found ${YEL}`$ifType`")

                    }
                }

                /// resolving then
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        currentLevel++
                        resolve(listOf(it.thenDoExpression), previousAndCurrentScope, statement)
                        currentLevel--
                    }

                    is IfBranch.IfBranchWithBody -> {
                        if (it.body.isNotEmpty()) {
                            currentLevel++
                            resolve(it.body, previousAndCurrentScope, statement)
                            currentLevel--
                            if (statement.kind == ControlFlowKind.Expression) {
                                val lastExpr = it.body.last()
                                if (lastExpr.isNotExpression()) {
                                    lastExpr.token.compileError("In if expression body last statement must be an expression")
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
                            "In if Expression return type of branch on line: ${WHITE}${prev.ifExpression.token.line}${RED} is ${WHITE}${prevType.name}${RED} " +
                                    "\n\tBut return type of branch on line ${WHITE}${it.ifExpression.token.line}${RED} is ${WHITE}${currType.name}${RED}, all branches must return the same type"
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
                currentLevel++
                resolve(statement.elseBranch, previousAndCurrentScope, statement)
                currentLevel--
                if (statement.kind == ControlFlowKind.Expression) {
                    val lastExpr = statement.elseBranch.last()
//                    if (lastExpr.notExpression()) {
//                        lastExpr.token.compileError("In switch expression body last statement must be an expression")
//                    }
                    val elseReturnType = when(lastExpr) {
                        is Expression -> lastExpr.type!!
                        is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                        is Assign -> lastExpr.value.type!!
                        else -> lastExpr.token.compileError("In switch expression body last statement must be an expression")
                    }
//                    val elseReturnType =  lastExpr.type!!
                    val elseReturnTypeName = elseReturnType.name
                    val firstReturnTypeName = firstBranchReturnType!!.name
                    if (elseReturnTypeName != firstReturnTypeName) {
                        lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same(${WHITE}$firstReturnTypeName ${RED}!= ${WHITE}$elseReturnTypeName)")
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
            statement.kind = detectExprOrStatement()

            if (statement.switch.type == null) {
                currentLevel++
                resolve(listOf(statement.switch), previousAndCurrentScope, statement)
                currentLevel--
            }


            var firstBranchReturnType: Type? = null
            val savedSwitchType = statement.switch.type

            val typesAlreadyChecked = mutableSetOf<Type>()
            var thisIsTypeMatching = false
            statement.ifBranches.forEachIndexed { i, it ->
                /// resolving if (^)
                currentLevel++
                resolve(listOf(it.ifExpression), previousAndCurrentScope, statement)
                currentLevel--
                val currentTypeName = it.ifExpression.type?.name
                val currentType = it.ifExpression.type!!

                if (i == 0 && it.ifExpression is IdentifierExpr && currentTypeName == it.ifExpression.name) {
                    statement.kind =
                        if (statement.kind == ControlFlowKind.Expression) ControlFlowKind.ExpressionTypeMatch
                        else ControlFlowKind.StatementTypeMatch
                }
                // this is cheaper than every string comparison
                thisIsTypeMatching =
                    statement.kind == ControlFlowKind.ExpressionTypeMatch || statement.kind == ControlFlowKind.StatementTypeMatch

                // new instance in every loop iteration to erase previous
                val scopeWithThisFields: MutableMap<String, Type> = mutableMapOf()

                if (thisIsTypeMatching && statement.switch is IdentifierExpr) {
                    if (currentType is Type.UserLike) {
                        currentType.fields.forEach {
                            val alreadyDeclarated = currentScope[it.name]
                            if (alreadyDeclarated == null) {
                                scopeWithThisFields[it.name] = it.type
                            }
                        }
                    }

                    currentScope[statement.switch.name] = currentType
                    previousAndCurrentScope[statement.switch.name] = currentType
                    statement.switch.type = currentType
                    typesAlreadyChecked += currentType
                }


                if (!compare2Types(currentType, statement.switch.type!!)) {
                    val curTok = it.ifExpression.token
                    curTok.compileError("If branch ${WHITE}`${it.ifExpression}`${RED} of type: ${YEL}`$currentType`${RED} is not of switching type: ${WHITE}`${statement.switch.type!!.name}`")
                }
                /// resolving then, if() ^
                val scopeWithFields =
                    if (thisIsTypeMatching) (previousAndCurrentScope + scopeWithThisFields).toMutableMap() else previousAndCurrentScope
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        currentLevel++
                        resolve(listOf(it.thenDoExpression), scopeWithFields, statement)
                        currentLevel--
                    }

                    is IfBranch.IfBranchWithBody -> {
                        if (it.body.isNotEmpty()) {
                            currentLevel++
                            resolve(it.body, scopeWithFields, statement)
                            currentLevel--

                            val lastExpr = it.body.last()
                            if (lastExpr.isNotExpression()) {
                                lastExpr.token.compileError("In if expression body last statement must be an expression")
                            }
                        }
                    }
                }

                // compare the current branch type with the last one
                if (i > 0) {
                    val prev = statement.ifBranches[i - 1]

                    val prevType: Type = prev.getReturnTypeOrThrow()
                    val currType = it.getReturnTypeOrThrow()
                    val isTypeEqual = compare2Types(prevType, currType)
                    if (!isTypeEqual) {
                        it.ifExpression.token.compileError(
                            "In switch Expression return type of branch on line: $WHITE${prev.ifExpression.token.line}$RED is $YEL${prevType.name}$RED "
                                    + "\n\tBut return type of branch on line $WHITE${it.ifExpression.token.line}$RED is $YEL${currType.name}$RED, all branches must return the same type"
                        )
                    }
                } else {
                    firstBranchReturnType = it.getReturnTypeOrThrow()
                }
            }

            statement.switch.type = savedSwitchType

            if (statement.elseBranch != null) {
                currentLevel++
                resolve(statement.elseBranch, previousAndCurrentScope, statement)
                currentLevel--
                val lastExpr = statement.elseBranch.last()
//                if (lastExpr !is Expression) {
//                    lastExpr.token.compileError("In switch expression body last statement must be an expression")
//                }
                val elseReturnType = when (lastExpr) {
                    is Expression -> lastExpr.type!!
                    is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                    is Assign -> lastExpr.value.type!!
                    else -> lastExpr.token.compileError("In switch expression body last statement must be an expression")
                }
//                val elseReturnType = lastExpr.type!!
                val elseReturnTypeName = elseReturnType.name
                val firstReturnTypeName = firstBranchReturnType!!.name
                if (elseReturnTypeName != firstReturnTypeName) {
                    lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($YEL$firstReturnTypeName$RED != $YEL$elseReturnTypeName$RED)")
                }
                statement.type = elseReturnType
            } else if (thisIsTypeMatching) {
                // check that this is exhaustive checking
                val root = if (savedSwitchType is Type.UserUnionRootType && savedSwitchType.parent == null) savedSwitchType else savedSwitchType!!.parent ?: throw Exception("Pattern matching on not union root?")
                if (root is Type.UserUnionRootType) {
                    val realBranchTypes = mutableSetOf<Type>()
                    root.branches.forEach {
                        realBranchTypes += it
                    }
                    if (realBranchTypes != typesAlreadyChecked) {
                        val difference = (realBranchTypes - typesAlreadyChecked).joinToString(", ") { it.name }
                        statement.token.compileError("Not all types are checked: ($YEL$difference$RED)")
                    }

                    if (statement.type == null) {
                        statement.type = root
                    }
                }
            }

        }

    }
}
