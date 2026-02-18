package main.frontend.resolver.messageResolving

import main.frontend.parser.types.ast.*
import frontend.resolver.Type

fun updateCollectionTypes(expr: Receiver, newType: Type) {
    when (expr) {
        is CollectionAst -> expr.type = newType
        is MapCollection -> expr.type = newType

        is ExpressionInBrackets ->
            if (expr.expr is Receiver) {
                updateCollectionTypes(expr.expr, newType)
            }

        is MessageSendKeyword -> {
            expr.messages.forEach { msg ->
                when (msg) {
                    is KeywordMsg -> msg.args.forEach { arg ->
                        if (arg.keywordArg is Receiver) {
                            updateCollectionTypes(arg.keywordArg, newType)
                        }
                    }
                    else -> {}
                }
            }
        }
        is KeywordMsg -> {
            expr.args.forEach { arg ->
                if (arg.keywordArg is Receiver) {
                    updateCollectionTypes(arg.keywordArg, newType)
                }
            }
        }
        else -> {}
    }
}
