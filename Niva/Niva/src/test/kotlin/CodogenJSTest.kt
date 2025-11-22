import main.codogenjs.codegenJs
import kotlin.test.Test
import kotlin.test.assertEquals

class CodogenJSTest {


//    @Test
//    fun ifff() {
//        val source = """
//
//        """.trimIndent()
//        val expected = """
//            ((1) + (2))
//            (("sas") + ("sas"))
//            ((true) || (false))
//        """.trimIndent()
//        val statements = resolve(source)
//        val w = codegenJs(statements)
//        assertEquals(expected, w)
//    }
    @Test
    fun exprSimple() {
        val source = """
            1
            "sas"
            true
            false
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 4)
        val w = codegenJs(statements)
        assertEquals(source, w)
    }

    @Test
    fun binaryExpr() {
        val source = """
            1 + 2
            "sas" + "sas"
            true || false
        """.trimIndent()
        val expected = """
            ((1) + (2))
            (("sas") + ("sas"))
            ((true) || (false))
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun manyExpr() {
        val source = """
            1 inc
            1 inc inc dec
            1 inc + 2 dec
            
        """.trimIndent()
        val expected = """
            Int__inc(1)
            Int__dec(Int__inc(Int__inc(1)))
            ((Int__inc(1)) + (Int__dec(2)))
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun unaryDecl() {
        val source = """
            Int sas = 22
        """.trimIndent()
        val expected = """
            function Int__sas(receiver) {
                return (22)}
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun keywordDecl() {
        val source = """
            Int foo::Int bar::String = 22
        """.trimIndent()
        val expected = """
            function Int__fooBar__Int__String(receiver, foo, bar) {
                return (22)}
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }


    @Test
    fun keywordAfterBinary() {
        val source = """
            Int foo::Int bar::String = 22
           
            1 inc + 2 foo: 1 bar: "string"
        """.trimIndent()
        val expected = """
            function Int__fooBar__Int__String(receiver, foo, bar) {
                return (22)}
            Int__fooBar__Int__String(((Int__inc(1)) + (2)), 1, "string")
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }
}