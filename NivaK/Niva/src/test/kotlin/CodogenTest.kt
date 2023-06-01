import codogen.codogenKt
import org.testng.annotations.Test

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
        assert(ktCode == "3.inc().dec().sas()")
    }

    @Test
    fun binaryCall() {
        val source = "3 inc dec sas + 2 dec sas - 3 sus"
        val ktCode = generateKotlin(source)
        assert(ktCode == "3.inc().dec().sas() + 2.dec().sas() - 3.sus()")
    }

    @Test
    fun binaryCall2() {
        val source = "3 + 2 - 3"
        val ktCode = generateKotlin(source)
        assert(ktCode == "3 + 2 - 3")
    }

    @Test
    fun keywordCall() {
        val source = "6 from: 3 inc dec sas + 2 dec sas + 5 to: 3 sus"
        val ktCode = generateKotlin(source)
        assert(ktCode == "6.fromTo(from = 3.inc().dec().sas() + 2.dec().sas() + 5, to = 3.sus())")
    }

    @Test
    fun keywordCall2() {
        val source = "1 from: 2 to: 3"
        val ktCode = generateKotlin(source)
        assert(ktCode == "1.fromTo(from = 2, to = 3)")
    }


    @Test
    fun varDeclaration() {
        val source = "x = 1"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1")
    }

    @Test
    fun varDeclarationWithBinaryMessageCall() {
        val source = "x = 1 + 41"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1 + 41")
    }

    @Test
    fun varDeclarationWithUnaryMessageCall() {
        val source = "x = 1 inc"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1.inc()")
    }

    @Test
    fun varDeclarationWithKeywordMessageCall() {
        val source = "x = 1 from: 1 inc + 2"
        val ktCode = generateKotlin(source)
        assert(ktCode == "val x = 1.from(from = 1.inc() + 2)")
    }

}
