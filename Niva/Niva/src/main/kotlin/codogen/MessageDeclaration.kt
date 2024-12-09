package main.codogen

import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.Pragma
import frontend.resolver.Type
import main.frontend.parser.types.ast.*
import main.utils.appendnl
import main.utils.isGeneric

val kotlinKeywords = arrayOf( "if", "else", ) // "fun", "val", "var", "class"

fun String.ifKtKeywordAddBackTicks(): String {
    return if (kotlinKeywords.contains(this)) {
         "`$this`"
    } else this
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

fun MessageDeclaration.getGenericsFromMessageDeclaration(): Set<String> {
    // return type can be generic
    // receiver can be generic

    val genericsFromReceiverAndReturnType = mutableSetOf<String>()
    // from return type
    if (returnTypeAST != null) {
        val isThereUnresolvedTypeArgs = returnTypeAST.name.isGeneric()
        if (isThereUnresolvedTypeArgs) {
            // There can be resolved type args like box::Box::Int, then we don't need to add them
            genericsFromReceiverAndReturnType.add(returnTypeAST.name)
        }
    }

    val isThereUnresolvedTypeArgs = typeArgs.filter { it.isGeneric() }
    if (isThereUnresolvedTypeArgs.isNotEmpty()) {
        // There can be resolved type args like box::Box::Int, then we don't need to add them
        genericsFromReceiverAndReturnType.addAll(isThereUnresolvedTypeArgs)
    }


    val forTypeVal = forType
    if (forTypeVal is Type.UserLike && forTypeVal.typeArgumentList.isNotEmpty()) {
        genericsFromReceiverAndReturnType.addAll(forTypeVal.collectGenericParamsRecursively(mutableSetOf()))
    }

    return genericsFromReceiverAndReturnType
}

// fun ReceiverType^
fun MessageDeclaration.funGenerateReceiver(isStatic: Boolean = false) = buildString {
    append("fun ")
    // return type can be generic
    // receiver can be generic

    val genericsFromReceiverAndReturnType = getGenericsFromMessageDeclaration()

    if (genericsFromReceiverAndReturnType.isNotEmpty()) {
        append("<")
        append(genericsFromReceiverAndReturnType.joinToString(", "))
        append(">")
    }

    append(forTypeAst.generateType((forType as? Type.UserLike)?.emitName, generateGeneric = false))

    if (isStatic) {
        append(".Companion")
    }

    // if forType is generic
    val forType2 = forType
    if (forType2 is Type.UserLike && forType2.typeArgumentList.isNotEmpty() && !isStatic) {
        append("<")
//        val typeArgs = mutableListOf<String>()
//        typeArgs.addAll(forType2.typeArgumentList.map { it.name }.toSet())

//        forType2.typeArgumentList.forEach {
//            append(it.name)
//        }
        append(
            forType2.typeArgumentList.joinToString(", ") {
                it.toKotlinString(true)
            }
        )
        append(">")
    }
}

fun MessageDeclarationUnary.generateUnaryDeclaration(isStatic: Boolean = false) = buildString {
    append(funGenerateReceiver(isStatic))

    // fun Int.sas^() {...}
    append(".", name.ifKtKeywordAddBackTicks())

    append("(")
    pragmas.addInvisibleArgsToMethodDeclaration(emptyList(), this)
    append(")")

    returnTypeAndBodyPart(this@generateUnaryDeclaration, this)
}

fun operatorToString(x: String): String {
    return operators[x]!!
}

fun MessageDeclarationBinary.generateBinaryDeclaration(isStatic: Boolean = false) = buildString {

    append("operator ")
    append(funGenerateReceiver(isStatic))

    // receiver type end
    val operatorName = operatorToString(name)
    append(".", operatorName, "(", arg.name)
    // operator fun Int.plus^(x: Int) {}

    // args
    if (arg.typeAST != null) {
        append(": ", arg.typeAST.name)
    }

    pragmas.addInvisibleArgsToMethodDeclaration(listOf(arg), this)

    append(")")
    // operator fun int.sas(...)
    returnTypeAndBodyPart(this@generateBinaryDeclaration, this)
}

fun MutableList<Pragma>.addInvisibleArgsToMethodDeclaration(args: List<KeywordDeclarationArg>, builder: StringBuilder) {
    this.asSequence().filterIsInstance<KeyPragma>().filter { it.name == "arg" }.forEachIndexed { i, it ->
        if (args.isNotEmpty() || i > 0) builder.append(", ")
        val num = it.value.token.lexeme
        builder.append("__arg", num, ": String")
    }
}

fun MessageDeclarationKeyword.generateKeywordDeclaration(isStatic: Boolean = false) = buildString {
    append(funGenerateReceiver(isStatic))

    // fun Person^.sas() {}
    append(".", name.ifKtKeywordAddBackTicks(), "(")

    // Args
    val c = args.count() - 1
    args.forEachIndexed { i, arg ->
        append(arg.name())
        if (arg.typeAST != null) {
            append(": ", arg.typeAST.generateType((arg.type as? Type.UserLike)?.emitName))
            if (i != c) {
                append(", ")
            }
        }
    }

    // if ctArgs, add receiver and arg
    pragmas.addInvisibleArgsToMethodDeclaration(args, this)

    append(")")
    returnTypeAndBodyPart(this@generateKeywordDeclaration, this)
}


fun StaticBuilderDeclaration.generateBuilderDeclaration() = buildString {
    val st = this@generateBuilderDeclaration

    append("inline fun " )
    st.receiverType?.let {
        append(it.toKotlinString(true), ".")
    }
    append(st.name.ifKtKeywordAddBackTicks(), "(")

    // Args

    val args = st.msgDeclaration.args
    val c = args.count() - 1
    args.forEachIndexed { i, arg ->
        append(arg.name())
        if (arg.typeAST != null) {
            append(": ", arg.typeAST.generateType((arg.type as? Type.UserLike)?.emitName))
            if (i != c) {
                append(", ")
            }
        }
    }
    // default action
    val da = if (st.defaultAction != null) {
        "(" + st.defaultAction.inputList.first().type!!.toKotlinString(true) + ") -> Any"
    } else ""
//    if (da != null) {
        if (args.count() > 0) append(", ")
        val forType = forType!!
        append("build", ": ")
        append(forType.toKotlinString(true), ".")
        append("($da) -> Unit")
//    }
    append(")")

    returnTypeAndBodyPart(st, this)
}

private fun returnTypeAndBodyPart(
    messageDeclaration: MessageDeclaration,
    stringBuilder: StringBuilder
) {
    if (messageDeclaration.returnTypeAST != null) {
        stringBuilder.append(": ", messageDeclaration.returnType!!.toKotlinString(true))
    } else {
        // experimental infer return type
        stringBuilder.append(": ", messageDeclaration.returnType!!.toKotlinString(true))
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
    if (//messageDeclaration.body.count() == 1 &&
        messageDeclaration.isSingleExpression &&
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

fun appendPragmas(pragmas: List<Pragma>, builder: StringBuilder) {
    pragmas.forEach {
        if (!setOfPragmaNames.contains(it.name)) {
            builder.appendnl("@${it.name}")
        }
    }
}

fun MessageDeclaration.generateMessageDeclaration(isStatic: Boolean = false): String = buildString {
    appendPragmas(pragmas, this)
    val st = this@generateMessageDeclaration
    if (isInline) append("inline ")
    if (isSuspend) append("suspend ")


    append(
        when (st) {
            is ConstructorDeclaration -> generateConstructorDeclaration()
            is MessageDeclarationUnary -> generateUnaryDeclaration(isStatic)
            is MessageDeclarationBinary -> generateBinaryDeclaration(isStatic)
            is MessageDeclarationKeyword -> generateKeywordDeclaration(isStatic)
            is StaticBuilderDeclaration -> generateBuilderDeclaration()
        }
    )
}

fun ConstructorDeclaration.generateConstructorDeclaration() =
    this.msgDeclaration.generateMessageDeclaration(true)
