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


fun markCollectionsAsMutable(expr: Receiver) {
    when (expr) {
        is CollectionAst -> expr.isMutableCollection = true
        is MapCollection -> expr.isMutable = true
        is ExpressionInBrackets -> markCollectionsAsMutable(expr.expr as Receiver)
        is MessageSendKeyword -> {
            markCollectionsAsMutable(expr.receiver)
            expr.messages.forEach { msg ->
                when (msg) {
                    is KeywordMsg -> msg.args.forEach { arg ->
                        if (arg.keywordArg is Receiver) {
                            markCollectionsAsMutable(arg.keywordArg)
                        }
                    }
                    is UnaryMsg -> markCollectionsAsMutable(msg.receiver)
                    else -> {}
                }
            }
        }
        is KeywordMsg -> {
            markCollectionsAsMutable(expr.receiver)
            expr.args.forEach { arg ->
                if (arg.keywordArg is Receiver) {
                    markCollectionsAsMutable(arg.keywordArg)
                }
            }
        }
        else -> {}
    }
}