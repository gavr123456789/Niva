package codogen

import frontend.parser.types.ast.VarDeclaration
import frontend.parser.types.ast.generateType

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateExpression()
    val valOrVar = if (!this.mutable) "val" else "var"
    val valueType2 = valueType
    val type = if (valueType2 == null) "" else
        ":${valueType2.generateType()}"

    return "$valOrVar $name$type = $valueCode"
}
