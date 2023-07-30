import codogen.codogenKt
import org.junit.jupiter.api.Assertions.assertEquals
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
        val source = "6 from: 3 inc dec sas + 2 dec sas + 5 to: 4 sus"
        val ktCode = generateKotlin(source)
        assertEquals("6.fromTo(3.inc().dec().sas() + 2.dec().sas() + 5, 4.sus())\n", ktCode)
    }

    @Test
    fun keywordCall2() {
        val source = "1 from: 2 to: 3"
        val ktCode = generateKotlin(source)
        assertEquals("1.fromTo(2, 3)\n", ktCode)
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
        assert(ktCode == "val x = 1.from(1.inc() + 2)\n")
    }

    @Test
    fun unaryDeclaration() {
        val source = """
            int sas -> unit = [
              q = 1
              self echo
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun int.sas(): unit {
                val q = 1
                self.echo()
            }
        """.trimIndent()
        assert(ktCode == expect)
    }

    @Test
    fun unaryDeclarationSingle() {
        val source = "int sas -> unit = self echo "
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun int.sas(): unit = self.echo()
        """.trimIndent()
        assert(ktCode == expect)
    }

    @Test
    fun binaryDeclaration() {
        val source = """
            int + x::int -> unit = [
                q = 1               
                self + x - 1 
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            operator fun int.plus(x: int): unit {
                val q = 1
                self + x - 1
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun keywordDeclaration() {
        val source = """
            int from: x::float to: y::string -> bool = [
              q = 1
              self + x - 1 > 0
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun int.fromTo(x: float, y: string): bool {
                val q = 1
                self + x - 1 > 0
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }


    @Test
    fun typeDeclaration() {
        val source = "type Person name: String age: Int"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            class Person(val name: String, val age: Int)
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun ifDeclaration() {
        val source = "| 1 > 5 => 1 echo |=> 2 echo"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            if (1 > 5) {
                1.echo()
            } else {
                2.echo()
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun ifDeclarationNoElse() {
        val source = "| 1 > 5 => 1 echo"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            if (1 > 5) {
                1.echo()
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun ifDeclarationManyBranch() {
        val source = "| 1 > 5 => 1 echo | 2 < 5 => 2 echo | 3 + 1 > 3 => 3 echo"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            if (1 > 5) {
                1.echo()
            } else if (2 < 5) {
                2.echo()
            } else if (3 + 1 > 3) {
                3.echo()
            }
        """.trimIndent().trim()
        assert(ktCode == expect)
    }

    @Test
    fun switchManyBranch() {
        val source = """
            x = 3
            | x
            | 1 => 1 echo 
            | 2 => 2 echo 
            | 3 => 3 echo 
            |=> "something else" echo 
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            val x = 3
            when (x) {
                1 -> 1.echo()
                2 -> 2.echo()
                3 -> 3.echo()
                else -> "something else".echo()
            }
        """.trimIndent().trim()
        assertEquals(ktCode, expect)
    }
}
