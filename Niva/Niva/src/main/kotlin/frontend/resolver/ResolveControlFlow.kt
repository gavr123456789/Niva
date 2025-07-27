package frontend.resolver

import frontend.resolver.Type.EnumBranchType
import frontend.resolver.messageResolving.resolveCodeBlock
import frontend.resolver.messageResolving.resolveCodeBlockAsBody
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.prettyCodePlace
import main.frontend.parser.types.ast.*
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.capitalizeFirstLetter
import main.utils.warning
import kotlin.collections.ArrayDeque


fun Statement.isNotExpression(): Boolean =
    this !is Expression && this !is ReturnStatement && this !is Assign

fun findGeneralRootMany(branchReturnTypes: List<Type>, tok: Token): Type  {

    if (branchReturnTypes.isEmpty()) throw Exception("Compiler bug: 0 branches in ControlFlow")
    var resultIsNullable = false
    val branchesWithoutNull = branchReturnTypes.filter {
        if (typeIsNull(it)) {
            resultIsNullable = true
            false
        } else true
    }
    if (branchesWithoutNull.count() == 1) {
        val result = branchesWithoutNull.first()
        return if (resultIsNullable && result !is Type.NullableType) {
            Type.NullableType(result)
        } else
            result
    }

    // find the general root
    // the simplest variant, all the same
    var currentGeneralRoot:Type? = null

    (1..<branchesWithoutNull.count()).forEach {
        val cur = branchesWithoutNull[it]
        val prev = branchesWithoutNull[it - 1]
        /// null check
        val realType1 = cur.unpackNull()
        val realType2 = prev.unpackNull()
        if (!resultIsNullable && realType1 != cur) resultIsNullable = true
        if (typeIsNull(prev)) {
            resultIsNullable = true
        }
        ///root between first 2
        val generalRoot = findGeneralRoot(realType1, realType2)
        if (generalRoot == null) {
            return@forEach
        } else
            currentGeneralRoot = generalRoot
    }

    // all types same reference
    val cgr = currentGeneralRoot
    if (cgr != null) {
        if (resultIsNullable && cgr !is Type.NullableType) {
            return Type.NullableType(cgr)
        }
        return cgr
    }

    val generalRoot = findGeneralRoot(branchesWithoutNull[0], branchesWithoutNull[1])
    tok.compileError("Cant find general root between $YEL$branchesWithoutNull$RESET")

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

    val detectExprOrStatementBasedOnRootSt = {
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
        statement.kind = detectExprOrStatementBasedOnRootSt()

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
                if (ifType !is Type.NullableType && ifType != Resolver.defaultTypes[InternalTypes.Bool] && ifType != Resolver.defaultTypes[InternalTypes.Nothing]) {
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
            if (i > 0 && (statement.kind == ControlFlowKind.ExpressionTypeMatch || statement.kind == ControlFlowKind.Expression)) {
                val prev = statement.ifBranches[i - 1]
                val prevType: Type = prev.getReturnTypeOrThrow()
                val currType = it.getReturnTypeOrThrow()
                val isTypeEqual =
                    compare2Types(prevType, currType, prev.ifExpression.token, unpackNull = true, compareParentsOfBothTypes = true, isOut = true, nullIsAny = true)
                if (!isTypeEqual) {
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

            if (statement.elseBranch.isNotEmpty()) {
                val lastExpr = statement.elseBranch.last()

                val elseReturnType = when (lastExpr) {
                    is Expression -> lastExpr.type!!
                    is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                    is Assign -> lastExpr.value.type!!
                    else ->
                        if (statement.kind == ControlFlowKind.Expression)
                            lastExpr.token.compileError("In switch expression body last statement must be an expression")
                        else Resolver.defaultTypes[InternalTypes.Unit]!!
                }
                // check if this still can be an if expression
                // if body and else types are the same, then change kind to expr
                val elseReturnTypeName = elseReturnType.name
                val firstReturnTypeName = firstBranchReturnType!!.name
                val whatIsTheGeneralRoot = findGeneralRoot(firstBranchReturnType, elseReturnType)
                if (whatIsTheGeneralRoot == null && !rootStatementIsMessageDeclAndItReturnsNullable() && statement.kind == ControlFlowKind.Expression) {
                    lastExpr.token.compileError("(${YEL}$firstReturnTypeName ${RESET}!= ${YEL}$elseReturnTypeName${RESET}) In if Expression return type of else branch and main branches are not the same")
                }
                if (whatIsTheGeneralRoot != null && statement.kind == ControlFlowKind.Statement) {
                    statement.kind = ControlFlowKind.Expression
                    statement.type = whatIsTheGeneralRoot
                }
                whatIsTheGeneralRoot
            } else {
                Resolver.defaultTypes[InternalTypes.Unit]!!
            }

        } else {
            // if no else branch, its not an expression already
            Resolver.defaultTypes[InternalTypes.Unit]!!
        }
    }


    val resolveControlFlowSwitch = { statement: ControlFlow.Switch ->

        statement.kind = detectExprOrStatementBasedOnRootSt()

        if (statement.switch.type == null) {
            currentLevel++
            resolveSingle(statement.switch, previousAndCurrentScope, statement)
            currentLevel--
        }
        statement.switch.isInlineRepl = false


        var firstBranchReturnType: Type? = null
        val savedSwitchType = statement.switch.type!!

        val typesAlreadyChecked = mutableSetOf<Type>()
        var thisIsTypeMatching = false // need to generate `is`
        // | x | null => ... |=> ...
        var thisIsNullMatching = false
        // resolve | (^) => ... part
        val mapOfExprStrToExpr = mutableMapOf<String, Expression>()
        statement.ifBranches.forEachIndexed { i, it ->

            // check that we don't check for the same type twice
            val strOfExpression = it.ifExpression.toString()
            if (!mapOfExprStrToExpr.containsKey(strOfExpression)) {
                mapOfExprStrToExpr[strOfExpression] = it.ifExpression
            } else {
                val previousLine = mapOfExprStrToExpr[strOfExpression]!!.token.line
                it.ifExpression.token.compileError("Such expression($strOfExpression) was already checked on line $previousLine")
            }

            currentLevel++
            resolveSingle(it.ifExpression, previousAndCurrentScope, statement)
            // resolving `| X, Y, Z` Y and Z here
            it.otherIfExpressions.forEach { otherExpr ->
                resolveSingle(otherExpr, previousAndCurrentScope, statement)
                typesAlreadyChecked.add(otherExpr.type!!)
            }
            currentLevel--
            val currentTypeName = it.ifExpression.type?.name
            val currentType = it.ifExpression.type!!

            if (!thisIsNullMatching)
                thisIsNullMatching = currentType.name == InternalTypes.Null.name

            if (i == 0 && it.ifExpression is IdentifierExpr && currentTypeName == it.ifExpression.name) {
                statement.kind =
                    if (statement.kind == ControlFlowKind.Expression)
                        ControlFlowKind.ExpressionTypeMatch
                    else
                        ControlFlowKind.StatementTypeMatch
                // if the match exp is a field of some type(not in current scope) or mutable then error
                val checkMutMatchError = { localName: String ->
                    val isField = {
                        // this in scope
                        // this type has switch field
                        val zis = previousAndCurrentScope["this"]
                        zis != null && zis is Type.UserLike && zis.fields.find {it.name == localName} != null
                    }()
                    val mutable = statement.switch.type?.isMutable ?: false
                    val name = localName.capitalizeFirstLetter()
                    val name2 = localName
                    if (isField || mutable) {
                        statement.switch.token.compileError(
                            "${statement.switch} is field or mut and can be mutated from another thread, match against a local copy like `local$name = $name2`"
                        )
                    }
                }
                if (statement.switch is IdentifierExpr) {
                    if (statement.switch.isType) {
                        statement.switch.token.compileError("${statement.switch} is type name, replace it with instance of type")
                    }
                    checkMutMatchError(statement.switch.name)
                } else if (statement.switch is UnaryMsg) {
                    checkMutMatchError(statement.switch.selectorName)
                }

            }
            // this is cheaper than every string comparison
            thisIsTypeMatching =
                statement.kind == ControlFlowKind.ExpressionTypeMatch || statement.kind == ControlFlowKind.StatementTypeMatch


            // new instance in every loop iteration to erase previous
            val scopeWithThisFields = mutableMapOf<String, Type>()
            if (thisIsNullMatching && statement.switch is IdentifierExpr) {
                val switchIsNullable = statement.switch.type is Type.NullableType
                if (!switchIsNullable) {
                    it.ifExpression.token.compileError("You can't match on null with non nullable type in switch (${statement.switch}::${statement.switch.type})")
                }
                if (currentType.name != InternalTypes.Null.name) {
                    val unpackedNull = currentType.unpackNull()
                    currentScope[statement.switch.name] = unpackedNull
                    previousAndCurrentScope[statement.switch.name] = unpackedNull
                    statement.switch.type = unpackedNull
                }
            }
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
                // add import of the type (if it's an errordomain it used only one time in matching)
                getCurrentPackage(statement.token)
                    .addImport(currentType.pkg)
            }

            val curTok = it.ifExpression.token
            if (!compare2Types(statement.switch.type!!, currentType, curTok, unpackNullForFirst = true)) {
                curTok.compileError("${WHITE}${it.ifExpression}${RESET} has type: ${YEL}$currentType${RESET} but you matching on\n       $WHITE${statement.switch}$RESET of type ${YEL}${statement.switch.type!!}")
            }
            /// resolving then, if() ^
            val scopeWithFields =
                if (thisIsTypeMatching)
                        (previousAndCurrentScope + scopeWithThisFields).toMutableMap()
                else previousAndCurrentScope
            when (it) {
                is IfBranch.IfBranchSingleExpr -> {
                    currentLevel++
                    resolveSingle((it.thenDoExpression), scopeWithFields, statement)
                    currentLevel--
                }

                is IfBranch.IfBranchWithBody -> {
                    if (it.body.statements.isNotEmpty()) {
                        currentLevel++
                        resolveCodeBlockAsBody(it.body, scopeWithFields, currentScope, statement)
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
            if (i > 0 && (statement.kind == ControlFlowKind.ExpressionTypeMatch || statement.kind == ControlFlowKind.Expression)) {
                val prev = statement.ifBranches[i - 1]

                val prevType: Type = prev.getReturnTypeOrThrow()
                val currType = it.getReturnTypeOrThrow()
                val isTypeEqual = compare2Types(prevType, currType, prev.ifExpression.token, unpackNull = true, compareParentsOfBothTypes = true, isOut = true, nullIsAny = true)
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
            if (thisIsNullMatching && statement.switch is IdentifierExpr) {
                previousAndCurrentScope[statement.switch.name] = savedSwitchType.unpackNull()
            }
            resolve(statement.elseBranch, previousAndCurrentScope, statement)
            if (statement.switch is IdentifierExpr)
                previousAndCurrentScope[statement.switch.name] = savedSwitchType

            currentLevel--
            var elseReturnTypeMaybe: Type? = null
            if (statement.elseBranch.isNotEmpty()) {
                val lastExpr = statement.elseBranch.last()

                val elseReturnType = when (lastExpr) {
                    is Expression -> lastExpr.type!!
                    is ReturnStatement -> lastExpr.expression?.type ?: Resolver.defaultTypes[InternalTypes.Unit]!!
                    is Assign -> Resolver.defaultTypes[InternalTypes.Unit]!! //lastExpr.value.type!!
                    else -> lastExpr.token.compileError("In switch expression body last statement must be an expression")
                }
                elseReturnTypeMaybe = elseReturnType

                // we have else branch here

                val generalRoot = findGeneralRoot(firstBranchReturnType2, elseReturnType)
                if (generalRoot == null) {
                    lastExpr.token.compileError("In switch Expression return type of else branch and main branches are not the same($YEL$firstBranchReturnType2$RESET != $YEL$elseReturnType$RESET)")
                }
            }

            val branchesReturnTypes = statement.ifBranches.map { it.getReturnTypeOrThrow() }.toMutableList()
            if (elseReturnTypeMaybe != null) {
                branchesReturnTypes.add(elseReturnTypeMaybe)
            }
            val result = findGeneralRootMany(branchesReturnTypes, statement.token)
            statement.type = result


        } else if (thisIsTypeMatching) {
            when (savedSwitchType) {
                is Type.UnionRootType -> {
                    val branchesFromDb: MutableSet<Type> = savedSwitchType.branches.toMutableSet()

                    recursiveCheckThatEveryBranchChecked(
                        branchesFromDb,
                        typesAlreadyChecked,
                        statement.token,
                        statement.ifBranches.associateBy({ it.ifExpression.type },
                            { it.ifExpression.token })
                    )

                    if (statement.type == null) {
                        statement.type = findGeneralRootMany(
                            statement.ifBranches.map { it.getReturnTypeOrThrow() },
                            statement.token
                        )
                    }
                }
                is Type.InternalType -> {
                    if (savedSwitchType.name == InternalTypes.Any.name) {
                        statement.token.compileError("When switching on Any else branch is required, add |=>, if its statement you can use |=> []")
                    }

                }

                else -> {
                    // this is branch, no need to do anything... for some reason
                    // probably we resolve it already from resolving root
                }
            }

        } else {
            // not a type matching
            // no else branch
            if (savedSwitchType.name == InternalTypes.Bool.name) {
                if (statement.ifBranches.count() > 2)
                    statement.token.compileError("You matching against Boolean, check only for true and false")
                val isTrue = statement.ifBranches.find {it.ifExpression.token.lexeme == "true"} != null
                val isFalse = statement.ifBranches.find {it.ifExpression.token.lexeme == "false"} != null

                if (isTrue && !isFalse) {
                    statement.token.compileError("false is not checked")
                } else if (!isTrue && isFalse) {
                    statement.token.compileError("true is not checked")
                }

            }
            if (statement.type == null) {
                statement.type =
                    findGeneralRootMany(statement.ifBranches.map { it.getReturnTypeOrThrow() }, statement.token)

            }
        }
        // check for enum exhaustiveness
        if (savedSwitchType is Type.EnumRootType && statement.elseBranch == null) {
            exhaustivenessEnumCheck(
                savedSwitchType.branches,
                statement.ifBranches.flatMap { listOf(it.ifExpression) + it.otherIfExpressions },
                statement.token
            )
        }
    }

    when (statement) {
        is ControlFlow.If -> resolveControlFlowIf(statement)
        is ControlFlow.Switch -> resolveControlFlowSwitch(statement)
    }

}
fun exhaustivenessEnumCheck(branchesFromDB: List<EnumBranchType>, switchingOn: List<Expression>, errToken: Token) {
    val setOfBranchesFromDb = branchesFromDB.map { it.name }.toSet()
    val setOfCheckedNames = mutableSetOf<String>()
    switchingOn.forEach {
        val type = it.type
        if (it is IdentifierExpr && (type is Type.EnumRootType || type is EnumBranchType)) {
            setOfCheckedNames.add(it.name)
        } else {
            val firstThreeBranches = branchesFromDB.take(3).joinToString()
            errToken.compileError("$it must be enum branch, one of $firstThreeBranches ...")
        }
    }
    val intersect = setOfBranchesFromDb - setOfCheckedNames

    if (intersect.isNotEmpty() ) {
        val rootName = branchesFromDB.first().root.name
        val strIntersect = intersect.joinToString(", ") { "$rootName.$it" }
//        if (setOfCheckedNames.count() < setOfBranchesFromDb.count())
            errToken.compileError("Exhaustiveness enum check failed, not checked: $strIntersect")
//        else if (setOfCheckedNames.count() > setOfBranchesFromDb.count()) {
//            errToken.compileError("You checked extra enums: $strIntersect")
//        }
    }
}
fun Type.Union.unpackUnionToAllBranches(x: MutableSet<Type.Union>, typeToToken: Map<Type?, Token>?): Set<Type.Union> {
    when (this) {
        is Type.UnionRootType -> {
            if (branches.isEmpty()) {
                x.add(this)
            } else
            this.branches.forEach {
                val y = it.unpackUnionToAllBranches(x, typeToToken)
                x.addAll(y)
            }
        }
        is Type.UnionBranchType -> {
            if (x.contains(this) && (typeToToken == null || typeToToken.contains(this))) {
                fun findSas(type: Type ,path: ArrayDeque<Type>): ArrayDeque<Type> {
                    val p = type.parent
                    if (p != null) {
                        path.addFirst(type)
                        return findSas(p, path)
                    }

                    return path
                }
                val path = findSas(this, ArrayDeque())
                val strPath = path.joinToString("$RESET <- $YEL") { "$YEL${it.name}" }
                if(typeToToken != null) {
                    val tokFromMap = {
                        val e = path.find { typeToToken[it] != null }
                        typeToToken[e]
                    }()
                    val onLine = if (tokFromMap != null) "on line ${tokFromMap.prettyCodePlace()}" else ""
                    // TODO make warning mechanism in LSP

                    warning("in matching $this was already checked $onLine ($strPath$RESET)")
                }
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
    val real2 = branchesInSwitch
        .filterIsInstance<Type.Union>()
    val real = real2.flatMap { it.unpackUnionToAllBranches(realSet, typeToTok)}.toSet()

    val realNamesAndPkg = real.map { it.name }.toSet()
    val fromDbNamesAndPkg = fromDb.map { it.name }.toSet()

    if (realNamesAndPkg != fromDbNamesAndPkg) {
        if (fromDbNamesAndPkg.count() > realNamesAndPkg.count()) {
            val difference = (fromDbNamesAndPkg - realNamesAndPkg).joinToString("$RESET, $YEL") { it }
            tok.compileError("Not all possible variants have been checked ($YEL$difference$RESET)")
        } else {
            val difference = (realNamesAndPkg - fromDbNamesAndPkg).joinToString("$RESET, $YEL") { it }
            tok.compileError("Extra unions are checked: ($YEL$difference$RESET)")
        }
    }

}

