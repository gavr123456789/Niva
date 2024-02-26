package main.frontend.parser.parsing

import frontend.parser.parsing.*
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.VarDeclaration


fun Parser.varDeclaration(): VarDeclaration {
    // skip mut
    val isMutable = match(TokenType.Mut)

    val tok = this.step()
    val typeOrEqual = step()

    val value: Expression
    val valueType: TypeAST?
    skipNewLinesAndComments()
    when (typeOrEqual.kind) {
        // x =^
        TokenType.Assign -> {
            val isNextReceiver = isNextSimpleReceiver()
            value = if (isNextReceiver) simpleReceiver() else expression(parseSingleIf = true)
            valueType = null
        }
        // ::^int
        TokenType.DoubleColon -> {
            valueType = parseType()
            // x::int^ =
            match(TokenType.Assign)
            skipNewLinesAndComments()
            val isNextReceiver = isNextSimpleReceiver()
            value = if (isNextReceiver) simpleReceiver() else expression(parseSingleIf = true)

        }

        else -> peek().compileError("after ${peek(-1)} needed type or expression")
    }

    val result = VarDeclaration(tok, tok.lexeme, value, valueType, isMutable)
    return result
}
