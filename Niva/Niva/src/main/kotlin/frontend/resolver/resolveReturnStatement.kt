package frontend.resolver

import main.frontend.meta.compileError
import main.frontend.parser.types.ast.CodeBlock
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.ReturnStatement
import main.utils.RESET
import main.utils.YEL

fun Resolver.resolveReturnStatement(statement: ReturnStatement, previousAndCurrentScope: MutableMap<String, Type>) {
    val isInsideLambdaCodeBlock = stack.any { it is CodeBlock && !it.isStatement }
    if (!isInsideLambdaCodeBlock) {
        wasThereTopLevelReturn = true
    }

    val expr = statement.expression
    if (expr != null) {
        resolveSingle((expr), previousAndCurrentScope, statement)
        if (expr.type == null) {
            throw Exception("Cant infer type of return statement on line: ${expr.token.line}")
        }
    }
    val unit = Resolver.defaultTypes[InternalTypes.Unit]!!
    val typeOfCurrentBlock = if (expr == null) unit else expr.type!!

    ///
    val previousReturnType = wasThereReturn
    val resolvingMessageDeclaration2 = resolvingMessageDeclaration

    if (resolvingMessageDeclaration2 != null && resolvingMessageDeclaration2.returnTypeAST == null) {
        resolvingMessageDeclaration2.returnType = if (previousReturnType != null) {
            val g = findGeneralRoot(previousReturnType, typeOfCurrentBlock)
                ?: statement.token.compileError("Cant find general root between return types $YEL$typeOfCurrentBlock$RESET and $YEL$previousReturnType$RESET")
            g
        } else {
            typeOfCurrentBlock
        }
    }


    wasThereReturn = typeOfCurrentBlock
    val root = this.resolvingMessageDeclaration
    if (root != null) {
        val realReturn = wasThereReturn
        val returnType = root.returnType

        val fromDB = typeTable[realReturn?.name]
        if (fromDB != null && realReturn != null && realReturn.parent == null && fromDB.parent != null) {
            realReturn.parent = fromDB.parent
        }
        val fromDB2 = typeTable[returnType?.name]
        if (fromDB2 != null && returnType != null && returnType.parent == null && fromDB2.parent != null) {
            returnType.parent = fromDB2.parent
        }


        if (realReturn != null && returnType != null &&
            !compare2Types(
                returnType,
                realReturn,
                root.returnTypeAST?.token ?: statement.token,
                unpackNullForSecond = false,
                isOut = true,
                compareParentsOfBothTypes = false,
                compareMutability = false,
                unpackNullForFirst = true
            )
            // тут надо заюзать поиск общих предков
            // не надо, он есть в компаре 2 тайпс для первого
//                    findGeneralRootMany(listOf(returnType, realReturn), root.token) == null
        ) {
            // 1) когда out true комперить паренты второго а не первого, и поменять сравнение в ретурнт типах
            compare2Types(
                returnType,
                realReturn,
                root.returnTypeAST?.token ?: statement.token,
//                unpackNullForSecond = true,
                isOut = true,
                compareParentsOfBothTypes = false,
                compareMutability = false,
                unpackNullForFirst = true
            )
            // если задекларирован очень конкретный, а мы возвращаем более общзий то этот ошибка
            statement.token.compileError("Return type defined: ${YEL}$returnType${RESET} but real type returned: ${YEL}$realReturn")
        }

        if (returnType?.isMutable == true && realReturn?.isMutable == false) {
            statement.token.compileError("Return type is mutable ${YEL}$returnType${RESET}, but ur type is not: ${YEL}$realReturn")
        }
    }
}
