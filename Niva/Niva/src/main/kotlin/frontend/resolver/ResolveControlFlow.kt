package frontend.resolver

import frontend.resolver.messageResolving.resolveCodeBlock
import frontend.resolver.messageResolving.resolveCodeBlockAsBody
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import kotlin.collections.ArrayDeque


fun Statement.isNotExpression(): Boolean =
    this !is Expression && this !is ReturnStatement && this !is Assign

fun getAssignTypeForControlFlow(branchReturnTypes: List<Type>, tok: Token): Type  {
    if (branchReturnTypes.isEmpty()) throw Exception("Compiler bug: 0 branches in ControlFlow")
    if (branchReturnTypes.count() == 1) return branchReturnTypes.first()

    // find general root
    // simplest variant, all the same
    var currentGeneralRoot:Type? = null
    var resultIsNullabel = false

    (1..<branchReturnTypes.count()).forEach {
        val cur = branchReturnTypes[it]
        val prev = branchReturnTypes[it - 1]

        val realType1 = cur.unpackNull()
        val realType2 = prev.unpackNull()
        if (!resultIsNullabel && realType1 != cur) resultIsNullabel = true

        val generalRoot = findGeneralRoot(realType1, realType2)
        if (generalRoot == null) {
            return@forEach
        } else
            currentGeneralRoot = generalRoot
    }

    // all types same reference
    val cgr = currentGeneralRoot
    if (cgr != null) {
        if (resultIsNullabel && cgr !is Type.NullableType) {
            return Type.NullableType(cgr)
        }
        return cgr
    }

    tok.compileError("Cant find general root between $YEL$branchReturnTypes$RESET")

}




fun Resolver.resolveControlFlow(
    statement: ControlFlow,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {
    if (statement.ifBranches.isEmpty()) {
        statement.token.compileError("If must contain at least one branch")
    }
    // if from different branches we return different types, but its Null from -> Int?
    val rootStatementIsMessageDeclAndItReturnsNullable = {
        rootStatement != null && rootStatement is MessageDeclaration && rootStatement.returnTypeAST?.isNullable == true
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

            is CodeBlock -> {
                if (!rootStatement.isStatement && rootStatement.statements.last() == statement) {
                    if (statement is ControlFlow.If && statement.elseBranch == null)
                        ControlFlowKind.Statement
                    else
                        ControlFlowKind.Expression
                }
                else
                    ControlFlowKind.Statement
            }


            is MessageDeclaration -> {
                if (rootStatement.isSingleExpression)
                    ControlFlowKind.Expression
                else
                    ControlFlowKind.Statement
            }

            else -> ControlFlowKind.Statement
        }

    }

    val resolveControlFlowIf = { statement: ControlFlow.If ->

        var firstBranchReturnType: Type? = null
        statement.kind = detectExprOrStatement()

        val isStatement =
            statement.kind == ControlFlowKind.StatementTypeMatch || statement.kind == ControlFlowKind.Statement

        statement.ifBranches.forEachIndexed { i, it ->
            /// resolving if
            currentLevel++
            resolveSingle((it.ifExpression), previousAndCurrentScope, statement)
            currentLevel--

            val ifExpr = it.ifExpression
            if (isStatement) {
                val ifType = ifExpr.type!!
                if (ifType !is Type.NullableType && ifType != Resolver.defaultTypes[InternalTypes.Boolean] && ifType != Resolver.defaultTypes[InternalTypes.Nothing]) {
                    ifExpr.token.compileError("if branch ${WHITE}${ifExpr}$RESET must be of the ${YEL}Boolean$RESET or nullable type, but found ${YEL}$ifType")
                }
            }

            /// resolving then
            when (it) {
                is IfBranch.IfBranchSingleExpr -> {
                    currentLevel++
                    resolveSingle((it.thenDoExpression), previousAndCurrentScope, statement)
                    currentLevel--
                }

                is IfBranch.IfBranchWithBody -> {
                    if (it.body.statements.isNotEmpty()) {
                        currentLevel++

                        resolveCodeBlock(it.body, previousScope, currentScope, statement)
                        currentLevel--
                        if (statement.kind == ControlFlowKind.Expression) {
                            val lastExpr = it.body.statements.last()

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
                        "In if Expression return type of branch on line: ${WHITE}${prev.ifExpression.token.line}$RESET is ${WHITE}${prevType.name}${RESET} " +
                                "\n\tBut return type of branch on line ${WHITE}${it.ifExpression.token.line}${RESET} is ${WHITE}${currType.name}${RESET}, all branches must return the same type"
                    )
                }
            } else {
                firstBranchReturnType = it.getReturnTypeOrThrow()
            }
        }


        if (statement.kind == ControlFlowKind.Expression && statement.elseBranch == null) {
            statement.token.compileError("If expression must contain else branch `|=>`")
        }

        statement.type = if (statement.elseBranch != null) {
            currentLevel++
            resolve(statement.elseBranch, previousAndCurrentScope, statement)
            currentLevel--
            if (statement.kind == ControlFlowKind.Expression) {
                val lastExpr = statement.elseBranch.last()

                val elseReturnType = when (lastExpr) {
                    is Expression -> lastExpr.type!!
                    is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                    is Assign -> lastExpr.value.type!!
                    else -> lastExpr.token.compileError("In switch expression body last statement must be an expression")
                }
                val elseReturnTypeName = elseReturnType.name
                val firstReturnTypeName = firstBranchReturnType!!.name
                val areIfAndElseEqual = compare2Types(firstBranchReturnType, elseReturnType)
                if (!areIfAndElseEqual && !rootStatementIsMessageDeclAndItReturnsNullable()) {
                    lastExpr.token.compileError("(${YEL}$firstReturnTypeName ${RESET}!= ${YEL}$elseReturnTypeName${RESET}) In if Expression return type of else branch and main branches are not the same")
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

    val resolveControlFlowSwitch = { statement: ControlFlow.Switch ->

        statement.kind = detectExprOrStatement()

        if (statement.switch.type == null) {
            currentLevel++
            resolveSingle(statement.switch, previousAndCurrentScope, statement)
            currentLevel--
        }


        var firstBranchReturnType: Type? = null
        val savedSwitchType = statement.switch.type!!

        val typesAlreadyChecked = mutableSetOf<Type>()
        var thisIsTypeMatching = false
        statement.ifBranches.forEachIndexed { i, it ->
            /// resolving if (^)
            currentLevel++
            resolveSingle(it.ifExpression, previousAndCurrentScope, statement)
            currentLevel--
            val currentTypeName = it.ifExpression.type?.name
            val currentType = it.ifExpression.type!!

            if (i == 0 && it.ifExpression is IdentifierExpr && currentTypeName == it.ifExpression.name) {
                statement.kind =
                    if (statement.kind == ControlFlowKind.Expression)
                        ControlFlowKind.ExpressionTypeMatch
                    else
                        ControlFlowKind.StatementTypeMatch
            }
            // this is cheaper than every string comparison
            thisIsTypeMatching =
                statement.kind == ControlFlowKind.ExpressionTypeMatch || statement.kind == ControlFlowKind.StatementTypeMatch

            // new instance in every loop iteration to erase previous
            val scopeWithThisFields = mutableMapOf<String, Type>()

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
                curTok.compileError("${WHITE}${it.ifExpression}${RESET} has type: ${YEL}$currentType${RESET} but you matching on\n       $WHITE${statement.switch}$RESET of type ${YEL}${statement.switch.type!!.name}")
            }
            /// resolving then, if() ^
            val scopeWithFields =
                if (thisIsTypeMatching) (previousAndCurrentScope + scopeWithThisFields).toMutableMap() else previousAndCurrentScope
            when (it) {
                is IfBranch.IfBranchSingleExpr -> {
                    currentLevel++
                    resolveSingle((it.thenDoExpression), scopeWithFields, statement)
                    currentLevel--
                }

                is IfBranch.IfBranchWithBody -> {
                    if (it.body.statements.isNotEmpty()) {
                        currentLevel++
//                            resolve(it.body, scopeWithFields, statement)
                        resolveCodeBlockAsBody(it.body, previousScope, currentScope, statement)

                        currentLevel--

                        val lastExpr = it.body.statements.last()
                        if (lastExpr.isNotExpression()) {
                            lastExpr.token.compileError("In if expression body last statement must be an expression")
                        }
                    } else {
                        it.body.type = Resolver.defaultTypes[InternalTypes.Unit]
                    }
                }
            }

            // compare the current branch type with the last one
            if (i > 0) {
                val prev = statement.ifBranches[i - 1]

                val prevType: Type = prev.getReturnTypeOrThrow()
                val currType = it.getReturnTypeOrThrow()
                val isTypeEqual = compare2Types(prevType, currType, unpackNull = true, compareParentsOfBothTypes = true)
                if (!isTypeEqual) {
                    it.ifExpression.token.compileError(
                        "In switch Expression return type of branch on line: $WHITE${prev.ifExpression.token.line}$RESET is $YEL${prevType.name}$RESET "
                                + "\n\tBut return type of branch on line $WHITE${it.ifExpression.token.line}$RESET is $YEL${currType.name}$RESET, all branches must return the same type"
                    )
                }
            } else {
                firstBranchReturnType = it.getReturnTypeOrThrow()
            }
        }
        val firstBranchReturnType2 = firstBranchReturnType
            ?: statement.token.compileError("Can't infer return type of the first branch, probably bug")

        // restore it type in current scope after narrowing in pm blocks
        statement.switch.type = savedSwitchType
        if (statement.switch is IdentifierExpr) {
            previousAndCurrentScope[statement.switch.name] = savedSwitchType
            currentScope[statement.switch.name] = savedSwitchType
        }
        if (statement.elseBranch != null) {
            currentLevel++
            resolve(statement.elseBranch, previousAndCurrentScope, statement)
            currentLevel--
            val lastExpr = statement.elseBranch.last()

            val elseReturnType = when (lastExpr) {
                is Expression -> lastExpr.type!!
                is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                is Assign -> lastExpr.value.type!!
                else -> lastExpr.token.compileError("In switch expression body last statement must be an expression")
            }
            if (!compare2Types(firstBranchReturnType2, elseReturnType)) {
                lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($YEL$firstBranchReturnType2$RESET != $YEL$elseReturnType$RESET)")
            }
//                statement.type = elseReturnType
            statement.type =
                getAssignTypeForControlFlow(statement.ifBranches.map { it.getReturnTypeOrThrow() }, statement.token)

        } else if (thisIsTypeMatching) {
            when (savedSwitchType) {
                is Type.UnionRootType -> {
                    val realBranchTypes = mutableSetOf<Type>()
                    // we need to get only one level of root if its error
                    // because all errors domain have one general root,
                    // and we don't want to pattern match them all
                    val root = if (savedSwitchType.isError) savedSwitchType else savedSwitchType.getRoot()
                    if (root is Type.UnionRootType) {
                        root.branches.forEach {
                            realBranchTypes += it
                        }
                    }
                    recursiveCheckThatEveryBranchChecked(
                        realBranchTypes,
                        typesAlreadyChecked,
                        statement.token,
                        statement.ifBranches.associateBy({ it.ifExpression.type },
                            { it.ifExpression.token })
                    )

                    if (statement.type == null) {
//                            statement.type = firstBranchReturnType2
                        statement.type = getAssignTypeForControlFlow(
                            statement.ifBranches.map { it.getReturnTypeOrThrow() },
                            statement.token
                        )
                    }
                }

                else -> {
                    // this is branch, no need to do anything... for some reason
                    // probably we resolve it already from resolving root
                }
            }

        } else {
            if (statement.type == null) {
                statement.type =
                    getAssignTypeForControlFlow(statement.ifBranches.map { it.getReturnTypeOrThrow() }, statement.token)

            }
        }

    }

    when (statement) {
        is ControlFlow.If -> resolveControlFlowIf(statement)
        is ControlFlow.Switch -> resolveControlFlowSwitch(statement)
    }

}

fun Type.Union.unpackUnionToAllBranches(x: MutableSet<Type.Union>, typeToToken: Map<Type?, Token>): Set<Type.Union> {
    when (this) {
        is Type.UnionRootType -> {
            this.branches.forEach {
                val y = it.unpackUnionToAllBranches(x, typeToToken)
                x.addAll(y)
            }
        }
        is Type.UnionBranchType -> {
            if (x.contains(this) && typeToToken.contains(this)) {
                fun findSas(type: Type ,path: ArrayDeque<Type>): ArrayDeque<Type> {
                    val p = type.parent
                    if (p != null) {
                        path.addFirst(type)
                        return findSas(p, path)
                    }

                    return path
                }
                val path = findSas(this, ArrayDeque())
                val strPath = path.joinToString("$RESET -> $YEL") { "$YEL${it.name}" }
                val tokFromMap = {
                    val e = path.find { typeToToken[it] != null }
                    typeToToken[e]
                }()
//                 val w =   typeToToken[path.first()]
                val curLine = (typeToToken[this]?.line)
                val curLineText = if (curLine != null) "(line $curLine)" else ""
                val onLine = if (tokFromMap!=null) "on line ${tokFromMap.line}" else ""
                println("${YEL}Warning:$RESET$curLineText $this was already checked $onLine ($strPath$RESET)")
            }
            x.add(this)
        }
    }
    return x
}

fun recursiveCheckThatEveryBranchChecked(
    branchesFromDb: MutableSet<Type>,
    branchesInSwitch: MutableSet<Type>,
    tok: Token,
    typeToTok: Map<Type?, Token>
) {
    val fromDb = branchesFromDb
        .filterIsInstance<Type.Union>()
        .flatMap { it.unpackUnionToAllBranches(mutableSetOf(), typeToTok)}.toSet()

    val realSet = mutableSetOf<Type.Union>()
    val real = branchesInSwitch
        .filterIsInstance<Type.Union>()
        .flatMap { it.unpackUnionToAllBranches(realSet, typeToTok)}.toSet()


    if (fromDb != real) {
        if (fromDb.count() > real.count()) {
            val difference = (fromDb - real).joinToString("$RESET, $YEL") { it.name }
            tok.compileError("Not all possible variants have been checked ($YEL$difference$RESET)")
        } else {
            val difference = (real - fromDb).joinToString("$RESET, $YEL") { it.name }
            tok.compileError("Extra unions are checked: ($YEL$difference$RESET)")
        }
    }

}

fun Type.getRoot(): Type =
    parent?.getRoot() ?: this

