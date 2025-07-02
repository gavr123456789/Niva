package frontend.resolver

import main.frontend.meta.compileError
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.ReturnStatement
import main.utils.RESET
import main.utils.YEL

fun Resolver.resolveReturnStatement(statement: ReturnStatement, previousAndCurrentScope: MutableMap<String, Type>) {
    val expr = statement.expression
    if (expr != null) {
        resolveSingle((expr), previousAndCurrentScope, statement)
        if (expr.type == null) {
            throw Exception("Cant infer type of return statement on line: ${expr.token.line}")
        }
    }
    val unit = Resolver.defaultTypes[InternalTypes.Unit]!!
    val typeOfReturnExpr = if (expr == null) unit else expr.type!!

    ///
    val previousReturnType = wasThereReturn
    val resolvingMessageDeclaration2 = resolvingMessageDeclaration

    if (resolvingMessageDeclaration2 != null && resolvingMessageDeclaration2.returnTypeAST == null) {
        if (previousReturnType != null) {
            val g = findGeneralRoot(previousReturnType, typeOfReturnExpr)
                ?: statement.token.compileError("Cant find general root between return types $YEL$typeOfReturnExpr$RESET and $YEL$previousReturnType$RESET")
            resolvingMessageDeclaration2.returnType = g
        } else {
            resolvingMessageDeclaration2.returnType = typeOfReturnExpr
        }
    }


    wasThereReturn = typeOfReturnExpr
    val root = this.resolvingMessageDeclaration
    if (root != null) {
        val realReturn = wasThereReturn
        val returnType = root.returnType

        if (realReturn != null && returnType != null &&
            !compare2Types(
                returnType,
                realReturn,
                root.returnTypeAST?.token ?: statement.token,
                unpackNullForFirst = true,
                isOut = true,
                compareParentsOfBothTypes = false
            )
            // тут надо заюзать поиск общих предков
            // не надо, он есть в компаре 2 тайпс для первого
//                    findGeneralRootMany(listOf(returnType, realReturn), root.token) == null
        ) {
            statement.token.compileError("Return type defined: ${YEL}$returnType${RESET} but real type returned: ${YEL}$realReturn")
        }
    }
}