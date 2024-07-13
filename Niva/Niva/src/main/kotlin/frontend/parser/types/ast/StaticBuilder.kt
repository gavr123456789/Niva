package main.frontend.parser.types.ast

import frontend.resolver.KeywordArgAst
import frontend.resolver.Type
import frontend.resolver.compare2Types
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.utils.GlobalVariables
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL


//class StaticBuilder(
//    val name: String,
//    val statements: List<Statement>,
//    var defaultAction: CodeBlock? = null,
//    val args: List<KeywordArgAst>,
//    type: Type?,
//    token: Token,
//    val expressions: MutableSet<Expression> = mutableSetOf()
//) : Receiver(type, token)

class StaticBuilder(
    val name: String,
    val statements: List<Statement>,
    var defaultAction: CodeBlock? = null,
    val args: List<KeywordArgAst>,
    type: Type?,
    token: Token,
    val receiverOfBuilder: Receiver?,
    val expressions: MutableSet<Expression> = mutableSetOf(),
    declaration: StaticBuilderDeclaration?
) : Message(receiverOfBuilder ?: IdentifierExpr(name, listOf(name), null, token), name, listOf(name), type, token, declaration)

// the body should be already resolved
fun StaticBuilder.collectExpressionsForDefaultAction() {
    val defaultAction = defaultAction
    if (defaultAction != null) {
        if (defaultAction.inputList.count() != 1) {
            this.token.compileError("Builders default action must contain one argument `defaultAction = [it::Int -> ...]`")
        }

        val typeOfDefaultAction = defaultAction.inputList.first().type!!

        statements.filterIsInstance<Expression>()
            .forEach {
                val type = it.type!!
                when (it) {
                    is Primary, is CollectionAst, is MapCollection, is ExpressionInBrackets -> {
                        if (compare2Types(type, typeOfDefaultAction, it.token)) {
                            expressions.add(it)
                        } else {
                            if (!GlobalVariables.isLspMode) {
                                println("${YEL}Warning:$RESET looks like you want to use $WHITE$it$RESET in default action of the builder, but it takes $YEL$typeOfDefaultAction$RESET not $YEL${it.type} $RESET")
                            }
                        }
                    }

                    is DotReceiver -> TODO() // not sure
                    else -> {}
                }
            }

    }
}


