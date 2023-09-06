package codogen

import frontend.parser.types.ast.CodeBlock

fun CodeBlock.generateCodeBlock() = buildString {
    // {x: Int, y: Int -> x + y}

    if (isSingle) {
        append(";")
    }

    append("{")

    // x: Int, ->
    inputList.forEach {
        append(it.name, ": ", it.type!!.name, ", ")
    }
    val isThereArgs = inputList.isNotEmpty()
    // generate single line lambda or not
    val statementsCode = if (statements.count() == 1) {
        append(if (isThereArgs) "-> " else "")
        codogenKt(statements, 0).removeSuffix("\n")
    } else {
        append(if (isThereArgs) "-> " else "", "\n")
        codogenKt(statements, 1)
    }
    append(statementsCode)



    append("}")
}
