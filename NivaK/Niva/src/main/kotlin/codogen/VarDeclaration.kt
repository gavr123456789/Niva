package codogen

import frontend.parser.types.ast.VarDeclaration

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateExpression()
    return "val ${this.name} = $valueCode"
}
