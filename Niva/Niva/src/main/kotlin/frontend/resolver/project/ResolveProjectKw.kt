package main.frontend.typer.project


import frontend.resolver.*
import main.utils.RED
import main.utils.WHITE
import main.utils.YEL
import main.codogen.addToGradleDependencies
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.ListCollection
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.MessageSend
import main.utils.removeDoubleQuotes

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
//                    "compose" -> useCompose(substring, statement.token)
                    else -> statement.token.compileError("Unexpected argument $WHITE${it.name} ${RED}for Project")
                }
            }

            is ListCollection -> {
                when (it.name) {
                    "loadPackages" -> {
                        if (it.keywordArg.initElements[0] !is LiteralExpression.StringExpr) {
                            it.keywordArg.token.compileError("Packages must be listed as ${YEL}String")
                        }

                        generator.addToGradleDependencies(it.keywordArg.initElements.map {x -> x.token.lexeme })
                    }
                    // TODO list of use "Project use: {"sas" "sus"}"
                }
            }

            else -> it.keywordArg.token.compileError("Only ${YEL}String$WHITE args allowed for $YEL${it.name}")
        }
    }
}
