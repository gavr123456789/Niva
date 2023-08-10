package codogen

import frontend.parser.types.ast.VarDeclaration

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateExpression()
    val valOrVar = if (!this.mutable) "val" else "var"
    return "$valOrVar $name = $valueCode"
}
