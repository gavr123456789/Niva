package main.codogen

import main.frontend.parser.types.ast.StaticBuilder
import main.utils.appendnl

fun generateBuilderCall(builder: StaticBuilder) = buildString {
    val defaultActionName = "defaultAction"
    val st = builder

    if (builder.receiverOfBuilder != null) {
        append(builder.receiverOfBuilder.generateExpression(), ".")
    }

    // add name
    append(st.name)

    // args
    if (builder.args.isNotEmpty())
        append("(",builder.args.joinToString(", ") { it.keywordArg.toString() }, ") ")
    append("{")
    if (builder.defaultAction != null)
        append(" $defaultActionName ->\n")
    // add body, but with "defaultAction" arg

    // TODO, just replace statements that needed to call with defaultAction in resolver
    val generatorKt = GeneratorKt()

    st.statements.forEach {
        if (it in st.expressions) {
            append("\t", defaultActionName, "(", generatorKt.generateKtStatement(it, 0), ")", "\n")
        } else
            appendnl(generatorKt.generateKtStatement(it, 1))
    }

    append("}\n\n")
}
