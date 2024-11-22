package main.languageServer

import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Type
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.Message
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationKeyword
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

    when (st) {
        is Expression, is VarDeclaration -> {
            addStToMegaStore(st)

            if (st is Message) {
                st.declaration?.messageData?.msgSends?.add(st)
            }
        }

        is DestructingAssign -> {
            st.names.forEach {
                addStToMegaStore(it)
            }
            addStToMegaStore(st.value)
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
            // add types of the decl as IdentExpr
            if (st is MessageDeclaration && st.returnType != null) {
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
        }


        else -> {}
    }
}