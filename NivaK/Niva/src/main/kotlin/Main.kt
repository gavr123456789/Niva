import codogen.codogenKt
import frontend.Lexer
import frontend.lex
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.statements
import frontend.parser.types.ast.Statement
import frontend.typer.Resolver
import frontend.typer.generateKtProject
import frontend.util.OS_Type
import frontend.util.div
import frontend.util.fillSymbolTable
import frontend.util.getOSType
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


fun runGradleRunInProject(path: String) {
    val file = File(path)
    when (getOSType()) {
        OS_Type.WINDOWS -> "cmd.exe /c gradlew.bat -q run".runCommand(file, true)
        OS_Type.LINUX -> TODO()
        OS_Type.MAC -> TODO()
    }
}

fun compileProjFromFile(pathToProjectRootFile: String, pathWhereToGenerateKt: String) {
    fun listFilesRecursively(directory: File): List<File> {
        val fileList = mutableListOf<File>()

        // Получаем все файлы и поддиректории в данной директории
        val filesAndDirs = directory.listFiles()

        if (filesAndDirs != null) {
            for (file in filesAndDirs) {
                if (file.isFile) {
                    // Если это файл, добавляем его в список
                    fileList.add(file)
                } else if (file.isDirectory) {
                    // Если это директория, рекурсивно вызываем эту же функцию для нее
                    fileList.addAll(listFilesRecursively(file))
                }
            }
        }

        return fileList
    }


    val mainFile = File(pathToProjectRootFile)
    val projectFolder = mainFile.absoluteFile.parentFile
    val otherFilesPaths = listFilesRecursively(projectFolder).filter { it.name != mainFile.name }
    // we have main file, and all other files, so we can create resolver now

    val resolver = Resolver(
        projectName = "common",
        mainFilePath = mainFile,
        otherFilesPaths = otherFilesPaths,
        statements = mutableListOf()
    )

    resolver.generateKtProject(pathWhereToGenerateKt)


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
        // STD
        fun Any?.echo() = println(this)
        
        inline fun IntRange.forEach(action: (Int) -> Unit) {
            for (element in this) action(element)
        }
        
        // for cycle
        inline fun Int.toDo(to: Int, `do`: (Int) -> Unit) {
            val range = this.rangeTo(to)
            for (element in range) `do`(element)
        }
        
        // while cycles
        typealias WhileIf = () -> Boolean
        
        inline fun <T> WhileIf.whileTrue(x: () -> T) {
            while (this()) {
                x()
            }
        }
        
        inline fun <T> WhileIf.whileFalse(x: () -> T) {
            while (!this()) {
                x()
            }
        }
        // end of STD
        
    """.trimIndent()
    return buildString {
        append(nivaStd, "\n")
        append(this@addNivaStd)
    }
}


fun main(args: Array<String>) {
    // java -jar .\Niva.jar C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\.infroProject C:\Users\gavr\Documents\Projects\Fun\Niva\NivaK\Niva\src\nivaExampleProject\main.niva

    val data = "sas"
    val open = "sus"
    val enum = 1
    val catch = 2

    val isThereArgs = args.count() == 2

    val pathToProjectRoot = if (isThereArgs) args[0] else ".." / ".infroProject"
    val pathWhereToGenerateKt = pathToProjectRoot / "src" / "main" / "kotlin"
    val pathToNivaProjectRootFile =
        if (isThereArgs) args[1] else "C:\\Users\\gavr\\Documents\\Projects\\Fun\\Niva\\NivaK\\Niva\\src\\examples\\Constructors\\constructors.niva" //"." / "src" / "nivaExampleProject" / "main.niva"

    compileProjFromFile(pathToNivaProjectRootFile, pathWhereToGenerateKt)
    runGradleRunInProject(pathToProjectRoot)
}
