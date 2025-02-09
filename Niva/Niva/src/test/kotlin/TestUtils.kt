package main.test

import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import main.frontend.parser.types.ast.Statement
import main.lex
import java.io.File

fun getAstTest(source: String): List<Statement> {
    val fakeFile = File("Niva.iml")
    val tokens = lex(source, fakeFile)
    val parser = Parser(file = fakeFile, tokens = tokens, source = source)
    return parser.statements()
}
