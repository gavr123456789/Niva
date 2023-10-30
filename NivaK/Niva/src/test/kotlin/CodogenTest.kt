import codogen.codegenKt
import frontend.typer.Resolver
import frontend.typer.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

fun generateKotlin(source: String): String {
    val ast = getAstTest(source)
    val resolver = Resolver(
        projectName = "common", mainFile = File("sas.niva"), statements = ast.toMutableList()
    )
    resolver.resolve(resolver.statements, mutableMapOf())
    val codogenerator = codegenKt(resolver.statements)

    return codogenerator
}

fun generateKotlinWithoutResolve(source: String): String {
    val ast = getAstTest(source)
    val resolver = Resolver(
        projectName = "common", mainFile = File("sas.niva"), statements = ast.toMutableList()
    )
//    resolver.resolve(resolver.statements, mutableMapOf())
    val codogenerator = codegenKt(resolver.statements)

    return codogenerator
}

class CodogenTest {

    @Test
    fun stringLiteral() {
        val source = "x = \"sas\""
        val ktCode = generateKotlin(source)
        assertEquals("val x = \"sas\"\n", ktCode)
    }

    @Test
    fun unaryCall() {
        val source = "3 inc dec sas"
        val ktCode = generateKotlin(source)
        assertEquals("3.inc().dec().sas()\n", ktCode)
    }

    @Test
    fun binaryCall() {
        val source = "3 inc dec sas + 2 dec sas - 3 sus"
        val ktCode = generateKotlin(source)
        assertEquals("3.inc().dec().sas() + 2.dec().sas() - 3.sus()\n", ktCode)
    }

    @Test
    fun binaryCall2() {
        val source = "3 + 2 - 3"
        val ktCode = generateKotlin(source)
        assertEquals(ktCode, "3 + 2 - 3\n")
    }

    @Test
    fun keywordCall() {
        val source = "6 to: 3 inc dec sas + 2 dec sas + 5 do: [34]"
        val ktCode = generateKotlin(source)
        assertEquals("(6).toDo(3.inc().dec().sas() + 2.dec().sas() + 5, {34})\n", ktCode)
    }

    @Test
    fun keywordCall2() {
        val source = "1 to: 2 do: [56]"
        val ktCode = generateKotlin(source)
        assertEquals("(1).toDo(2, {56})\n", ktCode)
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
            Int sas -> Unit = [
              q = 1
              self echo
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun Int.sas(): Unit {
                val q = 1
                self.echo()
            }
        """.trimIndent()
        assertEquals(expect, ktCode)
    }

    @Test
    fun unaryDeclarationSingle() {
        val source = "Int sas -> Unit = self echo "
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun Int.sas(): Unit = self.echo()
        """.trimIndent()
        assertEquals(expect, ktCode)
    }

    @Test
    fun binaryDeclaration() {
        val source = """
            Int + x::Int = [
                q = 1               
                self + x - 1 
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            operator fun Int.plus(x: Int) {
                val q = 1
                self + x - 1
            }
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun keywordDeclaration() {
        val source = """
            Int from: x::Float to: y::String -> Boolean = [
              q = 1
              this + x - 1 - 0
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun Int.fromTo(x: Float, y: String): Boolean {
                val q = 1
                this + x - 1 - 0
            }
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }


    @Test
    fun typeDeclaration() {
        val source = "type Person name: String age: Int"
        val ktCode = generateKotlin(source).trim()
        val expect = """
            class Person(val name: String, val age: Int)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
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
        assertEquals(expect, ktCode)
    }

    @Test
    fun ifDeclarationWithBody() {
        val source = """
              | 5 == 6 => [
                  " is open" echo
                  5 echo
                  
                ]
              |=> " is closed" echo
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            if (x.count() == 22) {
                1.echo()
            } else if (7 < 6) {
                val y = x.count() + 10
                y.echo()
            
            } else {
                else.branch().sas()
            }
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
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
        assertEquals(expect, ktCode)
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
        assertEquals(expect, ktCode)
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

    @Test
    fun getter() {
        val source = """
            type Person name: String age: Int
            person = Person name: "Sas" age: 4
            person name
          
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            class Person(val name: String, val age: Int)
            val person = Person("Sas", 4)
            person.name
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun constructorWithoutBrackets() {
        val source = """
            type Person name: String age: Int
            person = Person name: "Bob" age: 42
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            class Person(val name: String, val age: Int)
            val person = Person("Bob", 42)
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun lambdaCall() {
        val source = """
            x = [x::Int, y::Int -> x + y]
            x x: 1 y: 2
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            val x = {x: Int, y: Int, -> x + y}
            (x)(1, 2)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun lambdaCallExe() {
        val source = """
            x = [1 echo]
            x exe
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            val x = {1.echo()}
            x()
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun lambdaArgument() {
        val source = """
            Int to: x::Int doo::[Int -> Int] = [
              1 echo
            ]
            1 to: 2 doo: [5 + 5]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun Int.toDoo(x: Int, doo: (Int,) -> Int) = 1.echo()

            (1).toDoo(2, {5 + 5})
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun lambdaItArgument() {
        val source = """
            Int to: x::Int doo::[Int -> Int] = [
              x echo
            ]
            1 to: 2 doo: [it + 5]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            fun Int.toDoo(x: Int, doo: (Int,) -> Int) = 1.echo()

            (1).toDoo(2, {it + 5})
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun cycle() {
        val source = """
            1 to: 2 do: [it echo]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            (1).toDo(2, {it.echo()})
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun cycleWhileTrue() {
        val source = """
            1 to: 2 do: [it echo]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            (1).toDo(2, {it.echo()})
        """.trimIndent().trim()

        assertEquals(expect, ktCode)
    }

    @Test
    fun addBracketsToReceivers() {
        val source = """
            1 + 1 plus: 5
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            (1 + 1).plus(5)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun mutVar() {
        val source = """
            mut x = 6
            x <- 7
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            var x = 6
            x = 7
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun whileTrue() {
        val source = """
            mut x = 10
            [x > 6] whileTrue: [ 
                x <- x dec
                x echo
            ]
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            var x = 10
            ({x > 6}).whileTrue({
                x = x.dec()
                x.echo()
            })
        """.trimIndent().trim()


        assertEquals(expect, ktCode)
    }

    @Test
    fun intListCollection() {
        val source = """
            {1 2 3}
        """.trimIndent()
        val ktCode = generateKotlin(source).trim()
        val expect = """
            mutableListOf(1, 2, 3)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun expressionInBracketsWithKeyword() {
        val source = """
            (1 + 3) add: 4
        """.trimIndent()
        val ktCode = generateKotlinWithoutResolve(source).trim()
        val expect = """
            (1 + 3).add(4)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun expressionInBracketsWithUnary() {
        val source = """
            (1 + 3) sas
        """.trimIndent()
        val ktCode = generateKotlinWithoutResolve(source).trim()
        val expect = """
            (1 + 3).sas()
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

    @Test
    fun `binary with binary in brackets?`() {
        val source = """
            true && (x < 10)
        """.trimIndent()
        val ktCode = generateKotlinWithoutResolve(source).trim()
        val expect = """
            true && (x < 10)
        """.trimIndent().trim()
        assertEquals(expect, ktCode)
    }

}


