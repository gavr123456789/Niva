package main.frontend.parser.parsing

import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.parsing.expression
import frontend.parser.parsing.match
import frontend.parser.parsing.matchAfterSkip
import frontend.parser.parsing.peek
import frontend.parser.parsing.skipNewLinesAndComments
import frontend.parser.parsing.step
import main.frontend.meta.TokenType
import main.frontend.meta.isIdentifier
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.DestructureBinding
import main.frontend.parser.types.ast.IdentifierExpr

// {a b} = expr
// {local = field} = expr
fun Parser.tryDestructiveAssign(): DestructingAssign? {
    if (!check(TokenType.OpenBrace)) return null

    val savePosition = current
    val openBrace = step()

    val bindings = mutableListOf<DestructureBinding>()
    var invalid = false

    while (true) {
        skipNewLinesAndComments()
        val nextTok = peek()
        if (!nextTok.isIdentifier())
            break
        val localTok = step()
        var fieldTok = localTok
        if (matchAfterSkip(TokenType.Assign)) {
            val fieldMaybe = peek()
            if (fieldMaybe.isIdentifier()) {
                fieldTok = step()
            } else {
                invalid = true
                break
            }
        }
        bindings.add(
            DestructureBinding(
                localName = IdentifierExpr(localTok.lexeme, listOf(localTok.lexeme), null, localTok),
                fieldName = IdentifierExpr(fieldTok.lexeme, listOf(fieldTok.lexeme), null, fieldTok),
            )
        )
        matchAfterSkip(TokenType.Comma)
    }

    if (invalid) {
        current = savePosition
        return null
    }

    skipNewLinesAndComments()

    if (!match(TokenType.CloseBrace)) {
        current = savePosition
        return null
    }

    if (bindings.isEmpty()) {
        current = savePosition
        return null
    }

    match("!")
    skipNewLinesAndComments()

    if (!match(TokenType.Assign)) {
        current = savePosition
        return null
    }

    skipNewLinesAndComments()
    val value = expression(parseSingleIf = true, dot = true)

    return DestructingAssign(
        token = openBrace,
        bindings = bindings,
        value = value,
    )
}
