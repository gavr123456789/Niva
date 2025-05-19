package main.codogen

import frontend.resolver.Type
import main.frontend.parser.types.ast.VarDeclaration
import main.utils.GlobalVariables

fun VarDeclaration.generateVarStatement(): String {
    val valueCode = value.generateExpression()

    val valOrVar = if (!this.mutable) "val" else "var"
    val valueTypeAst = valueTypeAst
    val valueType = value.type
    val pkgOfType = if (valueType is Type.UserLike && valueType.isBinding) (valueType.pkg + ".") else ""
    val type = if (valueTypeAst == null) "" else
        ":$pkgOfType${valueType!!.toKotlinString(false)}"
    //"\n//@ ", tok.file.name, ":::", tok.line, "\n"
    val debugIfNeeded = if (GlobalVariables.needStackTrace)
        "\n//@ ${token.file.name}:::${token.line}\n"
    else ""



    return "$debugIfNeeded$valOrVar ${name.ifKtKeywordAddBackTicks()}$type = $valueCode"
}
