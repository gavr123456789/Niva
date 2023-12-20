package codogen

import frontend.parser.types.ast.CodeBlock
import main.codogen.generateType

fun CodeBlock.generateCodeBlock() = buildString {
    // {x: Int, y: Int -> x + y}

    if (isSingle) {
        append(";")
    }

    append("{")

    // x: Int, ->
    inputList.forEach {
        append(it.name, ": ")
        if (it.typeAST != null) {
            append(it.typeAST.generateType())
        } else {
            append(it.type!!.name)
        }
        append(", ")
    }
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
