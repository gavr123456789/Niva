package main.languageServer

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Type
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.CollectionAst
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.ManyConstructorDecl
import main.frontend.parser.types.ast.MapCollection
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageSend
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.VarDeclaration
import java.io.File

fun LS.onEachStatementCall(
    st: Statement,
    currentScope: Map<String, Type>?,
    previousScope: Map<String, Type>?,
    file2: File
) {
    fun addStToMegaStore(s: Statement, prepend: Boolean = false) {
        megaStore.addNew(
            s = s,
            scope =
                if (currentScope != null && previousScope != null)
                    currentScope + previousScope
                else
                    mutableMapOf(),
            prepend
        )
    }

    // recursively register all IdentifierExpr usages within an expression
    fun registerIdentifierUsages(expr: Expression) {
        val scope =
            if (currentScope != null && previousScope != null)
                currentScope + previousScope
            else
                mutableMapOf()
        when (expr) {
            is IdentifierExpr -> {
                if (!expr.isType && scope.containsKey(expr.name)) {
                    val key = "${expr.token.file.absolutePath}:${expr.name}"
                    val declarationToken = varNameToDeclarationToken[key]
                    if (declarationToken != null) {
                        info?.invoke("---Found usage of ${expr.name} at ${expr.token.toPositionKey()}")
                        varUsageToDeclaration[expr.token.toPositionKey()] = declarationToken
                    }
                }
            }
            is Message -> {
                registerIdentifierUsages(expr.receiver)
                when (expr) {
                    is BinaryMsg -> {
                        registerIdentifierUsages(expr.argument)
                        expr.unaryMsgsForReceiver.forEach { registerIdentifierUsages(it) }
                        expr.unaryMsgsForArg.forEach { registerIdentifierUsages(it) }
                    }
                    is KeywordMsg -> {
                        expr.args.forEach { arg ->
                            registerIdentifierUsages(arg.keywordArg)
                        }
                    }
                    else -> {}
                }
            }
            is MessageSend -> {
                registerIdentifierUsages(expr.receiver)
                expr.messages.forEach { registerIdentifierUsages(it) }
            }
            is CodeBlock -> {
                expr.statements.forEach { stmt ->
                    if (stmt is Expression) {
                        registerIdentifierUsages(stmt)
                    }
                }
            }
            is CollectionAst -> {
                expr.initElements.forEach { registerIdentifierUsages(it) }
            }
            is MapCollection -> {
                expr.initElements.forEach { (key, value) ->
                    registerIdentifierUsages(key)
                    registerIdentifierUsages(value)
                }
            }
            else -> {}
        }
    }

    when (st) {
        is Expression, is VarDeclaration -> {
            addStToMegaStore(st)

            when (st) {
                is Message -> st.declaration?.messageData?.msgSends?.add(st)
                is VarDeclaration -> {
                    val key = "${st.token.file.absolutePath}:${st.name}"
                    varNameToDeclarationToken[key] = st.token
                    registerIdentifierUsages(st.value)
                }
                is Expression -> registerIdentifierUsages(st)
            }

        }

        is Declaration -> {
            // fill fileToDecl
            val setOfStatements = this.fileToDecl[file2.absolutePath]
            if (setOfStatements != null) {
                setOfStatements.add(st)
            } else {
                fileToDecl[file2.absolutePath] = mutableSetOf(st)
            }
            // add doc comments so u can ctrl click them
            st.docComment?.let {
                it.identifiers?.forEach { addStToMegaStore(it) }
            }

            fun messageDecl(st: MessageDeclaration) {
                val realSt = when (st) {
                    is ConstructorDeclaration -> st.msgDeclaration
                    else -> st
                }

                // forType
                realSt.forType?.let {
                    realSt.forTypeAst.toIdentifierExpr(it, true).also {
                        // we prepend here because
                        // `Sas kek = this | match deep |`
                        // Sas is the last expression on the line, so it replace the whole line instead of
                        // only `this`
                        addStToMegaStore(it, prepend = true)
                    }
                }

                val returnType = st.returnType
                if (returnType != null) {
                    realSt.returnTypeAST?.toIdentifierExpr(returnType, true)?.also {
                        addStToMegaStore(it, prepend = true)
                    }
                }
                // args
                if (realSt is MessageDeclarationKeyword) {
                    realSt.args.forEachIndexed { i, arg ->
                        // for some reason arg types are null here, so I use typeDB
                        val type =
                            ((realSt.messageData ?: st.messageData) as? KeywordMsgMetaData)?.argTypes[i]?.type
                        if (type != null) arg.typeAST?.toIdentifierExpr(type, true)?.also {
                            addStToMegaStore(it)
                        }
                    }
                }
            }
            // add types of the decl as IdentExpr
            if (st is MessageDeclaration && st.returnType != null) {
                messageDecl(st)
            }
            if (st is ManyConstructorDecl) {
                st.messageDeclarations.forEach {
                    messageDecl(it)
                }
            }
        }

        is DestructingAssign -> {
            st.names.forEach {
                addStToMegaStore(it)
            }
            addStToMegaStore(st.value)
        }


        else -> {}
    }
}