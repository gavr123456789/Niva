package codogen

import frontend.parser.types.ast.*
import frontend.typer.Type


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

fun MessageDeclarationUnary.generateUnaryDeclaration(isStatic: Boolean = false) = buildString {
    // fun Int.sas(): unit {
    //   this.echo()
    // }
    append("fun ")
    // TODO also every argument can be generic, not only return type
    if (returnType != null) {
        val isThereUnresolvedTypeArgs = returnType.name.count() == 1 && returnType.name[0].isUpperCase()
        if (isThereUnresolvedTypeArgs) {
            // There can be resolved type args like box::Box::Int, then we don't need to add them
            append("<")
            append(returnType.name)
            append(">")
        }
    }
    append(forTypeAst.name)
    if (isStatic) {
        append(".Companion")
    }
    // if forType is generic
    val forType2 = forType
    if (forType2 is Type.UserLike && forType2.typeArgumentList.isNotEmpty()) {
        append("<")
        val typeArgs = mutableListOf<String>()
        typeArgs.addAll(forType2.typeArgumentList.map { it.name })

        forType2.typeArgumentList.forEach {
            append(it.name)
        }
        append(">")
    }



    append(".", name, "()")
    bodyPart(this@generateUnaryDeclaration, this)
}

fun MessageDeclarationBinary.generateBinaryDeclaration(isStatic: Boolean = false) = buildString {
    fun operatorToString(x: String): String {
        return operators[x]!!
    }

    //            operator fun Int.plus(increment: Int): Counter {
    //              this.echo()
    //            }

    append("operator fun ", forTypeAst.name)
    if (isStatic) {
        append(".Companion")
    }
    val operatorName = operatorToString(name)
    append(".", operatorName, "(", arg.name)

    if (arg.type != null) {
        append(": ", arg.type.name)
    }
    append(")")
    // operator fun int.sas(...)
    bodyPart(this@generateBinaryDeclaration, this)
}

fun MessageDeclarationKeyword.generateKeywordDeclaration(isStatic: Boolean = false) = buildString {

    // if this is the constructor, then method on Companion
    append("fun ")

    val isThereUnresolvedTypeArgs = typeArgs.filter { it.count() == 1 && it[0].isUpperCase() }
    if (isThereUnresolvedTypeArgs.isNotEmpty()) {
        // There can be resolved type args like box::Box::Int, then we don't need to add them
        append("<")
        append(isThereUnresolvedTypeArgs.joinToString(", "))
        append(">")
    }

    append(forTypeAst.generateType())
    if (isStatic) {
        append(".Companion")
    }
    append(".", name, "(")

    val c = args.count() - 1
    args.forEachIndexed { i, arg ->
        append(arg.name())
        if (arg.type != null) {
            append(": ", arg.type.generateType())
            if (i != c) {
                append(", ")
            }
        }
    }

    append(")")


    bodyPart(this@generateKeywordDeclaration, this)
}


private fun bodyPart(
    messageDeclaration: MessageDeclaration,
    stringBuilder: StringBuilder
) {
    if (messageDeclaration.returnType != null) {
        stringBuilder.append(": ", messageDeclaration.returnType.generateType())
    }
    if (messageDeclaration.body.isEmpty()) {
        stringBuilder.append(" { }\n")
        return
    }

    val firstBodyStatement = messageDeclaration.body[0]
    val isNotSetter =
        !(firstBodyStatement is MessageSendKeyword && firstBodyStatement.messages[0] is KeywordMsg && (firstBodyStatement.messages[0] as KeywordMsg).kind == KeywordLikeType.Setter)

    if (messageDeclaration.body.count() == 1 &&
        firstBodyStatement !is VarDeclaration &&
        firstBodyStatement !is Assign &&
        firstBodyStatement !is ReturnStatement &&
        isNotSetter
    ) {
        stringBuilder.append(" = ", codegenKt(messageDeclaration.body, 0))
    } else {
        stringBuilder.append(" {\n")
        stringBuilder.append(codegenKt(messageDeclaration.body, 1))
        stringBuilder.append("}\n")
    }
}

fun MessageDeclaration.generateMessageDeclaration(isStatic: Boolean = false): String = buildString {
    append(
        when (this@generateMessageDeclaration) {
            is ConstructorDeclaration -> generateConstructorDeclaration()
            is MessageDeclarationUnary -> generateUnaryDeclaration(isStatic)
            is MessageDeclarationBinary -> generateBinaryDeclaration(isStatic)
            is MessageDeclarationKeyword -> generateKeywordDeclaration(isStatic)
        }
    )
}

fun ConstructorDeclaration.generateConstructorDeclaration() =
    this.msgDeclaration.generateMessageDeclaration(true)
