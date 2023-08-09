package codogen

import frontend.parser.types.ast.*

fun MessageDeclarationUnary.generateUnaryDeclaration() = buildString {
    //            fun Int.sas(): unit {
    //              this.echo()
    //            }
    append("fun ", forType.name, ".", name, "()")
    bodyPart(this@generateUnaryDeclaration, this)
}

val operators = hashMapOf(
    "+" to "plus",
    "-" to "minus",
    "*" to "times",
    "/" to "div",
    "%" to "rem",
    ".." to "rangeTo",

    "%" to "contains",

    "+=" to "plusAssign",
    "-=" to "minusAssign",
    "*=" to "timesAssign",
    "/=" to "divAssign",
    "%=" to "remAssign",

    "==" to "equals",
    "!=" to "equals",

    ">" to "compareTo",
    "<" to "compareTo",
    ">=" to "compareTo",
    "<=" to "compareTo",

    "<-=" to "getValue",
    "=->" to "setValue",

    "apply" to "invoke",
)

fun MessageDeclarationBinary.generateBinaryDeclaration() = buildString {
    fun operatorToString(x: String): String {
        return operators[x]!!
    }

    //            operator fun Int.plus(increment: Int): Counter {
    //              this.echo()
    //            }

    append("operator fun ", forType.name, ".", operatorToString(name), "(", arg.name)
    if (arg.type != null) {
        append(": ", arg.type.name)
    }
    append(")")
    // operator fun int.sas(...)
    bodyPart(this@generateBinaryDeclaration, this)
}

fun MessageDeclarationKeyword.generateKeywordDeclaration() = buildString {
    //            fun Int.fromTo(x: Int, y: Int): Counter {
    //              this.echo()
    //            }
    append("fun ", forType.name, ".", name, "(")
    val c = args.count() - 1
    args.forEachIndexed { i, arg ->
        append(arg.name())
        if (arg.type != null) {
            append(": ", arg.type.toKotlinStr())
            if (i != c) {
                append(", ")
            }
        }
    }

    append(")")
    // operator fun int.sas(...)
    bodyPart(this@generateKeywordDeclaration, this)
}


private fun bodyPart(
    messageDeclaration: MessageDeclaration,
    stringBuilder: StringBuilder
) {
    if (messageDeclaration.returnType != null) {
        stringBuilder.append(": ", messageDeclaration.returnType.name)
    }

    if (messageDeclaration.body.count() == 1) {
        stringBuilder.append(" = ", codogenKt(messageDeclaration.body, 0))
    } else {
        stringBuilder.append(" {\n")
        stringBuilder.append(codogenKt(messageDeclaration.body, 1))
        stringBuilder.append("}\n")
    }
}
