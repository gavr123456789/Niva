package main.codogen

import frontend.parser.types.ast.KeyPragma
import frontend.parser.types.ast.Pragma
import frontend.resolver.Type
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.appendnl
import main.utils.isGeneric


fun String.ifKtKeywordAddBackTicks(): String =
    when (this) {
        "do", "val", "var", "class", "in", "for", "throw", "continue" -> "`$this`"
        else -> this
    }

fun MessageDeclaration.getGenericsFromMessageDeclaration(): Set<String> {
    // return type can be generic
    // receiver can be generic
    // args can be generic

    val result = mutableSetOf<String>()
    // from return type
    if (returnTypeAST != null) {
        val isThereUnresolvedTypeArgs = returnTypeAST.name.isGeneric()
        if (isThereUnresolvedTypeArgs) {
            // There can be resolved type args like box::Box::Int, then we don't need to add them
            result.add(returnTypeAST.name)
        }
    }

    val isThereUnresolvedTypeArgs = typeArgs.filter { it.isGeneric() }
    if (isThereUnresolvedTypeArgs.isNotEmpty()) {
        // There can be resolved type args like box::Box::Int, then we don't need to add them
        result.addAll(isThereUnresolvedTypeArgs)
    }

    // from args
    this.collectGenericsFromArgs(result)

    // from receiver
    val forTypeVal = forType
    if (forTypeVal is Type.UserLike && forTypeVal.typeArgumentList.isNotEmpty()) {
        forTypeVal.collectGenericParamsRecursively(mutableSetOf())
        val q = result.reversed().distinctBy { string -> string.first() }
        result.clear()
        result.addAll(q)
    }




    return result
}

fun MessageDeclaration.collectGenericsFromArgs (set: MutableSet<String>) {
    val x = this
    when (x) {
        is ConstructorDeclaration -> x.msgDeclaration.collectGenericsFromArgs(set)
        is MessageDeclarationBinary -> {
            val type = x.arg.type
            if (type is Type.UserLike && type.typeArgumentList.isNotEmpty()) {
                type.collectGenericParamsRecursively(set)
            }
        }
        is MessageDeclarationKeyword -> {
            x.args.forEach { arg ->
                val type = arg.type
                if (type is Type.UserLike && type.typeArgumentList.isNotEmpty()) {
                    type.collectGenericParamsRecursively(set)
                }
            }
        }
        is MessageDeclarationUnary -> {}
        is StaticBuilderDeclaration -> {x.msgDeclaration.collectGenericsFromArgs(set)}
    }
}

// fun ReceiverType^
fun MessageDeclaration.funGenerateReceiver(isStatic: Boolean = false) = buildString {
    append("fun ")
    // return type can be generic
    // receiver can be generic

    val genericsFromReceiverAndReturnType = getGenericsFromMessageDeclaration()

    if (genericsFromReceiverAndReturnType.isNotEmpty()) {
        append("<")
        if (isInline)
            append("reified ",genericsFromReceiverAndReturnType.joinToString(", reified "))
        else
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
        append(
            forType2.typeArgumentList.joinToString(", ") {
                it.toKotlinString(true)
            }
        )
        append(">")
    }
}

fun MessageDeclarationUnary.generateUnaryDeclaration(isStatic: Boolean = false) = buildString {
    // no need to generate toString method extension, we generate it from MessageDeclaration.kt 186
    if (name == "toString") {
        return@buildString
    }

    append(funGenerateReceiver(isStatic))

    // fun Int.sas^() {...}
    append(".", name.ifKtKeywordAddBackTicks())

    append("(")
    pragmas.addInvisibleArgsToMethodDeclaration(emptyList(), this)
    append(")")

    returnTypeAndBodyPart(this@generateUnaryDeclaration, this)
}



fun MessageDeclarationBinary.generateBinaryDeclaration(isStatic: Boolean = false) = buildString {

    val nivaEqualityName = nivaEqualityMethodName(name)
    if (nivaEqualityName == null) {
        append("operator ")
    }
    append(funGenerateReceiver(isStatic))

    // receiver type end
    val operatorName = nivaEqualityName ?: operatorToString(name, token)
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
    val seq = this.asSequence()
    val keyPragmas = seq.filterIsInstance<KeyPragma>()
    val nameOfTheArgPragmas = keyPragmas.filter { it.name == "arg" }
//    val callerPlacePragma = keyPragmas.filter { it.name == "callerPlace" }

    nameOfTheArgPragmas.forEachIndexed { i, it ->
        if (args.isNotEmpty() || i > 0) builder.append(", ")
        val num = it.value.token.lexeme
        builder.append("__arg", num, ": String")
    }

//    callerPlacePragma.forEachIndexed { i, it ->
//        val allArgs = nameOfTheArgPragmas.count() + args.count()
//
//        if (allArgs != 0 || i > 0) builder.append(", ")
////        val codePlace = it.value.token.prettyCodePlace()
//        builder.append("callerPlace", ": String")
//    }
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
            val type = arg.type
            val name = type?.toKotlinString(true)
                ?: arg.typeAST.generateType(null)
            append(": ", name)
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
    appendPragmas(pragmas, this)

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
    msgDecl: MessageDeclaration,
    stringBuilder: StringBuilder
) {
    if (msgDecl.returnTypeAST != null) {
        stringBuilder.append(": ", msgDecl.returnType!!.toKotlinString(true))
    } else {
        // experimental infer return type
        stringBuilder.append(": ", msgDecl.returnType!!.toKotlinString(true))
    }

    val debug = msgDecl.pragmas.find { it.name == "debug" }
    // add each arg as identifier for devmode 2
    if (debug != null && msgDecl !is ConstructorDeclaration) {
        if (msgDecl is MessageDeclarationKeyword) {
            msgDecl.args.forEach { arg ->
                val thisExpr = IdentifierExpr(arg.localName ?: arg.name, token = arg.tok).also {
                    it.isInlineRepl = true // args
                }
                msgDecl.body.addFirst(thisExpr)
            }
        }
        // add receiver as this identifier for devmode 2
        val thisExpr = IdentifierExpr("this", token = msgDecl.forTypeAst.token).also {
            it.isInlineRepl = true // this
        }
        msgDecl.body.addFirst(thisExpr)
    }
    generateBody(msgDecl, stringBuilder)
}

fun generateBody(
    messageDeclaration: MessageDeclaration,
    sb: StringBuilder
) {
    if (messageDeclaration.body.isEmpty()) {
        sb.append(" { }\n")
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
        sb.append(" = ", codegenKt(messageDeclaration.body, 0))
    } else {
        sb.append(" {\n")
        sb.append(codegenKt(messageDeclaration.body, 1))
        sb.append("}\n")
    }
}

fun appendPragmas(pragmas: List<Pragma>, builder: StringBuilder) {
    pragmas.forEach {
        if (!builtInPragmas.contains(it.name)) {
            builder.appendnl("@${it.name}")
        }
    }
}

fun MessageDeclaration.generateMessageDeclaration(isStatic: Boolean = false, needPragmaGeneration: Boolean = true): String = buildString {
    if (needPragmaGeneration)
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
    this.msgDeclaration.generateMessageDeclaration(true, false)

fun operatorToString(operator: String, token: Token?): String {
    return when (operator) {
        "+" -> "plus"
        "-" -> "minus"
        "*" -> "times"
        "/" -> "div"
        "%" -> "rem"
        ".." -> "rangeTo"
        "==" -> "equals"
        "!=" -> "notEquals"
        ">" -> "gt"
        "<" -> "lt"
        ">=" -> "gte"
        "<=" -> "lte"
        "||" -> "or"
        "&&" -> "and"
        "apply" -> "invoke"
        else -> {
            token?.compileError("Undefined operator: $operator") ?: operator
        }
    }
}

fun nivaEqualityMethodName(operator: String): String? =
    when (operator) {
        "==" -> "equal_niva"
        "!=" -> "not_equal_niva"
        else -> null
    }
