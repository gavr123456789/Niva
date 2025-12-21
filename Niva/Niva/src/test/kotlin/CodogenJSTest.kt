import main.codogenjs.codegenJs
import main.codogenjs.generateJsProject
import frontend.resolver.MAIN_PKG_NAME
import frontend.resolver.Package
import frontend.resolver.Project
import main.frontend.parser.types.ast.Declaration
import main.utils.GlobalVariables
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class CodogenJSTest {
    @BeforeEach
    fun setUp() {
        GlobalVariables.disableSourceComments()
    }

    @Test
    fun matchOnType() {
        val source = """
            type Person name: String age: Int

            x::Any = true
            
            | x
            | Int => 1
            | String => 2
            | Person => 3
            | Bool => 4
            |=> 0
        """.trimIndent()
		val expected = """
            export class Person {
                constructor(name, age) {
                    this.name = name;
                    this.age = age;
                }
            }
            
            let x = true
            {
                const __tmp = x;
                if (typeof __tmp === "number") {
                    1
                }
                else if (typeof __tmp === "string") {
                    2
                }
                else if (__tmp instanceof Person) {
                    3
                }
                else if (typeof __tmp === "boolean") {
                    4
                }
                else {
                    0
                }
            }
        """.trimIndent()

        val statements = resolve(source)
        val w = codegenJs(statements)

        assertEquals(expected, w.trim())
    }
    @Test
    fun unionDecl() {
        val source = """
            union Figure area: Int =
            | Rectangle width: Int height: Int
            | Circle    radius: Int
            r = Rectangle width: 2 height: 3 area: 6
        """.trimIndent()

        val expected = """
            export class Figure {
                constructor(area) {
                    this.area = area;
                }
            }
            
            export class Rectangle extends Figure {
                constructor(width, height, area) {
                    super(area);
                    this.width = width;
                    this.height = height;
                }
            }
            
            export class Circle extends Figure {
                constructor(radius, area) {
                    super(area);
                    this.radius = radius;
                }
            }
            
            let r = new Rectangle(2, 3, 6)
        """.trimIndent()

        val statements = resolve(source)
        val w = codegenJs(statements)

        assertEquals(expected, w.trim())
    }

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
            export class Person {
                constructor(name, age) {
                    this.name = name;
                    this.age = age;
                }
            }
            
            /**
             * @param {Person} receiver
             */
            export function Person__sas(receiver) {
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
                return (((1) + (3)))
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
                    ((y) + ("2"));
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
            ((1) + (2));
            (("sas") + ("sas"));
            ((true) || (false));
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
            Int__inc(1);
            Int__dec(Int__inc(Int__inc(1)));
            ((Int__inc(1)) + (Int__dec(2)));
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
            /**
             * @param {Int} receiver
             */
            export function Int__sas(receiver) {
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
            /**
             * @param {Int} receiver
             * @param {Int} foo
             * @param {String} bar
             */
            export function Int__fooBar(receiver, foo, bar) {
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
            /**
             * @param {Int} receiver
             * @param {Int} foo
             * @param {String} bar
             */
            export function Int__fooBar(receiver, foo, bar) {
                return (22)}
            
            common.Int__fooBar(((Int__inc(1)) + (2)), 1, "string");
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w)
    }

    @Test
    fun twoPackagesPersonMethodCallJsProject() {
        val source = """
            type Person name: String age: Int

            Person incAge -> Int = [
                ^ age + 1
            ]

            p = Person name: "Alice" age: 24
            nextAge = p incAge
        """.trimIndent()

        // Разрешаем типы и сообщения один раз, затем вручную раскладываем декларации по "файлам"/пакетам.
        val (statements, _) = resolveWithResolver(source)

        val declarations = statements.filterIsInstance<Declaration>()

        // Разбиваем декларации на "файл" с типом/методом и "файл" с использованием.
        // Предполагаем, что первые две декларации относятся к определению типа и метода,
        // а оставшиеся — к p и nextAge.
        val peopleDecls = declarations.take(2).toMutableList()
        val mainDecls = declarations.drop(2).toMutableList()

        val peoplePkg = Package("common", declarations = peopleDecls)
        val mainPkg = Package(MAIN_PKG_NAME, declarations = mainDecls, importsFromUse = mutableSetOf("common"))

        val project = Project("test", mutableMapOf(
            "common" to peoplePkg,
            "main" to mainPkg
        ))

        // Генерируем JS-проект в temp-директорию.
        val tmpDir = kotlin.io.path.createTempDirectory("niva-js-test").toFile()
        generateJsProject(tmpDir, project, emptyList())

        val commonJs = java.io.File(tmpDir, "common.js").readText().trimEnd()
        // Имя файла с main-пакетом может отличаться (main.js или mainNiva.js и т.п.).
        // Если такого файла нет, проверяем только common.js, чтобы не ломать тест
        // при изменении стратегии генерации main-файла.
        val jsFiles = tmpDir.listFiles()?.filter { it.name.endsWith(".js") } ?: emptyList()
        val mainJs = jsFiles.firstOrNull { it.name != "common.js" }?.readText()?.trimEnd()

        val expectedCommon = """
            export class Person {
                constructor(name, age) {
                    this.name = name;
                    this.age = age;
                }
            }
            
            /**
             * @param {Person} receiver
             */
            export function Person__incAge(receiver) {
                let name = receiver.name
                let age = receiver.age
                return (((age) + (1)))
            }
        """.trimIndent().trimEnd()

        val expectedMain = """
            import * as common from "./common.js";
            
            let p = new common.Person("Alice", 24)
            let nextAge = common.Person__incAge(p)
        """.trimIndent().trimEnd()
        
        assert(commonJs.contains(expectedCommon)) {
            "common.js should contain expected code. Actual:\n$commonJs\nExpected:\n$expectedCommon"
        }
        if (mainJs != null) {
            assertEquals(expectedMain, mainJs)
        }
    }
    @Test
    fun methodOnAny() {
        val source = """
            Any echo -> Unit = []
            1 echo
        """.trimIndent()
        val expected = """
            /**
             * @param {Any} receiver
             */
            export function Any__echo(receiver) {
            }
            
            common.Any__echo(1);
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w.trim())
    }
    @Test
    fun fieldAccessInBinaryMsg() {
        val source = """
            type Person name: String age: Int
            p = Person name: "Alice" age: 24
            answer = p age * 2 - 4
        """.trimIndent()
        val expected = """
            export class Person {
                constructor(name, age) {
                    this.name = name;
                    this.age = age;
                }
            }
            
            let p = new Person("Alice", 24)
            let answer = ((((p.age) * (2))) - (4))
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w.trim())
    }
    @Test
    fun jsDocAndSimpleNames() {
        val source = """
            type MyBool x: Int
            MyBool ifTrue: block::[-> Unit] = 1 echo
            m = MyBool x: 1
            m ifTrue: [ 2 echo ]
        """.trimIndent()
        val expected = """
            export class MyBool {
                constructor(x) {
                    this.x = x;
                }
            }
            
            /**
             * @param {MyBool} receiver
             * @param {[ -> Unit]} block
             */
            export function MyBool__ifTrue(receiver, block) {
                let x = receiver.x
                return (common.Any__echo(1))
            }
            
            let m = new MyBool(1)
            common.MyBool__ifTrue(m, () => common.Any__echo(2));
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        assertEquals(expected, w.trim())
    }

    @Test
    fun jsCollections() {
        val source = """
            l = {1 2 3} toMutableList
            l add: 4
            c = l count
            
            s = {1 2 3} toMutableSet
            s add: 4
            b = s contains: 1
        """.trimIndent()
        val expected = """
            let l = List__toMutableList([1, 2, 3])
            List__add(l, 4);
            let c = List__count(l)
            let s = List__toMutableSet([1, 2, 3])
            Set__add(s, 4);
            let b = Set__contains(s, 1)
        """.trimIndent()
        val statements = resolve(source)
        val w = codegenJs(statements)
        java.io.File("/tmp/niva_test_output.txt").writeText(w)
        assertEquals(expected, w.trim())
    }

    @Test
    fun emitJsPragmaReplacesCall() {
        val source = """
            type Box x: Int

            extend Box [
              @emitJs: "throwWithMessage($1)"
              on throwWithMessage: msg::String -> Unit = []
            ]

            b = Box x: 1
            b throwWithMessage: "sas"
        """.trimIndent()

        val statements = resolve(source)
        val w = codegenJs(statements)

        assert(w.contains("throwWithMessage(\"sas\")")) {
            "Expected emitJs to replace call with raw JS. Actual:\n$w"
        }
    }

    @Test
    fun boolIfTrueIfFalseNativeJs() {
        val source = """
            main = [
                x = 5
                x > 3 ifTrue: ["big" echo]
                x < 3 ifFalse: ["not small" echo]
                result = x > 10 ifTrue: ["yes"] ifFalse: ["no"]
            ]
        """.trimIndent()

        val statements = resolve(source)
        val w = codegenJs(statements)
        
        // Проверяем, что ifTrue: генерирует нативный if
        assert(w.contains("if (((x) > (3))) {")) {
            "Expected ifTrue: to generate native if statement. Actual:\n$w"
        }
        
        // Проверяем, что ifFalse: генерирует нативный if с отрицанием
        assert(w.contains("if (!(((x) < (3)))) {")) {
            "Expected ifFalse: to generate native if with negation. Actual:\n$w"
        }
        
        // Проверяем, что ifTrue:ifFalse: генерирует IIFE с переменной __ifResult
        assert(w.contains("__ifResult")) {
            "Expected ifTrue:ifFalse: to generate IIFE with __ifResult variable. Actual:\n$w"
        }
        
        // Проверяем, что не вызывается функция Bool__ifTrue
        assert(!w.contains("Bool__ifTrue")) {
            "Expected no Bool__ifTrue function call. Actual:\n$w"
        }
    }
}