import codogen.codogenKt
import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.Parser
import frontend.parser.statements
import frontend.parser.types.Statement
import frontend.util.addIdentationForEachString
import frontend.util.fillSymbolTable
import java.io.File
import java.util.concurrent.TimeUnit

fun emptySource() {
    check("", mutableListOf(TokenType.EndOfFile))
}

fun punctuation() {
    check("{}", mutableListOf(TokenType.BinarySymbol, TokenType.BinarySymbol, TokenType.EndOfFile))
}

fun check(source: String, tokens: MutableList<TokenType>) {
    val lexer = Lexer(source, "sas")
    lexer.fillSymbolTable()
    val result = lexer.lex().map { it.kind }
    if (tokens != result) {
        throw Throwable("\n\ttokens: $tokens\n\tresult: $result")
    }
}

fun lex(source: String): MutableList<Token> {
    val lexer = Lexer(source, "sas")
    lexer.fillSymbolTable()
    return lexer.lex()
}

fun String.runCommand(workingDir: File, withOutputCapture: Boolean = false) {
    val p = ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)

    if (withOutputCapture) {
        p.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
    }

    p.start()
        .waitFor(60, TimeUnit.MINUTES)
}

fun runKotlin(kotlinCode: String) {
    val file = File("sas.kt")
    val parent = file.absoluteFile.parentFile
    file.writeText(kotlinCode)
    "kotlinc sas.kt -language-version 2.0 -include-runtime -d sas.jar".runCommand(parent)
    "java -jar sas.jar".runCommand(parent, true)

}

fun putInMainKotlinCode(code: String) = buildString {
    append("fun main() {\n")
    append(code, "\n")
    append("}\n")
}

fun kotlinCodeFromNiva(nivaCode: String): String {
    fun getAst(source: String): List<Statement> {
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.statements()
        return ast
    }

    fun generateKotlin(source: String): String {
        val ast = getAst(source)
        val codogenerator = codogenKt(ast)
        return codogenerator
    }

    return generateKotlin(nivaCode)
}

fun String.addNivaStd(): String {
    val nivaStd = """
    fun Any?.echo() = println(this)
    """.trimIndent()
    return buildString {
        append(nivaStd, "\n")
        append(this@addNivaStd)
    }
}


fun main(args: Array<String>) {

    val source = """
        x = "Hello" + " World" + " from Niva!"
        x echo
    """.trimIndent()
    val ktCode = kotlinCodeFromNiva(source)
    val code1 = ktCode.addIdentationForEachString(1)
    val code2 = putInMainKotlinCode(code1)
    val code3 = code2.addNivaStd()

    runKotlin(code3)


}
