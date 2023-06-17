import codogen.codogenKt
import codogen.int
import org.junit.jupiter.api.Test

fun generateKotlin(source: String): String {
    val ast = getAst(source)
    val codogenerator = codogenKt(ast)
    return codogenerator
}

class CodogenTest {

    @Test
    fun unaryCall() {
        val source = "3 inc dec sas"
        val ktCode = generateKotlin(source)
        assert(ktCode == "3.inc().dec().sas()\n")
    }

    @Test
    fun binaryCall() {
        val source = "3 inc dec sas + 2 dec sas - 3 sus"
        val ktCode = generateKotlin(source)
        assert(ktCode == "3.inc().dec().sas() + 2.dec().sas() - 3.sus()\n")
    }

    @Test
    fun binaryCall2() {
        val source = "3 + 2 - 3"
        val ktCode = generateKotlin(source)
        assert(ktCode == "3 + 2 - 3\n")
    }

    @Test
    fun keywordCall() {
        val source = "6 from: 3 inc dec sas + 2 dec sas + 5 to: 3 sus"
        val ktCode = generateKotlin(source)
        assert(ktCode == "6.fromTo(3.inc().dec().sas() + 2.dec().sas() + 5, 3.sus())\n")
    }

    @Test
    fun keywordCall2() {
        val source = "1 from: 2 to: 3"
        val ktCode = generateKotlin(source)
        assert(ktCode == "1.fromTo(2, 3)\n")
    }


    @Test
    fun varDeclaration() {
        val source = "x = 1"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1\n")
    }

    @Test
    fun varDeclarationWithBinaryMessageCall() {
        val source = "x = 1 + 41"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1 + 41\n")
    }

    @Test
    fun varDeclarationWithUnaryMessageCall() {
        val source = "x = 1 inc"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1.inc()\n")
    }

    @Test
    fun varDeclarationWithKeywordMessageCall() {
        val source = "x = 1 from: 1 inc + 2"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1.from(from = 1.inc() + 2)\n")
    }

    @Test
    fun unaryDeclaration() {
        val source = "int sas -> unit = [ self echo ]"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun int.sas(): unit {
                self.echo()
            }
        """.trimIndent()
        assert(ktCode == expect)
    }

    @Test
    fun binaryDeclaration() {
        val source = "int + x::int -> unit = [ self + x - 1 ]"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            operator fun int.plus(x: int): unit {
                self + x - 1
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun keywordDeclaration() {
        val source = "int from: x::float to: y::string -> bool = [ self + x - 1 > 0]"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun int.fromTo(x: float, y: string): bool {
                self + x - 1 > 0
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

}
