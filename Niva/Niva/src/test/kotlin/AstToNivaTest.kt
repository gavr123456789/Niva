
import main.frontend.parser.types.ast.MessageSendBinary
import main.frontend.parser.types.ast.MessageSendUnary
import main.frontend.parser.types.ast.toStr.toNivaStr
import kotlin.test.Test
import kotlin.test.assertEquals

class AstToNivaTest {

    @Test
    fun primaryInt() {
        val ast = getAstTest("1")[0] as MessageSendUnary
        val w = ast.toNivaStr()
        assert(w == "1")
    }

    @Test
    fun primaryString() {
        val ast = getAstTest("\"abc\"")[0] as MessageSendUnary
        val w = ast.toNivaStr()
        assertEquals(w, "\"abc\"")
    }

    @Test
    fun primaryFloat() {
        val ast = getAstTest("1.1")[0] as MessageSendUnary
        val w = ast.toNivaStr()
        assertEquals(w, "1.1")
    }

    @Test
    fun unaryCall() {
        val source = """
        1 inc dec
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as MessageSendUnary
        val w = q.toNivaStr()
        assertEquals(w, "1 inc dec")
    }

    @Test
    fun binaryCall() {
        val source = """
        1 inc + 2 + 3
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as MessageSendBinary
        val w = q.toNivaStr()
        assertEquals(w, "1 inc + 2 + 3")
    }


}
