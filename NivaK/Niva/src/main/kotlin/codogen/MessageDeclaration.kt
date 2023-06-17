package codogen

import frontend.parser.types.ast.MessageDeclarationBinary
import frontend.parser.types.ast.MessageDeclarationKeyword
import frontend.parser.types.ast.MessageDeclarationUnary

typealias int = Int

fun int.sas() {

}

fun MessageDeclarationUnary.generateUnaryDeclaration() = buildString {
    //            fun Int.sas(): unit {
    //              this.echo()
    //            }
    append("fun ")
    append(forType.name)
    append(".")
    append(name)
    // fun int.sas
    append("()")
    if (returnType != null) {
        append(": ", returnType.name)
    }
    append(" {\n")
    // fun int.sas() {\n
    append(codogenKt(body, 1))
    append("}\n")
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
    append("operator fun ")
    append(forType.name)
    append(".")
    append(operatorToString(name))
    // operator fun int.sas
    append("(")
    append(arg.name)
    if (arg.type != null) {
        append(": ", arg.type.name)
    }
    append(")")
    // operator fun int.sas(...)
    if (returnType != null) {
        append(": ", returnType.name)
    }
    append(" {\n")
    // fun int.sas() {\n
    append(codogenKt(body, 1))
    append("}\n")
}

fun MessageDeclarationKeyword.generateKeywordDeclaration() = buildString {
    fun operatorToString(x: String): String {
        return operators[x]!!
    }

    //            fun Int.fromTo(x: Int, y: Int): Counter {
    //              this.echo()
    //            }
    append("fun ")
    append(forType.name)
    append(".")
    append(name)
    // operator fun int.sas
    append("(")
    val c = args.count() - 1
    args.forEachIndexed { i, arg ->
        append(arg.localName)
        if (arg.type != null) {
            append(": ", arg.type.name)
            if (i != c) {
                append(", ")
            }
        }
    }

    append(")")
    // operator fun int.sas(...)
    if (returnType != null) {
        append(": ", returnType.name)
    }
    append(" {\n")
    // fun int.sas() {\n
    append(codogenKt(body, 1))
    append("}\n")
}

