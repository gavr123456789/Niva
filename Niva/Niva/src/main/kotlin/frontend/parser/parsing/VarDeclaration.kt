package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.VarDeclaration
import main.utils.RED
import main.utils.RESET

// если мы так и не встретили равно то мы идем дальше
fun Parser.varDeclaration(): VarDeclaration? {
    val savePoint = current
//    try {
        // skip mut
        val isMutable = match(TokenType.Mut)

        val tok = step()
        val typeOrEqual = step()

        val value: Expression
        val valueType: TypeAST?
        skipNewLinesAndComments()
        when (typeOrEqual.kind) {
            // x =^
            TokenType.Assign -> {
//                try {

                    val isNextReceiver = isNextSimpleReceiver()
                    value = if (isNextReceiver)
                        simpleReceiver()
                    else
                        expression(parseSingleIf = true)
                    valueType = null

//                } catch (e: Exception) {
//                    if (e.message?.startsWith("${RED}Error:$RESET Don't use pipe") == true ||
//                        e.message?.startsWith("${RED}Error:$RESET After") == true) {
//                        throw e
//                    }
//                    current = savePoint
//                    return null
//                }

            }
            // ::^int
            TokenType.DoubleColon -> {
                try {
                    valueType = parseTypeAST()
                    // x::int^ =
                    matchAssert(TokenType.Assign)
                    skipNewLinesAndComments()
                    val isNextReceiver = isNextSimpleReceiver()
                    value = if (isNextReceiver) simpleReceiver() else expression(parseSingleIf = true)

                } catch (e: Exception) {
                    if (e.message?.startsWith("${RED}Error:$RESET Don't use pipe") == true ||
                        e.message?.startsWith("${RED}Error:$RESET After") == true) {
                        throw e
                    }
                    current = savePoint
                    return null
                }

            }

            else -> peek().compileError("After ${peek(-1)} needed type or expression")
        }

        val result = VarDeclaration(tok, tok.lexeme, value, valueType, isMutable)
        return result
//    } catch (e: Exception) {
//        if (e.message?.startsWith("${RED}Error:$RESET Don't use pipe") == true ||
//            e.message?.startsWith("${RED}Error:$RESET After") == true) {
//            throw e
//        }
//        current = savePoint
//        return null
//    }

}
