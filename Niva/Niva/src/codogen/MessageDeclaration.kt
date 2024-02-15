package codogen

import frontend.parser.types.ast.*
import frontend.resolver.Type
import main.codogen.generateType
import main.utils.isGeneric


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
    append("fun ")
    if (returnTypeAST != null) {
        val isThereUnresolvedTypeArgs = returnTypeAST.name.isGeneric()
        if (isThereUnresolvedTypeArgs) {
            // There can be resolved type args like box::Box::Int, then we don't need to add them
            append("<")
            append(returnTypeAST.name)
            append(">")
        }
    }
    append(forTypeAst.generateType(false))
    if (isStatic) {
        append(".Companion")
    }
    // if forType is generic
    val forType2 = forType
    if (forType2 is Type.UserLike && forType2.typeArgumentList.isNotEmpty()) {
        append("<")
        val typeArgs = mutableListOf<String>()
        typeArgs.addAll(forType2.typeArgumentList.map { it.name }.toSet())

        forType2.typeArgumentList.forEach {
            append(it.name)
        }
        append(">")
    }


    val args = if (needCtArgs) {
        // for unary only receiver needed
        "(__arg0: String)"
    } else "()"

    append(".", name, args)
    bodyPart(this@generateUnaryDeclaration, this)
}

fun MessageDeclarationBinary.generateBinaryDeclaration(isStatic: Boolean = false) = buildString {
    fun operatorToString(x: String): String {
        return operators[x]!!
    }

    //            operator fun Int.plus(increment: Int): Counter {
    //              this.echo()
    //            }

    append("operator fun ")
    append(forTypeAst.generateType())
    if (isStatic) {
        append(".Companion")
    }
    val operatorName = operatorToString(name)
    append(".", operatorName, "(", arg.name)

    if (arg.type != null) {
        append(": ", arg.type.name)
    }

    // if ctArgs, add receiver and arg
    if (needCtArgs) {
        append(", __arg0: String, __arg1: String")
    }

    append(")")
    // operator fun int.sas(...)
    bodyPart(this@generateBinaryDeclaration, this)
}

fun MessageDeclarationKeyword.generateKeywordDeclaration(isStatic: Boolean = false) = buildString {

    // if this is the constructor, then method on Companion
    append("fun ")

    val isThereUnresolvedTypeArgs = typeArgs.filter { it.isGeneric() }
    if (isThereUnresolvedTypeArgs.isNotEmpty()) {
        // There can be resolved type args like box::Box::Int, then we don't need to add them
        append("<")
        append(isThereUnresolvedTypeArgs.toSet().joinToString(", "))
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

    // if ctArgs, add receiver and arg
    if (needCtArgs) {
        val argsArgs = args.mapIndexed { i, it -> "__arg${i + 1}: String" }.joinToString(", ")
        append(", __arg0: String, $argsArgs")
    }

    append(")")


    bodyPart(this@generateKeywordDeclaration, this)
}


private fun bodyPart(
    messageDeclaration: MessageDeclaration,
    stringBuilder: StringBuilder
) {
    if (messageDeclaration.returnTypeAST != null) {
        stringBuilder.append(": ", messageDeclaration.returnTypeAST.generateType())
    }
    if (messageDeclaration.body.isEmpty()) {
        stringBuilder.append(" { }\n")
        return
    }

    val firstBodyStatement = messageDeclaration.body[0]
    val isNotSetter by lazy {
        !(firstBodyStatement is MessageSendKeyword && firstBodyStatement.messages[0] is KeywordMsg && (firstBodyStatement.messages[0] as KeywordMsg).kind == KeywordLikeType.Setter)
    }
    val isControlFlowStatement by lazy {
        (firstBodyStatement is ControlFlow && (firstBodyStatement.kind == ControlFlowKind.Statement || firstBodyStatement.kind == ControlFlowKind.StatementTypeMatch))
    }
    if (messageDeclaration.body.count() == 1 &&
        firstBodyStatement is Expression &&
        !isControlFlowStatement &&
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
    if (isInline) append("inline ")
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
