package main.codogen

import frontend.resolver.Type
import main.frontend.parser.types.ast.VarDeclaration

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateExpression()
    val valOrVar = if (!this.mutable) "val" else "var"
    val valueTypeAst = valueTypeAst
    val valueType = value.type
    val pkgOfType = if (valueType is Type.UserLike && valueType.isBinding) (valueType.pkg + ".") else ""
    val type = if (valueTypeAst == null) "" else
        ":$pkgOfType${valueTypeAst.generateType()}"

    return "$valOrVar $name$type = $valueCode"
}
