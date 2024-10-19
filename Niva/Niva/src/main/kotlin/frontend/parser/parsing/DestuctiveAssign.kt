package main.frontend.parser.parsing

import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.parsing.expression
import frontend.parser.parsing.matchAssert
import frontend.parser.parsing.skipNewLinesAndComments
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.ListCollection

fun Parser.tryDestructiveAssign(): DestructingAssign? {
    val f = simpleReceiver()
    if (f is ListCollection && this.check(TokenType.Assign)) {
        val destructingAssign = destructiveAssignParse(f)
        return destructingAssign
    } else return null
}

fun Parser.destructiveAssignParse(q: ListCollection): DestructingAssign {
//    val q = (if (tree.count() > 0 && tree.last() is ListCollection ) {
//        tree.removeLast() as? ListCollection
//    } else this.lastListCollection)
//        ?: tokForError.compileError("Parsing error, expected = after collection {} for destructing assign")
//    lastListCollection = null

    skipNewLinesAndComments()
    matchAssert(TokenType.Assign)
    skipNewLinesAndComments()

    val f = expression(parseSingleIf = true, dot = true)
//    val f = matchAssert(TokenType.Identifier)
    val names = q.initElements.map {
        if (it !is IdentifierExpr) {
            it.token.compileError("In destruction you need to list identifiers")
        } else {
            it
        }
    }


    val destructingAssign = DestructingAssign(
        token = q.token,
        names = names,
        value = f//IdentifierExpr(f.lexeme, listOf(f.lexeme), null, f),
    )
    return destructingAssign
}
