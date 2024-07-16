import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.resolve
import main.frontend.meta.CompilerError
import main.frontend.parser.types.ast.*
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

fun resolve(source: String): List<Statement> {
    val ast = getAstTest(source)
    val resolver = createDefaultResolver(ast)
    val resolved = resolver.resolve(resolver.statements, mutableMapOf())
    return resolved
}

fun resolveWithResolver(source: String): Pair<List<Statement>, Resolver> {
    val ast = getAstTest(source)
    val resolver = createDefaultResolver(ast)
    val resolved = resolver.resolve(resolver.statements, mutableMapOf())
    return Pair(resolved, resolver)
}

private fun createDefaultResolver(statements: List<Statement>) = Resolver(
    projectName = "common",
    statements = statements.toMutableList(),
    currentResolvingFileName = File("")
)

class ResolverTest {
    @Test
    fun getterCall() {
        val source = """
            type Person name: String
            person = Person name: "sas"
            person name
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val q = statements[2] as MessageSendUnary
        assert(q.type?.name == "String")

        val getter = q.messages[0]
        assert(getter.type?.name == "String")
        assert((getter as UnaryMsg).kind == UnaryMsgKind.Getter)
    }

    @Test
    fun keywordCall() {
        val source = """
            Int add2::Int = [
                ^ this + add2
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)

    }

    @Test
    fun constructorCall() {
        val source = """
            x = "sas"
            type Person name: String age: Int
            person = Person name: x age: 5
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)

        val q = statements[2] as VarDeclaration

        val value = q.value as MessageSendKeyword
        assert(value.type?.name == "Person")
        assert(value.receiver.type?.name == "Person")

        val message = value.messages[0]
        assert((message as KeywordMsg).kind == KeywordLikeType.Constructor)
        assert(message.type?.name == "Person")
        val firstArg = message.args[0]
        val secondArg = message.args[1]

        assert(firstArg.keywordArg.type!!.name == "String")
        assert(secondArg.keywordArg.type!!.name == "Int")
    }

    @Test
    fun setterCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas -> Unit = [
              this name: "sus"
            ]
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)

        val q = statements[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendKeyword
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "this")
        assert((msg as KeywordMsg).kind == KeywordLikeType.SetterImmutableCopy)
    }


    @Test
    fun selfCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas -> Unit = [
              this name
            ]
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)

        val q = statements[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendUnary
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "this")
        assert((msg as UnaryMsg).kind == UnaryMsgKind.Getter)
    }

//    @Test
//    fun projectSetting() {
//        val source = """
//            Project package: "files" protocol: "path"
//            type Person name: String age: Int
//        """.trimIndent()
//
//        val ast = getAstTest(source)
//        val resolver = createDefaultResolver(ast)
//        resolver.resolveDeclarationsOnly(resolver.statements)
//        resolver.resolve(resolver.statements, mutableMapOf())
//        val proj = resolver.projects["common"]!!
//        val pack = proj.packages["files"]!!
//        val protocol = (pack.types["Person"]!!) as Type.UserType //!!.protocols["path"]!!
//
//        assert(resolver.currentProtocolName == "path")
//        assert(resolver.currentPackageName == "files")
//        assert(pack.packageName == "files")
//        assert(protocol.fields[0].name == "name")
//        assert(protocol.fields[1].name == "age")
//    }

    @Test
    fun defaultTypesInCorePackage() {
        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
        """.trimIndent()

        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)

        val q = resolver.projects["common"]!!
        val w = q.packages["core"]!!
        val e = w.types
        assert(e["Int"] != null)
        assert(e["String"] != null)
        assert(e["Float"] != null)
        assert(e["Boolean"] != null)
        assert(e["Unit"] != null)
        assert(e["Project"] != null)
        assert(e["Bind"] != null)
        assert(e["Compiler"] != null)

    }

//    @Test
//    fun registerUnary() {
//
//        val source = """
//            Project package: "files" protocol: "path"
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//
//        val ast = getAstTest(source)
//        val resolver = createDefaultResolver(ast)
//        val statements = resolver.resolve(resolver.statements, mutableMapOf())
//
//        assert(statements.count() == 3)
//        assert(resolver.currentPackageName == "files")
//        assert(resolver.currentProjectName == "common")
//        assert(resolver.currentProtocolName == "path")
//
//        val proj = resolver.projects["common"]!!
//        val pack = proj.packages["files"]!!
//        val protocol = pack.types["Person"]!!.protocols["path"]!!
//        val unary = protocol.unaryMsgs["filePath"]!!
//        assert(unary.name == "filePath")
//    }

//    @Test
//    fun registerTopLevelStatements() {
//
//        val source = """
//            1 echo
//            9 echo
//            Project package: "files" protocol: "path"
//            x = 8
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//
//        val ast = getAstTest(source)
//        val resolver = createDefaultResolver(ast)
//        resolver.resolve(resolver.statements, mutableMapOf())
//
//        assert(resolver.currentPackageName == "files")
//        assert(resolver.currentProjectName == "common")
//        assert(resolver.currentProtocolName == "path")
//
//        val proj = resolver.projects["common"]!!
//        val pack = proj.packages["files"]!!
//        val protocol = pack.types["Person"]!!.protocols["path"]!!
//        val unary = protocol.unaryMsgs["filePath"]!!
//        assert(unary.name == "filePath")
//
//        assert(resolver.topLevelStatements.isNotEmpty())
//        assert(resolver.topLevelStatements.count() == 3)
//    }

//
//    @Test
//    fun recreateKtFolder() {
//        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"
//
//        val source = """
//            1 echo
//            9 echo
//            Project package: "files" protocol: "path"
//            x = 8
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//
//        val ast = getAstTest(source).toMutableList()
//
//        val resolver = createDefaultResolver(ast)
//        resolver.resolve(resolver.statements, mutableMapOf())
//
//        resolver.generateKtProject(path)
//    }

//    @Test
//    fun manySources() {
//        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"
//
//        val source1 = """
//            1 echo
//            9 echo
//            Project package: "files" protocol: "path"
//            x = 8
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//        val source2 = """
//            1 echo
//            9 echo
//            Project package: "collections"
//            x = 8
//            type MegaMassive count: Int
//            MegaMassive add -> Unit = [1 echo]
//        """.trimIndent()
//
//        val source3 = """
//            1 echo
//            9 echo
//            Project package: "collectionsUnlim"
//            x = 8
//            type MegaMassive2 count: Int
//            MegaMassive2 add -> Unit = [1 echo]
//        """.trimIndent()
//
//        val ast1 = getAstTest(source1).toMutableList()
//        val ast2 = getAstTest(source2).toMutableList()
//        val ast3 = getAstTest(source3).toMutableList()
//
//        val ast = ast3 + ast2
//
//        val resolver = createDefaultResolver(ast)
//
//        resolver.resolve(resolver.statements, mutableMapOf())
//
//        resolver.generateKtProject(path)
//    }


    @Test
    fun codeBlockEval() {

        val source = """
            x = [x::Int, y::Int -> x + y]
            x x: 1 y: 2
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 2)
        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
        lambdaCall.args.forEach {
            assert(it.keywordArg.type != null)
        }
    }

    @Test
    fun codeBlockEvalTopLevel() {

        val source = """
            [x::Int, y::Int -> x + y]
        """.trimIndent()
        resolve(source)

    }

    @Test
    fun doCycle() {

        val source = """
            1 to: 3 do: [ 1 echo ]
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 1)
        val lambdaCall = ((statements[0]) as MessageSendKeyword).messages[0] as KeywordMsg
        lambdaCall.args.forEach {
            assert(it.keywordArg.type != null)
        }
    }

    @Test
    fun doCodeBlockWithoutParams() {

        val source = """
            x = [1 echo]
            x do 
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }

    @Test
    fun simpleUnary() {
        val source = """
            1 echo
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 1)
    }

    @Test
    fun codeblockArgument() {

        val source = """
            Int to: x::Int doo::[Int -> Int] = [
              1 echo
            ]
            1 to: 2 doo: [5 + 5]
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 2)

    }

    @Test
    fun intListCollection() {

        val source = """
            {1 2 3}
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 1)
        val listCollection = statements[0] as ListCollection
        assert(listCollection.type != null)
        assertEquals("MutableList", listCollection.type?.name)
        val type = listCollection.type as Type.UserType
        assertEquals("Int", type.typeArgumentList[0].name)
    }


    @Test
    fun ifBranchesReturnSameType() {

        val source = """
            x = | 7
            | 6 => 6
            | 9 => 5
            |=> 7
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 1)
    }


    @Test
    fun intListMap() {

        val source = """
            littleList = {1 2 3 4 5}
            
            newList = littleList map: [
              it + 1
            ]
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 2)
    }

    @Test
    fun constructor() {

        val source = """
            type Wallet amount: Int
            constructor Wallet empty = Wallet amount: 0 
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
        val construct = ast[1] as ConstructorDeclaration
        assert(construct.body.count() == 1)
    }

    @Test
    fun keyWordDeclaration() {

        val source = """
            Int from::Int to: x::Int = [
              from echo
              x echo
            ]
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 1)
    }

    @Test
    fun keyWordNamedOnlyDeclaration() {

        val source = """
            Int from: y::Int to: x::Int = [
              y echo
              x echo
            ]
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 1)
    }


    @Test
    fun receiverIsBinaryThenReturnsDifferentType() {

        val source = """
             6 > 5 && (6 < 10)
        """.trimIndent()
        val statements = resolve(source)

        assert(statements.count() == 1)
    }


    @Test
    fun unionTypes() {

        val source = """
            union Shape area: Int =
            | Rectangle width: Int height: Int
            | Circle    radius: Int
            circle = Circle radius: 25 area: 5

        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 2)
    }

    @Test
    fun unionInsideUnionForwardDeclaration() {

        val source = """
            union Sas =
            | ^Tat
            | Sos
            union Tat =
            | Tut
            | Tam
        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 2)
    }


//    @Test
//    fun optionTypeGeneric() {
//
//        val source = """
//            union Option =
//                | Some x: T
//                | None
//
//            Option unwrap -> T = |this
//            | Some => this x
//            | None => Error throwWithMessage: "No value"
//
//            optionInt = Some x: 42
//            optionInt unwrap echo
//
//            optionStr = Some x: "sas"
//            optionStr unwrap echo
//
//        """.trimIndent()
//
//        val statements = resolve(source)
//
//        assert(statements.count() == 6)
//    }


    @Test
    fun genericInferReturnTypeFromReceiver() {

        val source = """
                x = { 1 2 3 }
                y = x map: [it + 5]
                z = y at: 1

        """.trimIndent()

        val statements = resolve(source)

        assert(statements.count() == 3)
    }

    @Test
    fun assignThis() {

        val source = """
            Int x = [
                ar = this
            ]

        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
    }

    @Test
    fun typeNoParamsButGeneric() {

        val source = """
            type Saas::T
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 1)
        assert((statements[0] as TypeDeclaration).genericFields.first() == "T")
    }

    @Test
    fun genericTypeBox() {

        val source = """
        type Box field: T

        x = Box field: 7
        y = Box field: "uh"

        x field + 5
        y field + "sas"
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 5)
        assert((statements[0] as TypeDeclaration).genericFields.first() == "T")
    }


    @Test
    fun inferReturnTypeOfSingleExpressionInMessageDeclaration() {

        val source = """
            type Person 
            Person say::String = "sas"
            Person say= "sas"
            Person + arg::Int = "sas"
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 4)
        assert((statements[1] as MessageDeclarationKeyword).returnType?.name == "String")
        assert((statements[2] as MessageDeclarationUnary).returnType?.name == "String")
        assert((statements[3] as MessageDeclarationBinary).returnType?.name == "String")
    }

    @Test
    fun recursiveUnary() {
        val source = """
            Int factorial -> Int = |this
            | 0 => 1
            |=> (this - 1) factorial * this
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 1)
    }

    @Test
    fun dotAsThis() {
        val source = """
            Int from::Int = from
            
            Int
                from::Int
                to::Int
            = [
                . from: 1
            ]
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 2)
        assert(((statements[1] as MessageDeclaration).body[0] as Expression).type?.name == "Int")
    }

    @Test
    fun switchIf() {
        val source = """
            x = "sas"
            | x
            | "sas" => 1 echo
            
            _| 5 > 5 => "sas" echo
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun ifSingle() {
        val source = """
            x = "sas"
            x == "sas" => "yay!" echo
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 2)
        assertTrue(statements[1] is ControlFlow.If)
    }

    @Test
    fun enumDeclaration() {
        val source = """
            enum Color r: Int g: Int b: Int =
            | RED   r: 255 g: 0 b: 0
            | GREEN r: 0 g: 255 b: 0
            
            c = Color.RED
            r = c r
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun unionDeclaration() {
        val source = """
            union Color = Red r: Int | Green g: Int | Purple r: Int g: Int
            x = Red r: 66
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 2)
    }

    @Test
    fun unionInsideSwitch() {
        val source = """
                union Shape area: Int =
                | Rectangle width: Int height: Int
                
                x = Rectangle width: 2 height: 3 area: 6
                | x
                | Rectangle => x width echo // Rectangle not constructor
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun msgForGenericType() {
        val source = """
            MutableList::Int bogosort = [
               
            ]

            list = {9 8 7}
            list bogosort
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun inlineQuestion() {
        val source = """
            type Person name: String age: Int
            Person sleep = 1 echo
            >? Person
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun pipe() {
        val source = """
            type Person name: String
            Person getName = name
            Person name: "Alice" |> getName
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun cascade() {
        val source = """
            type Person name: String
            Person name: "Alice"; name: "Bob"; name: "Centurion"
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[1] as MessageSendKeyword
        val w = q.messages[1].type
        val e = q.messages[2].type
        assertTrue { w?.name == "Person" }
        assertTrue { e?.name == "Person" }
    }

    @Test
    fun cascade2() {
        val source = """
            x = {1 2 3} add: 4; add: 5
            x add: 6
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)

    }

    @Test
    fun recursiveFunc() {
        val source = """
        Int fib -> Int = _
        | this < 2 => 1
        |=> (this - 2) fib + (this - 1) fib
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
    }

    @Test
    fun recursiveType() {
        val source = """
        type Sas sus: Sus
        type Sus x: Sas
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
    }

    @Test
    fun nullableType() {
        val source = """
        mut x::Int? = null
        x unpack: [
          it + 6
        ]
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 2)
        val x = (statements[0] as VarDeclaration).value
        assertTrue { x.type is Type.NullableType }
    }

    @Test
    fun nullableTypeDeclaration() {
        val source = """
            q::Int? = 6
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val value = (statements[0] as VarDeclaration).value
        assertTrue { value.type is Type.NullableType }

    }


    @Test
    fun nullableTypeReturn() {
        val source = """
        Int sas -> Int? = ^41
        x = 5 sas
        x unpackOrError inc 
        x unpack: [it inc]
        y = x unpackOrValue: 5
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 5)

        assert((statements[1] as VarDeclaration).value.type is Type.NullableType)
        assert((statements[2] as MessageSendUnary).type is Type.InternalType)
        assert((statements[3] as MessageSendKeyword).type is Type.InternalType)
        assert((statements[4] as VarDeclaration).value.type is Type.InternalType)


    }

    @Test
    fun unpackOrError() {
        val source = """
        Int sas -> Int? = ^41
        x = 5 sas
        x unpackOrError inc 
        """.trimIndent()


        val statements = resolve(source)
        assert(statements.count() == 3)

        assert((statements[1] as VarDeclaration).value.type is Type.NullableType)
        assert((statements[2] as MessageSendUnary).type is Type.InternalType)


    }

    @Test
    fun inferTypeOfEmptyArray() {
        val source = """
           union JsonObj =
           | JsonArray arr: MutableList::JsonObj

           arr = JsonArray arr: {}
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
    }

    @Test
    fun mapIterate() {
        val source = """
            map = #{ 1 "2" 3 "4" }

            map forEach: [k, v ->
                k echo
                v echo
            ]
        """.trimIndent()

        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
        val (k, v) = (q.args[0].keywordArg as CodeBlock).inputList
        assert(k.type?.name == "Int")
        assert(v.type?.name == "String")
    }


    @Test
    fun chunked() {
        val source = """
           linesChunked = {1 2 3 4 5 6} chunked: 2
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        assert((((statements[0] as VarDeclaration).value.type as Type.UserType).typeArgumentList[0] as Type.UserType).typeArgumentList[0].name == "Int")
    }

    @Test
    fun foreachOnComplexType() {
        val source = """
            linesChunked = {1 2 3 4 5 6} chunked: 2
            linesChunked forEach: [
              it at: 0
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        assert((((statements[0] as VarDeclaration).value.type as Type.UserType).typeArgumentList[0] as Type.UserType).typeArgumentList[0].name == "Int")
    }

    @Test
    fun resolveGenericKwReturn() {
        val source = """
            x = #{ 1 2 3 4 }
            y = x at: 1
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val y = statements[1] as VarDeclaration
        val nullableTypeUnpacked = (y.value.type as Type.NullableType).realType
        assert(nullableTypeUnpacked.name == "Int")
    }

    @Test
    fun nullableVarDeclaration() {
        val source = """
            x::Int? = 6
            x 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val x = statements[0] as VarDeclaration
        assert(x.value.type is Type.NullableType)
        val y = statements[1] as IdentifierExpr
        assertTrue { y.type is Type.NullableType }

    }

    @Test
    fun anyAssign() {
        val source = """
            x::Any = 1 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
    }

    @Test
    fun unpackGenericBox() {
        val source = """
            type Box t: T

            x = Box t: 5
            y = Box t: "sas"
        
            unpack_x = x t
            unpack_x + 5
        
            unpack_y = y t
            unpack_y + " sus"
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 7)
    }

    @Test
    fun returnGeneric() {
        val source = """
            Int x::T -> T = x
            y = 1 x: 5
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val yVal = (statements[1] as VarDeclaration).value as MessageSendKeyword
        assert(yVal.type?.name == "Int")
    }

    @Test
    fun returnGenericFromConstructor() {
        val source = """
            type Sas 
            constructor Sas t::T -> T = t 
            y = Sas t: 1
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val yVal = (statements[2] as VarDeclaration).value as MessageSendKeyword
        assert(yVal.type?.name == "Int")
    }

    @Test
    fun joins() {
        val source = """
            type Person name: String age: Int
            alice = Person name: "Alica" age: 30
            bob = Person name: "Bob" age: 31
        
//            { 1 2 3 } joinWith: ", " |> echo
//            { alice bob } joinTransform: [ it name ] |> echo
            { alice bob } joinWith: "\n" transform: [ it name + ": " + it age toString ] |> echo
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 4)
    }

    @Test
    fun writeNullToNullableFiled() {
        val source = """
            type Box nullable: Box?
            box = Box nullable: null
            box nullable
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)

        val boxTypeDecl = statements[0] as TypeDeclaration
        val nullable = boxTypeDecl.fields[0].typeAST as TypeAST.UserType
        assertTrue { nullable.isNullable }
        val boxType = (statements[2] as MessageSendUnary).type
        assertTrue { boxType is Type.NullableType }
    }

    @Test
    fun getGenericFromManyUnary() {
        val source = """
            group1 = #{ 1 "sas" 2 "sus" 3 "ses" }
            group1 keys toList shuffled
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val list = statements[1] as MessageSendUnary
        val listType = list.type as Type.UserType
        assertTrue { listType.typeArgumentList.first().name == "Int" }
    }

    @Test
    fun genericReceiver() {
        val source = """
            T sas -> T = this
            x = 1 sas
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val sas = statements[1] as VarDeclaration
        assertTrue { sas.value.type?.name == "Int" }
    }

    @Test
    fun constructorSimple() {
        val source = """
            type COLOR
            constructor COLOR RED = "\u001B[31m"
        """.trimIndent()
        val (statements, _) = resolveWithResolver(source)

        assert(statements.count() == 2)
    }

    @Test
    fun mapNotOverride() {
        val source = """
            group1 = #{ 1 "sas" 2 "sus" 3 "ses" }
            sas::MutableMap(Int,Int) = #{}
        """.trimIndent()
        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)

        val q = resolver.projects["common"]!!.packages["core"]!!.types["MutableMap"] as Type.UserType
        assertTrue { q.typeArgumentList[0].name == "T" }
    }

    @Test
    fun valuesOfMap() {
        val source = """
            nativeGroup = #{1 "sas" 2 "sus"}
            nativeGroup values 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val values = statements[1] as MessageSendUnary
        val setType = values.type!! as Type.UserType
        val setTypeArg = setType.typeArgumentList[0]
        assertTrue { setTypeArg.name == "String" }
    }

    @Test
    fun unaryForGenericReceiver() {
        val source = """
            { 1 2 3 } first
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val msg = statements[0] as MessageSendUnary
        val type = msg.type
        assertTrue { type?.name == "Int" }
    }


    @Test
    fun extend() {
        val source = """
            
        type Person
        extend Person [
          on unary = 1 echo
          on + binary::Int = binary echo
          on key::Int word::String = key echo
          on withLocalName: x::Int = x echo
        ]
        
        p = Person new
        
        p unary
        p + 5
        p key: 1 word: "2"
        p withLocalName: 1
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 7)
    }


    @Test
    fun resolveInsideExtend() {
        val source = """
        type Tape pos: Int

        tape = Tape pos: 5
        
        extend Tape [
            on sas = [
                list = {1 2 3}
                [pos >= list count] whileTrue: [
                    pos <- pos dec
                    pos echo
                ]
           ]
        ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)

    }

    @Test
    fun compilerReturnType() {
        val source = """
        Any sas = [
          Compiler getName: 0
        ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val sas = statements[0] as MessageDeclarationUnary
        val wew = sas.body[0] as MessageSendKeyword
        assertTrue { wew.type?.name == "String" }
    }

    @Test
    fun foldFunc() {
        // List<T> inject: G into: [G, T -> G]
        val source = """
            { 1 2 3 } inject: 0 into: [a, b -> a + b]
        
            set::MutableSet::Int = #()
            { 3 3 3 } inject: set into: [set, cur ->
                set add: cur
                set
            ]
            { 3 3 3 } inject: #(1 2 3) into: [set, cur ->
                set add: cur
                set
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 4)

    }

    @Test
    fun mustFail() {
        val source = """
            mut x::Int = 0
            y::Int? = 4
        
            x <- y
            x echo
        """.trimIndent()

        assertFailsWith<CompilerError>(message = "sas") {
            resolve(source)
        }

    }

    @Test
    fun receiverOfpipedIsMsg() {
        val source = """
            x::Int? = null
            y = x |> unpackOrError inc
        """.trimIndent()
        val (statements) = resolveWithResolver(source)

        assert(statements.count() == 2)

    }

    @Test
    fun returnNullFromNullable() {
        val source = """
            Int sas -> Int? = null
            Int sus -> Int? = 1 > 2 => null |=> 5
        """.trimIndent()
        val (statements) = resolveWithResolver(source)
        assert(statements.count() == 2)

        val decl1 = statements[0] as MessageDeclarationUnary
        val decl2 = statements[1] as MessageDeclarationUnary
        assertTrue { decl1.returnTypeAST?.isNullable == true && decl1.returnType?.toString() == "Int?" }
        assertTrue { decl2.returnTypeAST?.isNullable == true && decl2.returnType?.toString() == "Int?" }
    }

    @Test
    fun noDoubleNull() {
        val source = """
            Int sas::Int -> Int? = 45
            1 sas: 5 |> unpackOrError inc
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
    }

    @Test
    fun extensionLambda() {
        val source = """
            type Person
            Person foo::Int = []
            Int sas::Person[Int -> Unit] = [
              sas this: Person new Int: 4
            ]
            // call sas
            2 sas: [
              this // is Person
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 4)
        val q = statements[3] as MessageSendKeyword
        assertTrue {
            val w = (q.messages.first() as KeywordMsg)
            val f = (w.args.first().keywordArg as CodeBlock).statements.first() as IdentifierExpr

            f.type!!.name == "Person"
        }
    }

    @Test
    fun getGenericParamsFromLambdaArg() {
        val source = """
            Int map::[Int -> T] = [
                1 echo
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val q = statements[0] as MessageDeclarationKeyword
        assertTrue {
            q.typeArgs[0] == "T"
        }
    }

    @Test
    fun genericArgHasType() {
        val source = """
            type Node v: T next: Node?
            type LinkedList head: Node? tail: Node? size: Int
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[1] as TypeDeclaration
        assertTrue {
            ((q.fields.first().type as Type.NullableType).realType as Type.UserType).typeArgumentList.first().name == "T"
        }
    }

    @Test
    fun genericArgFromInputParamsNotNeedToBeResolved() {
        val source = """
            type Node v: T next: Node?
        
            Node str2 -> String = next unpackOrError str2
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val str2 = statements[1] as MessageDeclarationUnary
        val unpackOrErrorType = (str2.body.first() as MessageSendUnary).messages.first().type as Type.UserType
        assertTrue {
            unpackOrErrorType.typeArgumentList.isNotEmpty() && unpackOrErrorType.name == "Node"
        }
    }

    @Test
    fun genericParamForMsgSending() {
        val source = """
            type Node v: T
            type LinkedList head: Node? 
            constructor LinkedList::T empty = 
                LinkedList::T head: null
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val q = statements[2] as ConstructorDeclaration
        val w = q.body.first() as MessageSendKeyword
        val e = w.messages[0] as KeywordMsg
        val type = e.type as Type.UserType
        assertTrue(type.typeArgumentList[0] is Type.UnknownGenericType && type.typeArgumentList[0].name == "T")
    }

    @Test
    fun genericReceiverWithLambda() {
        val source = """
            T sas::[T -> Unit] = 1
            5 sas: [it + 1]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
    }

    @Test
    fun genericsWithExplicitDefinedType() {
        val source = """
            type Box::T v: Int
            box = Box::Int v: 5
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[1] as VarDeclaration
        val w = q.value.type as Type.UserType
        val e = w.typeArgumentList.first()
        assertEquals(e.name, "Int")
    }

    @Test
    fun twoDifferentTypePlus() {
        val source = """
              "sas" + 5
        """.trimIndent()
        assertThrows<CompilerError> {
            resolve(source)
        }
    }

    @Test
    fun lambdaWithReceiver() {
        val source = """
            String msg::String[Int, Int -> Unit] = [
                str = "sas"
                msg this: str Int: 1 Int: 2
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val q = statements[0] as MessageDeclarationKeyword
        val w = q.args[0].typeAST!! as TypeAST.Lambda
        assertTrue { w.extensionOfType!!.name == "String" }
    }

    @Test
    fun lambdaWithReceiverCall() {
        val source = """
            String msg::String[Int, Int -> Unit] = [
                str = "sas"
                msg this: str Int: 1 Int: 2
            ]
            
            "sas" msg: [ x, y -> 
                 this + "sas"
            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[1] as MessageSendKeyword
        val w = (q.messages.first() as KeywordMsg).args.first().keywordArg as CodeBlock
        val e = w.statements.first() as MessageSendBinary
        val r = e.receiver as IdentifierExpr
        assertTrue { r.name == "this" && r.type?.name == "String" }
    }

    @Test
    fun implicitLambdaReturnWithControlFlow() {
        val source = """
            x = [ y::Int ->
              y >= 0 => "pos" |=> "neg"
            ]
            q = x y: 4
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)

        val codeBlock = (statements[0] as VarDeclaration).value as CodeBlock
        val iff = codeBlock.statements[0] as ControlFlow.If
        assertTrue {
            iff.kind == ControlFlowKind.Expression &&
                    codeBlock.type?.name == "[Int -> String]"
        }
    }

    @Test
    fun implicitLambdaOnlyLastControlFlowIsExpression() {
        val source = """
            x = [ y::Int ->
              y >= 0 => "pos" echo
              
              y >= 0 => "pos" |=> "neg"
            ]
            q = x y: 4
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)

        val codeBlock = (statements[0] as VarDeclaration).value as CodeBlock
        val iff = codeBlock.statements[1] as ControlFlow.If
        assertTrue {
            iff.kind == ControlFlowKind.Expression &&
                    codeBlock.type?.name == "[Int -> String]"
        }
    }

    @Test
    fun sendMethodToMethod2() {

        val source = """
            String msg::String[Int, Int -> Unit] = [
                str = "sas"
                msg this: str Int: 1 Int: 2
            ]
            
            String x::Int y::Int = 1 echo
            
            "sas" msg: &String x:y: 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val q = statements[0] as MessageDeclarationKeyword
        val w = q.args[0].typeAST!! as TypeAST.Lambda
        assertTrue { w.extensionOfType!!.name == "String" }

        val x = (statements[2] as MessageSendKeyword).messages.first() as KeywordMsg
        val t = x.args.first().keywordArg.type!! as Type.Lambda
        assertTrue { t.args.count() == 3 }
        assertTrue { t.args.first().type.name == "String" }
    }


    @Test
    fun matchingOnBool() {
        val source = """
            q = | true
            | true => 234
            | false => 45
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val q = statements[0] as VarDeclaration
        assertTrue { q.value.type?.name == "Int" }
    }

    @Test
    fun generalizeReturnTypesOfSwitch() {
        val source = """
            union Sas = Sus | Sos

            Int kek::Boolean = | kek
            | true => Sus new
            | false => Sos new
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[1] as MessageDeclaration
        assertTrue { q.returnType?.name == "Sas" }
    }

    @Test
    fun generalizeNullability() {
        val source = """
            Int sas -> Int? = null
        
            x = 3
            y = [
            | x
            | 1 => 1
            | 2 => 2 sas
            | 3 => 3
            |=> 4
            ] do
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val y = statements[2] as VarDeclaration
        assertTrue(y.value.type is Type.NullableType)
    }

    @Test
    fun lastControlFlowIsStatementIfNoElseBranch() {
        val source = """
            Int sas -> Int? = null
        
            x = 3
            y = [
                x > 3 => "sas"
            ] 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val y = (statements[2] as VarDeclaration).value as CodeBlock
        val codeBlockType = y.type as Type.Lambda
        val returnType = codeBlockType.returnType
        assertTrue { returnType.name == "Unit" }
    }

    @Test
    fun typeAlias() {
        val source = """
           type Sas = [Int -> Int]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val q = statements[0] as TypeAliasDeclaration
        val w = q.realType as Type.Lambda
        assertTrue { w.args[0].name == "Int" }
    }

    @Test
    fun typeAliasSimple() {
        val source = """
           type MyInt = Int
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
        val q = statements[0] as TypeAliasDeclaration
        assertTrue {q.receiver != null && q.receiver!!.name == "Int"}
    }

    @Test
    fun typeAliasUse() {
        val source = """
           type Sas = [Int -> Int]
           sas::Sas = [x::Int -> x inc]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 2)
        val q = statements[0] as TypeAliasDeclaration
        val w = q.realType as Type.Lambda
        assertTrue { w.args[0].name == "Int" }
    }

    @Test
    fun messageForLambda() {
        val source = """
           type Sas = [Int -> Int]
           Sas at::Int = at echo
           [x::Int -> x inc] at: 5
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun mutable() {
        val source = """
           type Person name: String age: Int
           p::mut Person = Person name: "a" age: 12
           p = p name: "new name" // p is Unit
        """.trimIndent()
        // u cant use setter as expression
        assertThrows<CompilerError> { resolve(source) }
    }

    @Test
    fun mutable2() {
        val source = """
            type Person name: String age: Int
            p::Person = Person name: "a" age: 1
            q = p name: "new name"
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
    }

    @Test
    fun emptyCodeBlock() {
        val source = """
            { 1 2 3 } forEach: [ x ->

            ]
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 1)
    }

    @Test
    fun errordomainDeclaration() {
        val source = """
            errordomain MyError =
            | Error1 x: Int
            | Error2 x: Int
            
            
            Int sas -> Int!Error1 = [
                x = Error1 x: 5
                x throw
                ^ 1
            ]
            
            Int sas2 -> Int!Error1 = [
                x = 1 sas
                ^ x
            ]
        """.trimIndent()
        val (statements, r) = resolveWithResolver(source)
        assert(statements.count() == 3)
        assertTrue {
            r.typeDB.userTypes.contains("MyError") &&
                    r.typeDB.userTypes.contains("Error2") &&
                    r.typeDB.userTypes.contains("Error1")
        }
    }

    @Test
    fun builder() {
        /// example
        fun buildString2(buildString2: StringBuilder.((String) -> Any) -> Unit): String {
            val b = StringBuilder()
            val defaultAction = { default: String ->
                b.append(default)
            }
            buildString2(b, defaultAction)
            return b.toString()

        }
        buildString2 { defaultAction ->
            defaultAction("sas")
        }
        ///

        val source = """
            builder StringBuilder buildString -> String = [
                b = StringBuilder new
                defaultAction = [ default::String ->
                    b append: default
                ]
                
                
                build this: b defaultAction: defaultAction
                
                ^ b toString
            ]

            buildString [
                1 echo
                "sas"
                "sus"
                345
            ]
        """.trimIndent()
        val (statements, _) = resolveWithResolver(source)
        assert(statements.count() == 2)
    }

    @Test
    fun builderWithArgs() {

        val source = """
            type Card
            Card sas = 1
            builder Card width::Int height::Int -> String = [
                card = Card new
                defaultAction = [ default::String ->
                    1 echo
                ]
                
                
                build this: card defaultAction: defaultAction
                
                ^ card toString
            ]

            Card (width: 24 height: 30) [
                1 echo
                this sas
            ]
            
        """.trimIndent()
        val (statements, _) = resolveWithResolver(source)
        assert(statements.count() == 4)
    }

    @Test
    fun builderWithArgsWithReceiver() {

//        class Card()
//
//        fun String.Card(width: Int, action: Card.() -> Unit) {
//            Card().action()
//        }

        val source = """
            type Card
            Card sas = 1
            String builder Card width::Int height::Int -> String = [
                card = Card new
                defaultAction = [ default::String ->
                    1 echo
                ]
                
                
                build this: card defaultAction: defaultAction
                
                ^ card toString
            ]

//            "rar" Card (width: 24 height: 30) [
//                1 echo
//                this sas
//            ]
            
        """.trimIndent()
        val (statements, _) = resolveWithResolver(source)
        assert(statements.count() == 3)
    }


    @Test
    fun assignOnlyToRealThings() {

        val source = """
           type LearnModel
           answered: Boolean
           
           LearnModel answer = answer <- true // should be answered
        """.trimIndent()
        assertThrows<CompilerError> {
            resolveWithResolver(source)
        }
    }

    @Test
    fun inlineRepl() {

        val source = """
           x = 1
           > x
        """.trimIndent()
        val (x, _) = resolveWithResolver(source)
        assert(x.count() == 2)
        val q = x.last() as IdentifierExpr
        assert(q.isInlineRepl)

    }

    @Test
    fun isTypeIdentifier() {

        val source = """
           type Sas x: Int
           l = 25
           l
           Sas
        """.trimIndent()
        val (x, _) = resolveWithResolver(source)
        assert(x.count() == 4)
        val q = x.last() as IdentifierExpr
        assert(q.isType)

    }


}



