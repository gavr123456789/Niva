package main.languageServer

import main.frontend.parser.types.ast.Assign
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.CollectionAst
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.ControlFlow
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.DotReceiver
import main.frontend.parser.types.ast.EnumBranch
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.ErrorDomainDeclaration
import main.frontend.parser.types.ast.ExpressionInBrackets
import main.frontend.parser.types.ast.ExtendDeclaration
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.IfBranch
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.ManyConstructorDecl
import main.frontend.parser.types.ast.MapCollection
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageSend
import main.frontend.parser.types.ast.MethodReference
import main.frontend.parser.types.ast.NeedInfo
import main.frontend.parser.types.ast.ReturnStatement
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.StaticBuilder
import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.TypeAliasDeclaration
import main.frontend.parser.types.ast.TypeDeclaration
import main.frontend.parser.types.ast.UnaryMsg
import main.frontend.parser.types.ast.UnionBranchDeclaration
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.parser.types.ast.VarDeclaration

fun clearNonIncrementalStoreFromTypes(nonIncrementalStore: MutableMap<String, List<Statement>>) {
    nonIncrementalStore.values.forEach {
        it.forEach { statement ->
            statement.clearFromType()
        }
    }
}

fun Statement.clearFromType() {
    when (this) {
        is VarDeclaration -> {
            this.value.clearFromType()
        }
        is Assign -> this.value.clearFromType()

        is ExtendDeclaration -> {
            this.messageDeclarations.forEach { t ->
                t.clearFromType()
            }
        }
        is ManyConstructorDecl -> {
            this.messageDeclarations.forEach { t ->
                t.clearFromType()
            }
        }
        is ConstructorDeclaration -> {
            this.forType = null
            this.returnType = null
            this.messageData = null
            this.stackOfPossibleErrors.clear()
            this.body.forEach { it.clearFromType() }
            this.msgDeclaration.clearFromType()
        }
        is MessageDeclaration -> {
            this.forType = null
            this.returnType = null
            this.messageData = null
            this.stackOfPossibleErrors.clear()
            this.body.forEach { it.clearFromType() }
        }
        is EnumBranch -> {
            this.receiver = null
        }
        is EnumDeclarationRoot -> {
            this.receiver = null
            this.branches.forEach { it.clearFromType() }
        }
        is ErrorDomainDeclaration -> {
            this.unionDeclaration.clearFromType()
            this.receiver = null
        }

        is TypeAliasDeclaration -> {
            this.receiver = null
            this.realType = null
        }
        is TypeDeclaration -> {
            this.receiver = null
        }
        is UnionBranchDeclaration -> {
            this.receiver = null
        }
        is UnionRootDeclaration -> {
            this.receiver = null
            this.branches.forEach { it.clearFromType() }
        }

        is DestructingAssign -> {
            this.value.clearFromType()
        }
        is ControlFlow.If -> {
            this.ifBranches.forEach {
                it.ifExpression.clearFromType()
                it.otherIfExpressions.forEach { it.clearFromType() }
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        it.thenDoExpression.clearFromType()
                    }
                    is IfBranch.IfBranchWithBody -> {
                        it.body.clearFromType()
                    }
                }
            }
            this.elseBranch?.forEach { it.clearFromType() }
            this.type = null
        }
        is ControlFlow.Switch -> {
            this.ifBranches.forEach {
                it.ifExpression.clearFromType()
                it.otherIfExpressions.forEach { it.clearFromType() }
                when (it) {
                    is IfBranch.IfBranchSingleExpr -> {
                        it.thenDoExpression.clearFromType()
                    }
                    is IfBranch.IfBranchWithBody -> {
                        it.body.clearFromType()
                    }
                }
            }
            this.elseBranch?.forEach { it.clearFromType() }

            this.type = null
            this.switch.clearFromType()
        }
        is CodeBlock -> {
            this.type = null
            this.inputList.forEach { it.clearFromType() }
            this.statements.forEach { it.clearFromType() }
        }
        is CollectionAst -> {
            this.initElements.forEach { t -> t.clearFromType() }
            this.type = null
        }
        is DotReceiver -> {
            this.type = null
        }
        is ExpressionInBrackets -> {
            this.type = null
            this.expr.clearFromType()
        }
        is MapCollection -> {
            this.type = null
            this.initElements.forEach { it.first.clearFromType(); it.second.clearFromType() }
        }
        is BinaryMsg -> {
            this.receiver.clearFromType()
            this.type = null
            this.argument.clearFromType()
            this.unaryMsgsForArg.forEach { it.clearFromType() }
            this.unaryMsgsForReceiver.forEach { it.clearFromType() }

            this.msgMetaData = null
            this.declaration = null

        }
        is KeywordMsg -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
            this.args.forEach { it.keywordArg.clearFromType() }
        }
        is StaticBuilder -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
        }
        is UnaryMsg -> {
            this.type = null
            this.receiver.clearFromType()
            this.msgMetaData = null
        }
        is MessageSend -> {
            this.type = null
            this.receiver.clearFromType()
            this.messages.forEach { it.clearFromType() }
        }
        is MethodReference -> {
            this.type = null
            this.method = null
        }
        is IdentifierExpr -> this.type = null
        is LiteralExpression -> {
            this.type = null
        }
        is NeedInfo -> {
            this.expression?.clearFromType()
        }
        is ReturnStatement -> this.expression?.clearFromType()
        is TypeAST.InternalType -> {}
        is TypeAST.Lambda -> {}
        is TypeAST.UserType ->{}

    }
}
