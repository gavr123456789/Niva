import codogen.codogenKt
import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.Statement
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

//    val commantd = """
//        /usr/lib/jvm/java-17-openjdk/bin/java
//        -javaagent:/home/gavr/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-0/231.9011.34/lib/idea_rt.jar=46715
//        :/home/gavr/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-0/231.9011.34/bin
//        -Dfile.encoding=UTF-8
//        -classpath /home/gavr/Documents/Projects/Fun/Niva/NivaK/Niva/out/production/Niva
//        :/home/gavr/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.8.21/kotlin-stdlib-jdk8-1.8.21.jar
//        :/home/gavr/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.8.21/kotlin-stdlib-1.8.21.jar
//        :/home/gavr/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-common/1.8.21/kotlin-stdlib-common-1.8.21.jar
//        :/home/gavr/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar
//        :/home/gavr/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.8.21/kotlin-stdlib-jdk7-1.8.21.jar MainKt
//
//    """.trimIndent()
//
//    val source = """
//        Int sas = this echo
//        1 sas
//
//        x = "Hello" + " World" + " from Niva!"
//        x echo
//        | x count < 5 => x count echo
//        | x count == 22 => [
//          y = x count + 20
//          y echo
//        ]
//        |=> "count < 10" echo
//    """.trimIndent()
//    val ktCode = kotlinCodeFromNiva(source)
//    val code1 = ktCode.addIndentationForEachString(1)
//    val code2 = putInMainKotlinCode(code1)
//    val code3 = code2.addNivaStd()
//
//    runKotlin(code3)


}
