package main.frontend.typer.project

import codogen.addToGradleDependencies
import frontend.meta.compileError
import frontend.parser.types.ast.KeywordMsg
import frontend.parser.types.ast.ListCollection
import frontend.parser.types.ast.LiteralExpression
import frontend.parser.types.ast.MessageSend
import frontend.resolver.*
import frontend.util.removeDoubleQuotes

fun Resolver.resolveProjectKeyMessage(statement: MessageSend) {
    // add to the current project
    assert(statement.messages.count() == 1)
    val keyword = statement.messages[0] as KeywordMsg

    keyword.args.forEach {

        when (it.keywordArg) {
            is LiteralExpression.StringExpr -> {
                val substring = it.keywordArg.token.lexeme.removeDoubleQuotes()
                when (it.name) {
                    "name" -> changeProject(substring, statement.token)
                    "package" -> changePackage(substring, statement.token)
                    "protocol" -> changeProtocol(substring)
                    "use" -> usePackage(substring)
                    "import" -> usePackage(substring, true)
                    "target" -> changeTarget(substring, statement.token)
                    "mode" -> changeCompilationMode(substring, statement.token)
                    else -> statement.token.compileError("Unexpected argument ${it.name} for Project")
                }
            }

            is ListCollection -> {
                when (it.name) {
                    "loadPackages" -> {
                        if (it.keywordArg.initElements[0] !is LiteralExpression.StringExpr) {
                            it.keywordArg.token.compileError("packages must be listed as String")
                        }

                        generator.addToGradleDependencies(it.keywordArg.initElements.map { it.token.lexeme })
                    }
                }
            }

            else -> it.keywordArg.token.compileError("Only String args allowed for ${it.name}")
        }
    }
}
