package codogen

import frontend.parser.types.ast.VarDeclaration

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateKotlinCode()
    return "val ${this.name} = $valueCode"
}
