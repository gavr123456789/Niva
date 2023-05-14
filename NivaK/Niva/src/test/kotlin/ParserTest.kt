import frontend.parser.*
import org.testng.annotations.Test

class ParserTest {
    @Test
    fun varDeclaration() {
        val source = "x::int = 1"
        val ast = declarationList(source)
        println("ast.count = ${ast.count()}")
        assert(ast.count() == 1)
        println("ast = $ast")
        println("ast[0] = ${ast[0]}")

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name.str == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun varDeclWithTypeInfer() {
        val source = "x = 1"
        val ast = declarationList(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name.str == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun string() {
        val source = "\"sas\""
        val ast = declarationList(source)
        assert(ast.count() == 1)

        val declaration = ast[0] as UnaryMsg

        assert(declaration.str == "\"sas\"")
    }


    @Test
    fun helloWorld() {
        val source = "\"sas\" echo"
        val ast = declarationList(source)
        assert(ast.count() == 1)

        val unaryMsg = ast[0] as UnaryMsg

        assert(unaryMsg.selectorName == "echo")
        assert(unaryMsg.receiver.type == "string")
        assert(unaryMsg.receiver.str == "\"sas\"")
    }


    @Test
    fun varDeclAndUnaryMsg() {
        val source = """
        x = 1
        x echo
    """.trimIndent()
        val ast = declarationList(source)
        assert(ast.count() == 2)

        val declaration = ast[0] as VarDeclaration
        val unaryMsg = ast[1] as UnaryMsg
        assert(declaration.name.str == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")

        assert(unaryMsg.selectorName == "echo")
//        assert(unaryMsg.receiver.type == "string")
        assert(unaryMsg.receiver.str == "x")


    }

    private fun declarationList(source: String): List<Declaration> {
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.parse()
        return ast
    }
}