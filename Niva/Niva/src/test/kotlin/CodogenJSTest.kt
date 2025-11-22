import main.codogenjs.codegenJs
import kotlin.test.Test
import kotlin.test.assertEquals

class CodogenJSTest {


    @Test
    fun typeCreate() {
        val source = """
            type Person name: String age: Int
            
            Person sas -> Int = [
                q = age + 1
                w = this age
                ^ q
            ]
            
            p = Person name: "Alice" age: 24
            
            name = p name 
            age = p age
        """.trimIndent()
        val expected = """
            class Person {
                constructor(name, age) {
                    this.name = name;
                    this.age = age;
                }
            }
            
            function Person__sas(receiver) {
                let name = receiver.name
                let age = receiver.age
                let q = ((age) + (1))
                let w = receiver.age
                return (q)
            }
            
            let p = new Person("Alice", 24)
            let name = p.name
            let age = p.age
        """.trimIndent()

        val statements = resolve(source)
        val w = codegenJs(statements)

        assertEquals(expected, w.trim())
    }
    @Test
    fun codeBlockLambdaWithArgs() {
        val source = """
            x = [x::Int -> x + 2]
        """.trimIndent()
        val expected = """
            let x = (x) => ((x) + (2))
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun codeBlockLambda() {
        val source = """
            x = [1 + 2]
            m = [
                1 + 2
                1 + 3
            ]
        """.trimIndent()
        val expected = """
            let x = () => ((1) + (2))
            let m = () => {
                ((1) + (2));
                return ((1) + (3))
            }
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun switchExpr() {
        val source = """
            x = | 1
            | 1 => "sas"
            | 2 => [
                y = "4"
                y + "2"
            ]
            | 3 => "fpg"
            |=> "default"
        """.trimIndent()
        val expected = """
            let x = (() => {
                switch (1) {
                    case 1:
                        return ("sas");
                    case 2:
                        let y = "4"
                        return (((y) + ("2")));
                    case 3:
                        return ("fpg");
                    default:
                        return ("default");
                }
            })()
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }


    @Test
    fun switchStatement() {
        val source = """
            | 1
            | 1 => "sas"
            | 2 => [
                y = "4"
                y + "2"
            ]
            | 3 => "fpg"
            |=> "default"
        """.trimIndent()

        val expected = """
            switch (1) {
                case 1:
                    "sas"
                    break;
                case 2:
                    let y = "4"
                    ((y) + ("2"))
                    break;
                case 3:
                    "fpg"
                    break;
                default:
                    "default"
            }
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun ifExpr() {
        val source = """
            true ? 1 ! [2]
        """.trimIndent()
        val expected = """
         (() => { if (true) return (1); return (2); })()
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }


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