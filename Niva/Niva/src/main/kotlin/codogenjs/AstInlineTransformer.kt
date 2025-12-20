package main.codogenjs

import frontend.resolver.KeywordArgAst
import main.frontend.parser.types.ast.*

class AstInlineTransformer(
    val receiver: Receiver,
    val paramMap: Map<String, Receiver>,
    val suffix: String
) {
    fun transformStatement(st: Statement): List<Statement> {
        return when (st) {
            is Expression -> {
                // Если это вызов лямбды-аргумента, инлайним её тело прямо сюда
                if (st is MessageSend) {
                    val newReceiver = transformReceiver(st.receiver)
                    if (newReceiver is CodeBlock && st.messages.size == 1) {
                        val msg = st.messages[0]
                        if (msg.selectorName == "do" || msg.selectorName == "value") {
                            return newReceiver.statements.flatMap { transformStatement(it) }
                        }
                    }
                }
                val transformed = transformExpression(st)
                listOf(transformed)
            }
            is VarDeclaration -> {
                listOf(VarDeclaration(
                    st.token,
                    st.name + suffix,
                    transformExpression(st.value),
                    st.valueTypeAst,
                    st.mutable,
                    st.pragmas,
                    st.declaredType
                ))
            }
            is Assign -> {
                listOf(Assign(
                    st.token,
                    st.name + suffix,
                    transformExpression(st.value),
                    st.pragmas
                ))
            }
            is ReturnStatement -> {
                listOf(ReturnStatement(
                    st.expression?.let { transformExpression(it) },
                    st.token,
                    st.pragmas
                ))
            }
            else -> listOf(st)
        }
    }

    fun transformExpression(expr: Expression): Expression {
        val result = when (expr) {
            is IdentifierExpr -> {
                if (expr.name == "this" && expr.names.size == 1) {
                    receiver
                } else if (paramMap.containsKey(expr.name) && expr.names.size == 1) {
                    paramMap[expr.name]!!
                } else {
                    if (expr.names.size == 1 && !expr.isType) {
                         IdentifierExpr(expr.name + suffix, listOf(expr.name + suffix), expr.typeAST, expr.token, expr.isType)
                    } else expr
                }
            }
            is MessageSend -> {
                val newReceiver = transformReceiver(expr.receiver)
                // Если мы вызываем лямбду, которая является аргументом, мы могли бы её инлайнить, 
                // но для этого нужно, чтобы MessageSend мог быть заменен на список стейтментов.
                // Пока оставим как вызов, но с замененным ресивером.
                
                val newMessages = expr.messages.map { msg ->
                    when (msg) {
                        is UnaryMsg -> UnaryMsg(
                            newReceiver,
                            msg.selectorName,
                            msg.path,
                            msg.type,
                            msg.token,
                            msg.kind,
                            msg.declaration
                        )
                        is BinaryMsg -> BinaryMsg(
                            newReceiver,
                            msg.unaryMsgsForReceiver.map { transformExpression(it) as UnaryMsg },
                            msg.selectorName,
                            msg.type,
                            msg.token,
                            transformReceiver(msg.argument),
                            msg.unaryMsgsForArg.map { transformExpression(it) as UnaryMsg },
                            msg.declaration
                        )
                        is KeywordMsg -> KeywordMsg(
                            newReceiver,
                            msg.selectorName,
                            msg.type,
                            msg.token,
                            msg.args.map { KeywordArgAst(it.name, transformExpression(it.keywordArg)) },
                            msg.path,
                            msg.kind,
                            msg.declaration
                        )
                        is StaticBuilder -> TODO()
                    }
                }
                when (expr) {
                    is MessageSendUnary -> MessageSendUnary(newReceiver, newMessages.filterIsInstance<Message>().toMutableList(), expr.type, expr.token)
                    is MessageSendBinary -> MessageSendBinary(newReceiver, newMessages.filterIsInstance<Message>(), expr.type, expr.token)
                    is MessageSendKeyword -> MessageSendKeyword(newReceiver, newMessages.filterIsInstance<Message>(), expr.type, expr.token)
                }
            }
            is CodeBlock -> {
                val lambdaParamNames = expr.inputList.map { it.name }.toSet()
                val innerTransformer = AstInlineTransformer(
                    receiver,
                    paramMap.filter { it.key !in lambdaParamNames },
                    suffix
                )
                CodeBlock(
                    expr.inputList,
                    expr.statements.flatMap { innerTransformer.transformStatement(it) },
                    expr.isSingle,
                    expr.type,
                    expr.token,
                    expr.isStatement,
                    expr.errors
                )
            }
            is ControlFlow.If -> {
                ControlFlow.If(
                    expr.ifBranches.map { br ->
                        when (br) {
                            is IfBranch.IfBranchSingleExpr -> IfBranch.IfBranchSingleExpr(
                                transformExpression(br.ifExpression),
                                transformExpression(br.thenDoExpression),
                                br.otherIfExpressions.map { transformExpression(it) }
                            )
                            is IfBranch.IfBranchWithBody -> IfBranch.IfBranchWithBody(
                                transformExpression(br.ifExpression),
                                transformExpression(br.body) as CodeBlock,
                                br.otherIfExpressions.map { transformExpression(it) }
                            )
                        }
                    },
                    expr.elseBranch?.flatMap { transformStatement(it) },
                    expr.kind,
                    expr.token,
                    expr.type
                )
            }
            else -> expr
        }
        result.type = expr.type
        return result
    }

    fun transformReceiver(rect: Receiver): Receiver {
        return when (rect) {
            is Expression -> transformExpression(rect) as Receiver
            is DotReceiver -> receiver
            else -> rect
        }
    }
}

fun hasReturn(st: Statement): Boolean = when (st) {
    is ReturnStatement -> true
    is Expression -> when (st) {
        is ControlFlow.If -> st.ifBranches.any { 
            when (it) {
                is IfBranch.IfBranchSingleExpr -> hasReturn(it.thenDoExpression)
                is IfBranch.IfBranchWithBody -> it.body.statements.any { s -> hasReturn(s) }
            }
        } || st.elseBranch?.any { s -> hasReturn(s) } ?: false
        is MessageSend -> false // вызовы методов не считаем за нелокальный возврат самого метода
        is CodeBlock -> false // возврат внутри лямбды - это возврат из неё, если она не инлайнится. 
        // Но мы тут ищем именно ReturnStatement в текущем скоупе инлайна
        else -> false
    }
    else -> false
}

fun MessageDeclaration.inlineBody(receiver: Receiver, arguments: List<Receiver>, suffix: String): List<Statement> {
    val paramMap = mutableMapOf<String, Receiver>()
    when (this) {
        is MessageDeclarationUnary -> {}
        is MessageDeclarationBinary -> {
            paramMap[arg.name] = arguments[0]
        }
        is MessageDeclarationKeyword -> {
            args.forEachIndexed { i, arg ->
                paramMap[arg.name] = arguments[i]
            }
        }
        is ConstructorDeclaration -> return this.msgDeclaration.inlineBody(receiver, arguments, suffix)
        is StaticBuilderDeclaration -> return this.msgDeclaration.inlineBody(receiver, arguments, suffix)
    }
    
    val transformer = AstInlineTransformer(receiver, paramMap, suffix)
    return body.flatMap { transformer.transformStatement(it) }
}
