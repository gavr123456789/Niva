import frontend.parser.Parser
import frontend.parser.UnaryMsg
import frontend.parser.VarDeclaration
import frontend.parser.parse
import org.testng.annotations.Test

class ParserTest {
    @Test
    fun varDeclaration() {
        val source = "x::int = 1"
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.parse()
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
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.parse()
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name.str == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun string() {
        val source = "\"sas\""
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.parse()
        assert(ast.count() == 1)

        val declaration = ast[0] as UnaryMsg
//        println("declaration.type = ${declaration.type}")
//        assert(declaration.type == "string")
        assert(declaration.str == "\"sas\"")
    }


    @Test
    fun helloWorld() {
        val source = "\"sas\" echo"
        val tokens = lex(source)
        val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
        val ast = parser.parse()
        assert(ast.count() == 1)

        val declaration = ast[0]
        assert(declaration is UnaryMsg)
        val decl = declaration as UnaryMsg

        assert(decl.selectorName == "echo")
        assert(decl.receiver.type == "string")
        assert(decl.receiver.str == "\"sas\"")
    }
}