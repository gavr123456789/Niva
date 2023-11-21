package codogen

import frontend.parser.types.ast.VarDeclaration
import frontend.parser.types.ast.generateType
import frontend.typer.Type

fun VarDeclaration.generateVarDeclaration(): String {
    val valueCode = value.generateExpression()
    val valOrVar = if (!this.mutable) "val" else "var"
    val valueTypeAst = valueType
    val valueType = value.type
    val pkgOfType = if (valueType is Type.UserLike && valueType.isBinding) (valueType.pkg + ".") else ""
    val type = if (valueTypeAst == null) "" else
        ":$pkgOfType${valueTypeAst.generateType()}"

    return "$valOrVar $name$type = $valueCode"
}
