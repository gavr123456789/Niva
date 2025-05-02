package main.codogen

import main.frontend.parser.types.ast.CodeBlock

fun CodeBlock.generateCodeBlock(withTypeDeclaration: Boolean = true, putArgListInBrackets: Boolean = false) = buildString {
    // {x: Int, y: Int -> x + y}

    if (isSingle) {
        append(";")
    }

    append("{")

    if (putArgListInBrackets) append("(")
    // ^x: Int, ->
    inputList.forEach({ append(", ") }) {
        append(it.name)

        if (withTypeDeclaration) {
            append(": ")
            append(it.type!!.toKotlinString(true))

//            if (it.typeAST != null) {
//                append(it.typeAST.generateType())
//            } else {
//                append(it.type!!.toKotlinString(true))
//            }
        }

    }
    if (putArgListInBrackets) append(")")

    val isThereArgs = inputList.isNotEmpty()
    // generate single line lambda or not
    val statementsCode = if (statements.count() == 1) {
        append(if (isThereArgs) "-> " else "")
        codegenKt(statements, 0).removeSuffix("\n")
    } else {
        append(if (isThereArgs) "-> " else "", "\n")
        codegenKt(statements, 1)
    }
    append(statementsCode)



    append("}")
}
